package com.kai.bill.data.capture.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kai.bill.core.prefs.KaiPrefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 通知监听服务。
 *
 * 职责（仅采集，不含解析）：在 [onNotificationPosted] 中按包名白名单 + [NotificationContentGate]
 * 过滤，跨品牌抽取通知文本，清洗后封装成 [RawCaptureEvent] 投喂给 [NotificationCaptureBridge]；
 * 解析与落库由 [com.kai.bill.data.ingest.BillIngestor] 统一处理。
 *
 * 连接态写入 `notificationListenerEnabled`（[KaiPrefs]），供 UI 与首页「漏采提示」判断监听是否被 OEM 回收。
 *
 * 组件在 `data/src/main/AndroidManifest.xml` 注册（[android.permission.BIND_NOTIFICATION_LISTENER_SERVICE]）。
 */
@AndroidEntryPoint
class KaiNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var bridge: NotificationCaptureBridge

    @Inject
    lateinit var kaiPrefs: KaiPrefs

    @Inject
    lateinit var holder: NotificationListenerHolder

    /** 系统回调在 Binder 线程，写入 prefs 切到 IO 协程；[NotificationListenerService] 非 LifecycleService，自带作用域 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() = markConnected(true)

    override fun onListenerDisconnected() = markConnected(false)

    /** 进程内态给采集体检用（进程被杀会重置），落盘标记给 UI 展示用（跨进程重启仍留痕迹） */
    private fun markConnected(connected: Boolean) {
        if (connected) holder.markConnected() else holder.markDisconnected()
        scope.launch { kaiPrefs.setNotificationListenerEnabled(connected) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return

        // 只处理白名单来源；跳过常驻（ongoing）与分组概要通知，避免噪音与重复
        if (!NotificationWhitelist.isWatched(pkg)) return
        if (sbn.isOngoing) return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val rawText = NotificationTextExtractor.extract(sbn) ?: return
        if (rawText.isBlank()) return

        // 内容闸门：微信只放行「微信支付」；支付宝 / 银行 / 短信必须证明是账单（见 NotificationContentGate）
        if (!NotificationContentGate.passes(pkg, rawText)) return

        bridge.emit(
            RawCaptureEvent(
                packageName = pkg,
                rawText = rawText,
                postedAtMillis = sbn.postTime,
                sbnKey = sbn.key
            )
        )
    }

    override fun onDestroy() {
        // 服务销毁即断开：进程马上要没，进程内状态也要跟着归零，
        // 否则「新进程 + 还没连上」的窗口里体检会误读成正常
        holder.markDisconnected()
        scope.cancel()
        super.onDestroy()
    }
}
