package com.kai.bill.review

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.kai.bill.core.common.overlay.ReviewKind
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.data.notify.ReviewCardContract
import com.kai.bill.feature.review.ReviewCardRoute
import dagger.hilt.android.AndroidEntryPoint

/**
 * 确认卡片 —— **点通知**进来的那条路径。
 *
 * 自动弹出那条路径**不走这里**：它由无障碍悬浮层承载（见 [OverlayReviewCardHost]），
 * 因为后台启动 Activity 的官方豁免里没有「无障碍服务」，系统会静默拦截，
 * 真机表现就是「卡片一次都没弹出来过」。
 *
 * 本 Activity 只服务于「用户主动点通知」这一种前台场景 —— 那种情况下启动 Activity
 * 不受任何限制，这也是当初保留它作为**兜底通道**的原因：
 * 悬浮层挂不上（服务被停 / 进程异常）时，至少还有通知可点。
 *
 * 两个刻意的选择：
 * 1. `noHistory` + `excludeFromRecents`：它是一次性浮层，不该出现在最近任务里，
 *    也不该在用户切走后还留在栈里；
 * 2. 改分类**留在卡片里完成**（不再跳主界面的编辑页）—— 两条路径的行为必须一致，
 *    否则「悬浮层里能直接改、点通知进来却要跳走」会让人困惑。
 *
 * 由 `data` 层用显式 action 拉起（见 [ReviewCardContract]）—— 那里看不到本类，
 * 因此两边只共享 action 与 extra 字符串常量，而不是类引用。
 */
@AndroidEntryPoint
class ReviewCardActivity : ComponentActivity() {

    /**
     * 当前展示的账单 id。
     *
     * 用可观察状态而不是普通字段：卡片已在前台时又记了一笔，系统会把新 Intent
     * 交给 [onNewIntent]（`singleTop`），只有可观察状态才能让已组合的界面换成新内容。
     */
    private val billIdState = mutableStateOf(0L)

    /** 卡片种类（新建 / 补分类）；同样要能随 [onNewIntent] 变化 */
    private val kindState = mutableStateOf(ReviewKind.CREATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        billIdState.value = intent.readReviewBillId()
        kindState.value = intent.readReviewKind()

        setContent {
            BillOfKaiTheme {
                ReviewCardRoute(
                    billId = billIdState.value,
                    kind = kindState.value,
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        billIdState.value = intent.readReviewBillId()
        kindState.value = intent.readReviewKind()
    }
}

/**
 * 「去改这一笔」的 Intent 契约。
 *
 * 卡片本身**不再用它**（改分类已改为在卡内完成），但主界面仍然接受它 ——
 * 保留是为了让「带账单 id 启动 App 并直接进编辑页」这条外部入口继续可用。
 */
object EditBillContract {

    /** Intent extra：要编辑的账单主键 */
    const val EXTRA_EDIT_BILL_ID = "com.kai.bill.extra.EDIT_BILL_ID"
}

private fun Intent?.readReviewBillId(): Long =
    this?.getLongExtra(ReviewCardContract.EXTRA_BILL_ID, 0L) ?: 0L

/** 缺省按「新建一笔」展示：老版本通知里没有这个 extra，不能因此崩掉或显示空白 */
private fun Intent?.readReviewKind(): ReviewKind =
    runCatching {
        this?.getStringExtra(ReviewCardContract.EXTRA_REVIEW_KIND)
            ?.let { name -> ReviewKind.valueOf(name) }
    }.getOrNull() ?: ReviewKind.CREATED
