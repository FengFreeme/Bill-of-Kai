package com.kai.bill.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账单表 —— 全 App 唯一的数据源核心表。
 *
 * 设计要点：
 * - `dedupHash` 上建**唯一索引**，从数据库层面杜绝重复入库（去重的第一道防线）
 * - `rawText` 存原始通知/短信全文，是解析规则热更新后批量重解析的命根子
 * - `countInStats` 把「是否计入统计」从类型中剥离，一次性覆盖转账/报销/AA 等边界场景
 *
 * @property amountCents 金额，单位「分」，**恒为正数**；正负语义由 [type] 决定
 * @property type 账单类型：支出 / 收入 / 转账
 * @property countInStats 是否计入统计与预算。**只对支出 / 收入有意义** ——
 *           转账本就被排除在收支之外，该字段对转账不产生任何影响
 * @property categoryId 分类 ID；当 [type] 为 [BillType.TRANSFER] 时复用为「还款/互转/借款」子类型
 * @property accountId 账户 ID，可空（未指定账户的流水）
 * @property merchant 商户名，解析得出
 * @property time 交易发生时间（毫秒），**不是记录时间** —— 补记场景必须用真实交易时间
 * @property source 账单来源，用于定位解析失败原因
 * @property rawText 原始通知/短信全文
 * @property dedupHash 去重键：md5(金额 + 秒级时间桶 + 类型 + 商户)，唯一索引。
 *           算法见 `data` 模块的 `DedupKey`（唯一真相在这里只有「唯一索引」这一条约定）
 * @property createdAt 记录创建时间（毫秒）
 * @property updatedAt 记录最后修改时间（毫秒）
 */
@Entity(
    tableName = "bill",
    indices = [
        Index("time"),
        Index("categoryId"),
        Index("accountId"),
        // 数据库层防重复：同一笔被重复投递（系统重发通知、补投）时，后到的插入会失败
        Index(value = ["dedupHash"], unique = true)
    ]
)
data class BillEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val amountCents: Long,

    val type: BillType,

    val countInStats: Boolean,

    val categoryId: Long,

    val accountId: Long?,

    val merchant: String?,

    val note: String?,

    val time: Long,

    val source: SourceType,

    val rawText: String?,

    val dedupHash: String,

    val createdAt: Long,

    val updatedAt: Long
)
