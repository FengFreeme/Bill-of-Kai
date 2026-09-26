package com.kai.bill.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机后重新武装采集链路：用户上次的开关仍是开启，但没有任何进程在跑。
 *
 * NOTE: 不接 `LOCKED_BOOT_COMPLETED` —— 应用数据在凭据加密区，解锁前读不到开关，
 * 读了只会把「已开启」误判成「未开启」。开机广播本身就发生在解锁之后。
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // onReceive 不能做挂起操作，用 goAsync 借 10 秒宽限期读一次开关
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    CaptureKeepAliveEntryPoint::class.java
                )
                if (entryPoint.kaiPrefs().captureState.first().captureEnabled) {
                    // 用完整体检而非只 rearm：接不上要能被发现（连续两次才提醒，开机这次不会刷告警）
                    entryPoint.captureKeepAlive().healthCheck()
                }
            } catch (_: Throwable) {
                // 开机广播里不让异常外泄，否则换来一次无意义的崩溃上报
            } finally {
                pendingResult.finish()
            }
        }
    }
}
