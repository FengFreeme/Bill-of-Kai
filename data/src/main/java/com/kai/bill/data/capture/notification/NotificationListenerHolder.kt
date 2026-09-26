package com.kai.bill.data.capture.notification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知监听器的**进程内**连通态，同 `AccessibilityServiceHolder` 的做法：服务自己登记。
 *
 * NOTE: 不能用落盘标记 `KaiPrefs.notificationListenerEnabled` 做体检判据 ——
 * 进程被杀时没机会改写它，它会一直停在 true，把「已经断了」误判成正常。
 * 进程内单例随进程消失，新进程起来天然是「未连接」，正是体检需要的语义。
 */
@Singleton
class NotificationListenerHolder @Inject constructor() {

    @Volatile
    private var connected: Boolean = false

    val isConnected: Boolean
        get() = connected

    fun markConnected() {
        connected = true
    }

    fun markDisconnected() {
        connected = false
    }
}
