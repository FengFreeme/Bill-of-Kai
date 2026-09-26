package com.kai.bill.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kai.bill.core.common.ext.isAccessibilityServiceEnabled
import com.kai.bill.core.prefs.KaiPrefs
import com.kai.bill.data.R
import com.kai.bill.data.capture.accessibility.AccessibilityServiceHolder
import com.kai.bill.data.capture.notification.KaiNotificationListener
import com.kai.bill.data.capture.notification.NotificationListenerHolder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 采集链路的「重新武装」与「存活体检」。
 *
 * 通知监听与无障碍都是系统绑定的服务，理论上被杀后系统会重绑；但国内 ROM（尤其
 * vivo / OriginOS）灭屏长待后会清进程，`START_STICKY` 指望不上，代码层能做三件事：
 * 1. **重新武装**：拉起前台保活服务 + 请求系统重绑通知监听；
 * 2. **定时体检**：`WorkManager` 每 15 分钟一次（系统允许的最小周期）。进程被杀后这个任务
 *    仍在系统侧，执行时把进程唤醒，从而完成第 1 步；
 * 3. **让用户知道**：连续两次不通就发一条静默通知 —— 过夜失效最糟的是用户不知道。
 *
 * NOTE: 用户在最近任务里「强制停止」应用后，系统不会再跑任何任务（JobScheduler 与广播
 * 全部失效），那种状态只能靠用户侧设置（自启动 / 后台高耗电 / 锁定后台）规避。
 */
@Singleton
class CaptureKeepAlive @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kaiPrefs: KaiPrefs,
    private val notificationListenerHolder: NotificationListenerHolder,
    private val accessibilityServiceHolder: AccessibilityServiceHolder
) {

    /**
     * 排入周期性体检（幂等）。
     *
     * 用 `KEEP` 而非 `UPDATE`：用户开关采集时不该重置计时器，否则频繁开关会把体检一直往后推。
     */
    fun schedulePeriodicCheck() {
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<KeepAliveWorker>(CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
                    // 不设约束：采集不依赖网络与电量，体检必须能在夜间照常跑
                    .setConstraints(Constraints.NONE)
                    .build()
            )
        }
    }

    /** 取消周期性体检（用户关掉采集开关时） */
    fun cancelPeriodicCheck() {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
    }

    /** 轻量重新武装：拉起前台服务 + 重绑通知监听，**不打扰用户**（应用启动、开机完成时用） */
    suspend fun rearm() {
        startForegroundService()
        rebindNotificationListener()
    }

    /**
     * 完整体检：重新武装 + 判定链路是否真的活着，必要时提醒用户。
     *
     * @return true 表示链路正常（通知监听连着；无障碍若已在系统里授权也连着）
     */
    suspend fun healthCheck(): Boolean {
        rearm()

        // 刚被唤醒的进程里，系统重绑服务要几秒；不等一下会把正常重启判成故障，每次唤醒都误报
        delay(REARM_GRACE_MILLIS)

        val state = kaiPrefs.captureState.first()
        if (!state.captureEnabled) {
            // 用户主动关掉了：清掉计数与告警，别把「关闭」当成故障
            kaiPrefs.resetCaptureDownStreak()
            dismissAlert()
            return true
        }

        // 判据取**进程内**的真实连通态：落盘标记在进程被杀时没机会改写，
        // 会停在 true，拿它体检只会得到「一切正常」的假象
        val listenerOk = notificationListenerHolder.isConnected
        // 无障碍是可选项：没在系统里勾选就不算故障，否则只想要通知记账的用户会被莫名提醒
        val accessibilityOk = !context.isAccessibilityServiceEnabled() ||
            accessibilityServiceHolder.serviceContext != null

        if (listenerOk && accessibilityOk) {
            kaiPrefs.resetCaptureDownStreak()
            dismissAlert()
            return true
        }

        // 连续两次才提醒：服务重连需要时间，一次判死会误报
        val streak = kaiPrefs.bumpCaptureDownStreak()
        when (streak) {
            // 第一次不通：补排 5 分钟后的复查。没有它只能等下一个 15 分钟周期，
            // 而这条链路的价值恰恰是「尽快发现」
            ALERT_AT_STREAK - 1 -> scheduleFollowUpCheck()
            ALERT_AT_STREAK -> postAlert(listenerOk = listenerOk, accessibilityOk = accessibilityOk)
        }
        return false
    }

    /**
     * 排一次 5 分钟后的单次复查。
     *
     * 用单次任务而不是缩短周期：周期任务的首次执行受 15 分钟下限约束，压不下去。
     */
    private fun scheduleFollowUpCheck() {
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                FOLLOW_UP_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<KeepAliveWorker>()
                    .setInitialDelay(FOLLOW_UP_DELAY_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(Constraints.NONE)
                    .build()
            )
        }
    }

    private fun startForegroundService() {
        runCatching {
            context.startForegroundService(Intent(context, CaptureForegroundService::class.java))
        }
    }

    /** 请求系统重绑通知监听；未授予通知使用权时是空操作，因此不必先判权限 */
    private fun rebindNotificationListener() {
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, KaiNotificationListener::class.java)
            )
        }
    }

    /**
     * 发一条「采集已停止」的提醒。
     *
     * NOTE: 跳转用 AOSP 标准页而不是厂商私有页 —— 私有页在 `PendingIntent` 里万一
     * resolve 不到就是「点了没反应」，而这条通知的价值就在于点一下能修好它。
     */
    private fun postAlert(listenerOk: Boolean, accessibilityOk: Boolean) {
        ensureChannel()

        val titleRes = when {
            !listenerOk && !accessibilityOk -> R.string.capture_health_title_both
            !listenerOk -> R.string.capture_health_title_listener
            else -> R.string.capture_health_title_accessibility
        }
        val targetAction = if (!listenerOk) {
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        } else {
            Settings.ACTION_ACCESSIBILITY_SETTINGS
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(targetAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(R.string.capture_health_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(ALERT_ID, notification) }
    }

    /** 链路恢复时撤掉提醒，免得用户点进一个「其实已经好了」的告警 */
    private fun dismissAlert() {
        runCatching { NotificationManagerCompat.from(context).cancel(ALERT_ID) }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.capture_health_channel_name),
                    // LOW：过夜失效常在凌晨，提醒要留在通知栏，但不该把人吵醒
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.capture_health_channel_desc)
                }
            )
        }
    }

    private companion object {
        const val WORK_NAME = "capture_keep_alive"

        /** 首次发现异常后补排的复查任务名与延迟 */
        const val FOLLOW_UP_WORK_NAME = "capture_keep_alive_follow_up"
        const val FOLLOW_UP_DELAY_MINUTES = 5L

        /** 体检周期：15 分钟是 `WorkManager` 允许的最小周期 */
        const val CHECK_INTERVAL_MINUTES = 15L

        /** 重新武装之后等多久再看状态：留给系统重绑服务的时间 */
        const val REARM_GRACE_MILLIS = 6_000L

        /** 第几次连续失败才提醒（配合上面的复查，约 5~20 分钟内报出来） */
        const val ALERT_AT_STREAK = 2

        const val CHANNEL_ID = "capture_health"
        const val ALERT_ID = 1002
    }
}

/**
 * `Worker` 与 `BroadcastReceiver` 不是 Hilt 组件、拿不到构造注入，通过入口点取依赖 ——
 * 避免为两个后台入口去接 `hilt-work` + `HiltWorkerFactory`。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CaptureKeepAliveEntryPoint {

    fun captureKeepAlive(): CaptureKeepAlive

    fun kaiPrefs(): KaiPrefs
}
