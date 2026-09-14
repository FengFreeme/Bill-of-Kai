package com.kai.bill.feature.stats

import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.stats.CategoryStat
import com.kai.bill.feature.home.DailyGroup

/**
 * 分类详情页的 UI 状态。
 *
 * 「总览」与「某个子分类」共用同一份状态：切换顶部 tab 时由 ViewModel 重新收窄口径，
 * 因此 [totalCents] / [billCount] / [groups] 始终是**当前口径**下的数字。
 *
 * @property categoryId 当前分类（一级）ID
 * @property categoryName 分类名，用于标题
 * @property childTabs 该分类下的二级分类；用于顶部 tab 行
 * @property selectedChildId 选中的二级分类 ID；null 表示「总览」
 * @property totalCents 当前口径的支出合计，单位「分」
 * @property averageCents 当前口径的单笔均值，单位「分」
 * @property billCount 当前口径的笔数
 * @property composition 子分类构成（总览时为各子分类占比；单看某子分类时只有它一项）
 * @property groups 按日分组的流水，最新在前
 */
data class CategoryDetailUiState(
    val categoryId: Long = 0L,
    val categoryName: String = "",
    val childTabs: List<Category> = emptyList(),
    val selectedChildId: Long? = null,
    val totalCents: Long = 0L,
    val averageCents: Long = 0L,
    val billCount: Int = 0,
    val composition: List<CategoryStat> = emptyList(),
    val groups: List<DailyGroup> = emptyList()
) {
    /** 没有任何流水时不画空图表 */
    val isEmpty: Boolean
        get() = billCount == 0
}
