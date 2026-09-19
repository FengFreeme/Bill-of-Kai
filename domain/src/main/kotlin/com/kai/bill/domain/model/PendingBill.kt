package com.kai.bill.domain.model

/**
 * 待确认账单 —— 通知到手了、金额也抽到了，但「这笔算什么」需要用户拍板。
 *
 * 建模要点：
 * - **它不是 [Bill]**：连 [type] 都还没定，硬塞进账单表只会逼着到处填假值；
 * - 每条都带一套 `suggested*` 建议值，用户点一下「接受建议」即可落成正式账单 ——
 *   没有建议值的话，待确认列表就变成了「要用户从头记一遍账」，那还不如不采集；
 * - [reason] 是给用户看的解释（「因为『转出成功』，可能只是账户互转」），
 *   也是后续统计「哪类词最常判不出来」的依据。
 *
 * @property amountCents 金额，单位「分」，恒为正数
 * @property suggestedType 建议方向；null 表示连方向都判不出来
 * @property suggestedCategoryId 建议分类
 * @property suggestedAccountId 建议账户（按通知来源 App 判定）
 * @property suggestedCountInStats 建议是否计入统计；「自己的钱搬家」与红包固定为 false
 * @property matchedKeyword 命中的词，列表用它解释「为什么把你叫过来」
 * @property tradeTimeMillis 交易发生时间（通知投递时间），不是入队时间
 * @property source 采集来源
 * @property rawText 原始通知全文，词表热更新后重解析的依据
 * @property dedupHash 去重键，与 [Bill.dedupHash] 同一套算法
 * @property createdAt 入队时间，保留期按它计算
 */
data class PendingBill(

    val id: Long = 0,

    val amountCents: Long,

    val suggestedType: BillType?,

    val suggestedCategoryId: Long?,

    val suggestedAccountId: Long?,

    val suggestedCountInStats: Boolean,

    val reason: PendingReason,

    val matchedKeyword: String?,

    val tradeTimeMillis: Long,

    val source: SourceType,

    val rawText: String?,

    val dedupHash: String,

    val createdAt: Long
) {

    /**
     * 有没有可直接采纳的建议。
     *
     * 判定要求**方向与分类同时具备**：`bill` 表的 `type` 与 `categoryId` 都是非空，
     * 少任何一个都落不成一条合法账单，用户点「接受建议」只会得到一笔没有归属的假账。
     * 因此「建议不完整」与「没有建议」对列表而言是同一件事：都不给接受按钮。
     */
    val hasSuggestion: Boolean
        get() = suggestedType != null && suggestedCategoryId != null
}
