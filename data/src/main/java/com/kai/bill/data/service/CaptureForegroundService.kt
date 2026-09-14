package com.kai.bill.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kai.bill.core.prefs.KaiPrefs
import com.kai.bill.data.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 前台保活服务：辅助通知监听在后台存活，不保证监听永久在线。
 *
 * - `targetSdk=35` 要求声明 `foregroundServiceType`（本服务在 Manifest 注册为 `dataSync`），
 *   否则启动即抛 SecurityException / ForegroundService 类型缺失异常。
 * - `onCreate` 必须在 5 秒内调用 [startForeground]，否则系统强制停止服务。
 * - 生命周期内写 `captureServiceAlive` 供首页「漏采」状态判断。
 */
@AndroidEntryPoint
class CaptureForegroundService : Service() {

    @Inject
    lateinit var kaiPrefs: KaiPrefs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification())
        scope.launch { kaiPrefs.setCaptureServiceAlive(true) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        scope.launch { kaiPrefs.setCaptureServiceAlive(false) }
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.capture_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.capture_channel_desc)
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.capture_notif_title))
        .setContentText(getString(R.string.capture_notif_text))
        .setSmallIcon(R.drawable.ic_notification)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    companion object {
        private const val CHANNEL_ID = "capture_foreground"
        private const val NOTIF_ID = 1001
    }
}
