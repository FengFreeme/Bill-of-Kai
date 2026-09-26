package com.kai.bill.review

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.kai.bill.core.common.overlay.ReviewCardReason
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.data.notify.ReviewCardContract
import com.kai.bill.feature.review.ReviewCardRoute
import dagger.hilt.android.AndroidEntryPoint

/**
 * 确认卡片 —— 识别成功并落库后自动弹出的**半透明浮层**。
 *
 * 三个刻意的选择：
 * 1. **不是 Dialog 而是 Activity**：它由通知的 `PendingIntent` 拉起，而 `PendingIntent` 只能拉
 *    Activity / Service / Receiver —— `DialogFragment` 需要先有宿主 Activity 在前台；
 * 2. **不用 `SYSTEM_ALERT_WINDOW`**：那要额外权限。自动弹卡那条路走无障碍悬浮层
 *    （后台 `startActivity` 会被系统静默拦截：「无障碍服务」**不在**官方豁免之列，见 `ReviewCardOverlay`）；
 * 3. **`noHistory` + `excludeFromRecents`**：它是一次性浮层，不该出现在最近任务里，
 *    也不该在用户切走后还留在栈里。
 *
 * 由 `data` 层用显式 action 拉起（见 [ReviewCardContract]）—— 那里看不到本类，
 * 因此两边只共享一个 action 字符串常量，而不是类引用。
 */
@AndroidEntryPoint
class ReviewCardActivity : ComponentActivity() {

    /**
     * 当前展示的账单 id。
     *
     * 用可观察状态而非普通字段：卡片已在前台时又记一笔，系统会把新 Intent 交给 [onNewIntent]，
     * 只有可观察状态才能让已组合的界面换成新内容。
     */
    private val billIdState = mutableStateOf(0L)

    /**
     * 当前展示的来由（新建 / 补分类）。与 [billIdState] 同样用可观察状态：
     * 走通知兜底路径时只能靠 Intent 把来由带进来，而卡片文案必须跟着它变（见 [ReviewCardReason]）。
     */
    private val reasonState = mutableStateOf(ReviewCardReason.CREATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        billIdState.value = intent.readReviewBillId()
        reasonState.value = intent.readReviewReason()

        setContent {
            BillOfKaiTheme {
                ReviewCardRoute(
                    billId = billIdState.value,
                    reason = reasonState.value,
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        billIdState.value = intent.readReviewBillId()
        reasonState.value = intent.readReviewReason()
    }

}

/**
 * 「从外部 Intent 直达账单编辑页」的 Intent 契约。
 *
 * 与 `data` 的 [ReviewCardContract] 分开（那个负责「谁来显示卡片」），
 * 混在一起会让 `data` 的契约表里多出一个它永不写入的字段。
 * 确认卡片改为就地选分类后已不再写它，现仅 `MainActivity` 读取，保留以维持外部 Intent 直达的通路。
 */
object EditBillContract {

    /** Intent extra：要编辑的账单主键 */
    const val EXTRA_EDIT_BILL_ID = "com.kai.bill.extra.EDIT_BILL_ID"
}

private fun Intent?.readReviewBillId(): Long =
    this?.getLongExtra(ReviewCardContract.EXTRA_BILL_ID, 0L) ?: 0L

/**
 * 读来由；缺失或取值不认识时回退「新建」。
 *
 * 回退而不是报错：这只是文案差异，旧版本发的 Intent（没有这个 extra）也应正常显示卡片。
 */
private fun Intent?.readReviewReason(): ReviewCardReason =
    this?.getStringExtra(ReviewCardContract.EXTRA_REASON)
        ?.let { name -> ReviewCardReason.entries.firstOrNull { it.name == name } }
        ?: ReviewCardReason.CREATED
