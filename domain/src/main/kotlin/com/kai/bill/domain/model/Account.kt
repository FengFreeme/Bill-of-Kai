package com.kai.bill.domain.model

/**
 * 账户 —— 统计的第二维度。
 *
 * 账户**只归档不删除**：历史账单通过 `accountId` 关联账户，
 * 物理删除会让老账单变成孤儿数据（要么显示成空白，要么整个查不出来）。
 *
 * @property id 主键，0 表示尚未落库
 * @property name 账户名，如「招行储蓄卡」
 * @property icon 图标标识
 * @property type 账户类型
 * @property sortOrder 排序号，升序
 * @property isArchived 是否归档；归档后不再出现在「记一笔」的账户选择器中，但历史流水照常可查
 */
data class Account(

    val id: Long = 0,

    val name: String,

    val icon: String?,

    val type: AccountType,

    val sortOrder: Int,

    val isArchived: Boolean
)
