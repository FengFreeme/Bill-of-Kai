package com.kai.bill.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 待确认账单表 —— 判不准的通知在这里等用户拍板。
 *
 * **为什么不塞进 `bill` 表**：[BillEntity.type] 与 [BillEntity.categoryId] 都是非空，
 * 而待确认恰恰就是这两项定不下来；塞进去必须先填假值，那些假值会立刻污染
 * `StatsDao` 的全部统计 SQL（统计只认 `countInStats`，但分类 / 账户维度会跟着错）。
 *
 * 与 `bill` 表对齐的三处约定：
 * - `time` 是**交易时间**（通知 `postTime`），不是记录时间
 * - `rawText` 存原文，是词表热更新后重解析的依据
 * - `dedupHash` 与 `bill` 表**同一套算法**并同样建唯一索引：既保证同一笔通知不重复入队，
 *   也让用户确认落库时天然防重（确认时直接复用这个 hash）
 *
 * @property amountCents 金额，单位「分」，恒为正数
 * @property suggestedType 建议方向；null 表示连方向都判不出来（列表里显示为待定）
 * @property suggestedCategoryId 建议分类；可为 null
 * @property suggestedAccountId 建议账户；按通知来源 App 判定，可为 null
 * @property suggestedCountInStats 建议是否计入统计。「自己的钱搬家」类固定为 false，
 *           用户点「接受建议」时落库就用这个值
 * @property reason 为什么进待确认，决定列表里的文案
 * @property matchedKeyword 命中的词（如「转出成功」），列表直接展示「因为『转出成功』」
 * @property source 采集来源，排查「这条是哪来的」
 * @property createdAt 入队时间；30 天自动清理按它算
 */
@Entity(
    tableName = "pending_bill",
    indices = [
        // 列表按入队时间倒序，最近的待确认在最前
        Index("createdAt"),
        // 数据库层防重复：同一笔通知反复投递（含「只留最后一条」的更新型通知）不会刷屏
        Index(value = ["dedupHash"], unique = true)
    ]
)
data class PendingBillEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val amountCents: Long,

    val suggestedType: BillType?,

    val suggestedCategoryId: Long?,

    val suggestedAccountId: Long?,

    val suggestedCountInStats: Boolean,

    val reason: PendingReason,

    val matchedKeyword: String?,

    val time: Long,

    val source: SourceType,

    val rawText: String?,

    val dedupHash: String,

    val createdAt: Long
)
