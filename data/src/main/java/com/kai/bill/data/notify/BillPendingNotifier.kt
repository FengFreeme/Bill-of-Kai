package com.kai.bill.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kai.bill.data.R
import com.kai.bill.domain.model.PendingReason
import com.kai.bill.domain.repository.PendingBillRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/** 通知点击后要跳到哪 —— 与 app 层的路由名解耦：data 不认识 `Route`，由 app 映射。 */
object PendingNotificationContract {

    /** Intent extra：目标页面标识 */
    const val EXTRA_DESTINATION = "com.kai.bill.extra.PENDING_DESTINATION"

    /** 目标：待确认列表 */
    const val DESTINATION_NEEDS_REVIEW = "needs_review"
}

/**
 * 待确认账单的通知出口。
 *
 * 抽成接口的理由与 [BillSavedNotifier] 相同：采集链路要能在纯 JVM 单测里断言
 * 「这笔有没有触发提醒」，而不是把 NotificationManager 搬进测试。
 */
interface BillPendingNotifier {

    /**
     * 弹一条**值得打扰用户**的待确认提醒。
     *
     * 调用契约：只在「判不出方向」时由采集链路调用 —— 这种通知没给出任何可用线索，
     * 不提醒就等于静默丢弃。而「自己的钱搬家 / 红包 / 泛化入账」都有明确建议值，
     * 只进列表与角标（汇总通知由条数观察驱动），不走到这里。
     *
     * 分档判定刻意放在**调用方**（`IngestPipeline`）而不是实现里：这样它能在纯 JVM 单测里
     * 被断言（「弱档不该打扰」），而实现层只剩渲染，不需要为测试搬来 NotificationManager。
     *
     * @param amountCents 金额（分）
     * @param reason 为什么进待确认，决定文案
     */
    fun notifyPending(amountCents: Long, reason: PendingReason, matchedKeyword: String?)
}

/**
 * 系统通知实现：**两条通知、两条通道**。
 *
 * | 通道 | 何时发 | 形态 |
 * |---|---|---|
 * | `bill_pending_alert_v1`（HIGH） | 只在该条 `reason == DIRECTION_UNKNOWN` 时 | 悬浮横幅，带具体金额与原因 |
 * | `bill_pending_summary_v1`（LOW） | 每次待确认条数变化 | 只更新通知栏与角标，**不悬浮** |
 *
 * 为什么要分两条而不是一条换通道：Android 的「是否悬浮」由**通道重要性**决定，
 * 同一通道内没法「这条弹、那条不弹」。而用户对这两类提醒的容忍度本就不同 ——
 * 每次零钱提现都弹一次横幅，用户会直接把整个通道关掉，连带把真正需要看的提醒一起关掉。
 *
 * 汇总通知与角标由**观察待确认条数**驱动（而不是每次手动 +1/-1）：
 * 用户在待确认页处理掉任意几条后，角标与文案会自动跟上；清空时两条通知自动消失，
 * 不需要页面反向依赖通知层。
 */
@Singleton
class DefaultBillPendingNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    repository: PendingBillRepository
) : BillPendingNotifier {

    private val manager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        repository.observeCount()
            .onEach { count ->
                if (count <= 0) {
                    manager.cancel(NOTIF_ID_SUMMARY)
                } else {
                    notifySummary(count)
                }
            }
            .launchIn(scope)
    }

    override fun notifyPending(amountCents: Long, reason: PendingReason, matchedKeyword: String?) {
        val amount = "¥%.2f".format(amountCents / 100.0)
        val keywordText = matchedKeyword?.let { "（因为『$it』）" }.orEmpty()
        val notif = builder(CHANNEL_ID_ALERT, "待确认提醒")
            .setContentTitle("有账单待确认")
            .setContentText("$amount · 看不出是支出还是收入$keywordText")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // 同一条被更新时不要重复响铃；用户已经看过就别再吵
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIF_ID_SUMMARY + 1, notif)
    }

    private fun notifySummary(count: Int) {
        val notif = builder(CHANNEL_ID_SUMMARY, "待确认汇总")
            .setContentTitle("待确认账单")
            .setContentText("有 $count 笔待确认")
            .setNumber(count)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIF_ID_SUMMARY, notif)
    }

    /**
     * 统一构造：小图标 + 点击跳待确认列表 + 通道。
     *
     * 跳转不写死 Activity 类名（`data` 模块看不到 app 的 `MainActivity`），
     * 而是拿系统的启动 Intent 加一个「目标页面」标记，由 app 侧翻译成自己的路由。
     */
    private fun builder(channelId: String, channelName: String): NotificationCompat.Builder {
        ensureChannel(channelId, channelName)
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent())
    }

    private fun contentIntent(): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        launch.apply {
            // 复用已在栈顶的 Activity（不重建），并用 onNewIntent 把目标页面递进去
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(
                PendingNotificationContract.EXTRA_DESTINATION,
                PendingNotificationContract.DESTINATION_NEEDS_REVIEW
            )
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            launch,
            // FLAG_IMMUTABLE 是 Android 12+ 的硬要求；UPDATE_CURRENT 保证 extra 是最新的
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(id: String, name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(id) != null) return

        // 注意：通道一旦创建，重要性与名称都无法被应用「升级」——
        // 因此这里是两条独立通道、各自 ID 带版本后缀（`_v1`），改动时换新 ID 而不是改旧通道。
        val importance = if (id == CHANNEL_ID_ALERT) {
            NotificationManager.IMPORTANCE_HIGH
        } else {
            NotificationManager.IMPORTANCE_LOW
        }
        manager.createNotificationChannel(
            NotificationChannel(id, name, importance).apply {
                description = if (id == CHANNEL_ID_ALERT) {
                    "判不出类型的账单提醒（悬浮横幅）"
                } else {
                    "待确认账单条数与角标，不打扰"
                }
                setShowBadge(true)
            }
        )
    }

    private companion object {
        const val CHANNEL_ID_ALERT = "bill_pending_alert_v1"
        const val CHANNEL_ID_SUMMARY = "bill_pending_summary_v1"

        /** 汇总通知的 ID（角标挂在它上面） */
        const val NOTIF_ID_SUMMARY = 3002

        const val REQUEST_CODE = 3003
    }
}
