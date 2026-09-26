package com.kai.bill

import android.app.Application
import com.kai.bill.data.capture.notification.NotificationCapture
import com.kai.bill.data.pending.PendingBillCleaner
import com.kai.bill.data.seed.DatabaseSeeder
import com.kai.bill.data.service.CaptureController
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用入口：挂 Hilt、冷启动播种预置数据。
 *
 * 注入 [NotificationCapture] / [CaptureController] 是为了在冷启动即创建这两个单例、触发其 `init`
 * 订阅（通知桥接流 / `captureEnabled`）—— 否则系统投递事件会因无人订阅被丢弃，保活服务也不会启停。
 */
@HiltAndroidApp
class KaiApplication : Application() {

    @Inject
    lateinit var seeder: DatabaseSeeder

    @Inject
    lateinit var pendingBillCleaner: PendingBillCleaner

    @Inject
    lateinit var notificationCapture: NotificationCapture

    @Inject
    lateinit var captureController: CaptureController

    private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // NOTE: 幂等播种 —— 预置分类/账户用固定 id + IGNORE，重复调用安全
        seedScope.launch {
            runCatching { seeder.seed() }
                .onFailure { it.printStackTrace() }
        }
        // NOTE: 冷启动顺带清一次过期待确认记录（保留 30 天）；失败不影响启动
        seedScope.launch {
            runCatching { pendingBillCleaner.purgeExpired() }
                .onFailure { it.printStackTrace() }
        }
    }
}
