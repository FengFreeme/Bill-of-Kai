package com.kai.bill.data.di

import com.kai.bill.data.capture.notification.DefaultNotificationCaptureBridge
import com.kai.bill.data.capture.notification.NotificationCaptureBridge
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * data 模块 Hilt 绑定。
 *
 * 把接口声明与实现分离（服务层用接口、单测可 mock、实现可替换），
 * 这里统一用 [Binds] 注册默认实现。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindNotificationCaptureBridge(
        impl: DefaultNotificationCaptureBridge
    ): NotificationCaptureBridge
}
