package com.kai.bill.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kai.bill.data.R
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动记账成功后弹系统通知，给用户即时反馈。
 *
 * 仅对 [com.kai.bill.domain.model.CaptureResult.PARSED_AND_SAVED] 触发；
 * 重复跳过 / 解析失败不弹，避免刷屏。
 *
 * 复用前台服务已声明的 [R.drawable.ic_notification] 小图标，并新建独立通道
 * 「记账结果」（`bill_saved_v2`，[NotificationManager.IMPORTANCE_HIGH] + [NotificationCompat.PRIORITY_HIGH]）
 * 以便以**悬浮（heads-up）横幅**形式弹出。
 *
 * 注意：Android 不允许应用「升级」已存在通知通道的重要性，因此这里使用新的通道
 * ID（`v2`）而非修改旧的 `bill_saved`（DEFAULT），否则已安装用户升级后旧通道仍是
 * DEFAULT、无法悬浮，必须卸载重装才行。
 */
@Singleton
class BillSavedNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mgr by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun notifySaved(amountCents: Long, type: BillType, source: SourceType) {
        val amount = "¥%.2f".format(amountCents / 100.0)
        val typeText = when (type) {
            BillType.EXPENSE -> "支出"
            BillType.INCOME -> "收入"
            BillType.TRANSFER -> "转账"
        }
        val sourceText = when (source) {
            SourceType.NOTIFICATION -> "通知"
            SourceType.SMS -> "短信"
            SourceType.MANUAL -> "手动"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "记账结果",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "自动记账成功提示（悬浮横幅）"
                    setShowBadge(true)
                }
                mgr.createNotificationChannel(channel)
            }
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("已记账")
            .setContentText("$amount · $typeText · $sourceText")
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        mgr.notify(NOTIF_ID, notif)
    }

    companion object {
        private const val CHANNEL_ID = "bill_saved_v2"
        private const val NOTIF_ID = 2001
    }
}
