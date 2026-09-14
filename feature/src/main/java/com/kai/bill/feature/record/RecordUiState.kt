package com.kai.bill.feature.record

import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.CategoryNode

/**
 * 记一笔页面的 UI 状态（新增 / 编辑复用）。
 *
 * 金额以字符串 [amountText]（「元」，如 `"12.34"`）为唯一真源，[amountCents] 由其推导，
 * 避免浮点误差。分类与账户来自预置仓库的热流；编辑态由 [billId] 非零标识。
 *
 * 关闭页等一次性导航走 [RecordViewModel.events]，不放进本状态，避免 StateFlow 重放。
 *
 * @property billId 编辑目标账单主键；0 表示新增
 * @property isEditing 是否编辑态
 * @property type 当前账单类型（支出 / 收入）
 * @property amountText 已输入金额（元字符串）
 * @property amountCents 由 [amountText] 推导的金额（分）
 * @property categoryId 选中的分类 ID
 * @property accountId 选中的账户 ID
 * @property note 备注
 * @property tradeTimeMillis 交易发生时间（毫秒）；新增默认「现在」，可由日期键改写
 * @property categoryTree 当前类型下的两级分类（一级 + 其下二级）
 * @property expandedParentId 当前展开的一级分类 ID；选中二级时指向它所属的一级
 * @property accounts 活跃账户列表
 * @property canSave 是否满足保存条件（金额 > 0 且已选分类；账户允许为空）
 * @property errorMessage 最近一次保存/删除失败提示；成功操作后清空
 */
data class RecordUiState(
    val billId: Long = 0L,
    val isEditing: Boolean = false,
    val type: BillType = BillType.EXPENSE,
    val amountText: String = "",
    val amountCents: Long = 0L,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val note: String = "",
    val tradeTimeMillis: Long = 0L,
    val categoryTree: List<CategoryNode> = emptyList(),
    val expandedParentId: Long? = null,
    val accounts: List<Account> = emptyList(),
    val canSave: Boolean = false,
    val countInStats: Boolean = true,
    val errorMessage: String? = null
)

/**
 * 记一笔一次性事件（导航 / 提示），通过 SharedFlow 下发，避免 State 重放。
 */
sealed interface RecordEvent {
    /** 保存或删除成功，路由应关闭本页 */
    data object Close : RecordEvent
}
