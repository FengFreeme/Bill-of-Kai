package com.kai.bill.feature.settings.review

import com.kai.bill.domain.model.PendingBill

/**
 * 待确认页状态。
 *
 * @property items 待确认记录（按入队时间倒序，最新的在最上面）
 * @property categoryNames 分类 id → 名称。建议行要显示「咖啡奶茶」而不是「24」，
 *           因此列表与分类表在这里合流 —— 与「记一笔」页的处理方式一致
 * @property accountNames 账户 id → 名称，同上
 * @property busy 正在处理某条（落库 + 删行是两步，期间禁用按钮避免重复点）
 * @property editBillId 一次性事件：需要跳到编辑页的账单 id；UI 导航后调
 *           [NeedsReviewViewModel.onEditNavigationHandled] 清空
 * @property errorMessage 出错提示，点一下清除
 */
data class NeedsReviewUiState(
    val items: List<PendingBill> = emptyList(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val accountNames: Map<Long, String> = emptyMap(),
    val busy: Boolean = false,
    val editBillId: Long? = null,
    val errorMessage: String? = null
) {

    /** 一条待确认都没有：显示空态 */
    val isEmpty: Boolean get() = items.isEmpty()

    /**
     * 有几条能一键接受。
     *
     * 「全部接受建议」只在 > 0 时显示：判不出方向的条目压根没有建议可接受，
     * 给一个点了没反应的按钮比不给更让人困惑。
     */
    val acceptableCount: Int get() = items.count { it.hasSuggestion }
}
