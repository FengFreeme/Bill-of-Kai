package com.kai.bill.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kai.bill.core.common.overlay.ReviewCardOverlay
import com.kai.bill.core.common.overlay.ReviewKind
import com.kai.bill.data.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 确认卡片的 Intent 契约 —— 与 app 层的 Activity 类名解耦。
 *
 * 与 [PendingNotificationContract] 同一套理由：`data` 模块看不到 app 的 Activity，
 * 因此只声明一个 action 字符串，由 app 侧在清单里登记接收者。
 *
 * 注意：它现在只服务于**通知被点击**这条路径。自动弹出走的是无障碍悬浮层
 * （见 [ReviewCardOverlay]），不需要启动任何 Activity。
 */
object ReviewCardContract {

    /** 拉起确认卡片的 action；app 侧 `ReviewCardActivity` 声明同名 intent-filter */
    const val ACTION_REVIEW_CARD = "com.kai.bill.action.REVIEW_CARD"

    /** Intent extra：要展示的账单主键 */
    const val EXTRA_BILL_ID = "com.kai.bill.extra.REVIEW_CARD_BILL_ID"

    /**
     * Intent extra：这张卡片是「新建一笔」还是「补分类」（存 [ReviewKind] 的 name）。
     *
     * 通知被点击后由 app 侧的 `ReviewCardActivity` 读取，用来决定标题与说明文案 ——
     * 两条路径（悬浮层 / 点通知）必须说同样的话，所以种类要跟着 Intent 一起传。
     */
    const val EXTRA_REVIEW_KIND = "com.kai.bill.extra.REVIEW_CARD_KIND"
}

/**
 * 确认卡片的出口。
 *
 * 抽成接口的理由与 [BillSavedNotifier] 相同：采集链路要能在纯 JVM 单测里断言
 * 「这次该不该弹卡片」，而不是把 `WindowManager` / `NotificationManager` 搬进测试。
 *
 * 调用契约：**只在「确实记上了」时调用**（结果编码为 `CREATED` / `ENRICHED`）。
 * 分档判定刻意放在调用方（`CategorySignalRecorder`），实现层只负责把卡片送达。
 */
interface BillReviewNotifier {

    /**
     * 把「刚记下的这一笔」送到用户眼前，让他当场改分类或撤销。
     *
     * 实现**不得抛异常**：卡片是「更好」，不是「必须」——
     * 送不到绝不能让采集链路中断。
     *
     * @param billId 账单主键，必须 > 0
     * @param kind 这张卡片是「新建一笔」还是「补分类」—— 文案必须区分，见 [ReviewKind]
     */
    suspend fun notifyForReview(billId: Long, kind: ReviewKind)
}

/**
 * 两条投递路径，先强后弱。
 *
 * | 路径 | 前提 | 依据 |
 * |---|---|---|
 * | **无障碍悬浮层** | 本应用的无障碍服务在运行 | `TYPE_ACCESSIBILITY_OVERLAY` 窗口类型**不需要任何权限** |
 * | 高优先级横幅通知 | 上一条失败（服务被停/进程异常） | 点通知属于前台操作，系统必然放行启动卡片 |
 *
 * ⚠️ **不要退回「后台 startActivity」**：Android 10 起后台启动 Activity 的 8 条官方豁免里
 * 没有「无障碍服务」，`SYSTEM_ALERT_WINDOW` 也不该为此申请（商业产品同样没申请）。
 * 那条路会被系统**静默拦截**，不抛异常、不留日志，本项目为此白改了一轮。
 */
@Singleton
class DefaultBillReviewNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val overlay: ReviewCardOverlay
) : BillReviewNotifier {

    private val manager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /** 已送过的账单 id（有界）；同一笔只打扰一次 */
    private val shown = LinkedHashSet<Long>()

    override suspend fun notifyForReview(billId: Long, kind: ReviewKind) {
        if (billId <= 0L) return
        if (!markShown(billId)) return

        // 路径一：无障碍悬浮层。只要无障碍服务在跑（信号正是它产出的），这条路一定可用
        val displayed = runCatching { overlay.show(billId, kind) }.getOrDefault(false)
        if (displayed) return

        // 路径二：退化为横幅通知。宁可退一档（用户下拉点一下），
        // 也不能让「多了一笔账」这件事悄无声息
        runCatching { postHeadsUp(billId, kind) }
    }

    /** @return true 表示这是第一次送；false 表示已送过，跳过 */
    private fun markShown(billId: Long): Boolean = synchronized(shown) {
        if (!shown.add(billId)) return false
        while (shown.size > MAX_REMEMBERED) shown.remove(shown.first())
        true
    }

    private fun cardIntent(billId: Long, kind: ReviewKind): Intent =
        Intent(ReviewCardContract.ACTION_REVIEW_CARD)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(ReviewCardContract.EXTRA_BILL_ID, billId)
            .putExtra(ReviewCardContract.EXTRA_REVIEW_KIND, kind.name)

    private fun postHeadsUp(billId: Long, kind: ReviewKind) {
        ensureChannel()
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(kind.headsUpTitle())
            .setContentText(kind.headsUpText())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // 会变的 extra 必须配不同的 requestCode，否则第二条通知会点到第一笔账上
            .setContentIntent(cardPendingIntent(billId, kind))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(notifId(billId), notif)
    }

    private fun cardPendingIntent(billId: Long, kind: ReviewKind): PendingIntent = PendingIntent.getActivity(
        context,
        notifId(billId),
        cardIntent(billId, kind),
        // FLAG_IMMUTABLE 是 Android 12+ 的硬要求
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** 通知与 PendingIntent 共用 id：让「通知」与「点击它要打开的卡片」一一对应 */
    private fun notifId(billId: Long): Int = NOTIF_ID_BASE + (billId % NOTIF_ID_SPAN).toInt()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        // 通道一旦创建，重要性无法被应用「升级」——因此 ID 带版本后缀，改动时换新 ID
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "记账确认", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "自动记下一笔后提醒你确认分类"
                setShowBadge(true)
            }
        )
    }

    private companion object {
        const val CHANNEL_ID = "bill_review_v2"

        const val NOTIF_ID_BASE = 4000

        /** id 复用区间宽度：同一时刻最多允许 1000 条待确认通知，超出则最早的被覆盖 */
        const val NOTIF_ID_SPAN = 1000L

        const val MAX_REMEMBERED = 32
    }
}

/**
 * 通知栏标题。
 *
 * 「凭空补记一笔」和「只是把分类补全了」对用户是两件不同的事：
 * 前者要他核对金额对不对，后者只要知道改成了什么。文案不区分，用户就不知道要不要动手。
 */
private fun ReviewKind.headsUpTitle(): String = when (this) {
    ReviewKind.CREATED -> "已自动补记一笔"
    ReviewKind.ENRICHED -> "已补全分类"
}

private fun ReviewKind.headsUpText(): String = when (this) {
    ReviewKind.CREATED -> "点开可以改分类或撤销"
    ReviewKind.ENRICHED -> "点开可以改成别的分类"
}
