package com.kai.bill.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账户表。
 *
 * 账户是统计的第二个维度（第一个是分类），与预算无关 —— 预算不按账户分设。
 *
 * @property name 账户名，如「招行储蓄卡」
 * @property sortOrder 排序号，升序
 * @property isArchived 是否归档；归档后不再出现在「记一笔」的账户选择器中，
 *                      但历史流水仍要能查到，因此**不做物理删除**
 */
@Entity(
    tableName = "account",
    indices = [Index("type")]
)
data class AccountEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val icon: String?,

    val type: AccountType,

    val sortOrder: Int,

    val isArchived: Boolean
)
