package com.kai.bill

import android.app.Application
import com.kai.bill.data.capture.notification.NotificationCapture
import com.kai.bill.data.seed.DatabaseSeeder
import com.kai.bill.data.service.CaptureController
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用入口。
 *
 * 职责：挂上 Hilt，触发整个 DI 图的生成；并在冷启动时触发一次预置数据播种。
 * 播种逻辑本身在 [DatabaseSeeder]（data 模块），这里只负责「在合适的时机调一次」。
 *
 * 注入 [NotificationCapture] 是为了在应用冷启动时即创建该单例（其 `init` 开始消费通知桥接流），
 * 保证系统绑定通知监听服务、投递事件时不会因无人订阅而被环形缓冲丢弃。
 * 注入 [CaptureController] 是为了触发其对 `captureEnabled` 的订阅，
 * 进而按用户开关启停前台保活服务。
 */
@HiltAndroidApp
class KaiApplication : Application() {

    @Inject
    lateinit var seeder: DatabaseSeeder

    @Inject
    lateinit var notificationCapture: NotificationCapture

    @Inject
    lateinit var captureController: CaptureController

    private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 幂等播种：预置分类/账户用固定 id + IGNORE，重复调用安全
        seedScope.launch {
            runCatching { seeder.seed() }
                .onFailure { it.printStackTrace() }
        }
    }
}
