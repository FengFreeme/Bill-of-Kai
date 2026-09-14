package com.kai.bill.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kai.bill.domain.time.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 应用级依赖绑定 —— 全工程**唯一**允许出现 `@Provides` 的地方（基础设施类）。
 *
 * 模块边界：本 Module 只负责「基础设施」（存储、时钟、调度器），
 * 业务实现类的绑定（Repository 等）在 [RepositoryModule] 与 [DataSourceModule]，
 * 避免所有绑定挤在一个文件里。
 *
 * Clock：接口在 domain，系统实现 [SystemClock] 在此绑定，保证 domain 零依赖。
 *
 * 为什么 DataStore 用 `PreferenceDataStoreFactory` 而非 `preferencesDataStore` 委托：
 * 委托属性绑定在 `Context` 的扩展上，Hilt 无法感知；工厂方式能在 `@Provides` 里
 * 显式拿到 `Context` 并控制 `CoroutineScope`，契合 DI 的单一收口原则。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** 标记注入到 IO 调度器的 [CoroutineDispatcher] */
    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class IoDispatcher

    /** 偏好存储文件名，与 [com.kai.bill.core.db.KaiDatabase.DATABASE_NAME] 区分开，避免混淆 */
    private const val PREFS_FILE_NAME = "kai_prefs.preferences_pb"

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = null,
            migrations = emptyList(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        ) {
            context.dataDir.resolve(PREFS_FILE_NAME)
        }

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
