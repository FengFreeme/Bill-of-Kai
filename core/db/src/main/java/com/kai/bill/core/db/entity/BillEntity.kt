package com.kai.bill.core.db.entity

import androidx.room.ColumnInfo
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
 * - `isRefund` 把「退款」从收入里剥离：收入侧不计入，支出侧折成负项（冲抵）
 *
 * @property amountCents 金额，单位「分」，**恒为正数**；正负语义由 [type] 决定
 * @property countInStats 是否计入统计与预算。**只对支出 / 收入有意义** ——
 *           转账本就被排除在收支之外，该字段对转账不产生任何影响
 * @property isRefund 是否退款。**退款行的 [type] 仍是 INCOME**（与来源页面的「+12.39」一致），
 *           但在统计里属于支出侧的负项；`time` / `categoryId` / `accountId` 存的是**原消费**的
 *           归属（落库时回溯原支出账单写入），因此「哪个月支出的就哪个月加回去」。
 *           统计口径与内存路径的等价实现见 `Bill.signedExpenseCents`。
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

    /**
     * 是否退款（`type` 仍是 INCOME，统计上属支出侧负项）。
     *
     * NOTE: `defaultValue = "0"` 供 `MIGRATION_2_3` 的 `ADD COLUMN` 使用 ——
     * Room 只校验实体声明过的默认值，两边写成同一个才不会被 schema 校验判成不一致。
     */
    @ColumnInfo(defaultValue = "0")
    val isRefund: Boolean = false,

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
