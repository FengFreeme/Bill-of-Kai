package com.kai.bill.data.parser

import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.PendingReason
import com.kai.bill.domain.model.SourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2 方向路由：判断这笔钱是支出、收入、转账，还是需要人工确认。
 *
 * 判定顺序**不可调换**，每一步都有明确的错账后果：
 * 1. **排除词**：失败 / 预告 / 营销文本同样带金额，不先拦住就会记成真实收支
 *    （`支付宝 支付失败 ¥88.00`、`本期应还 ¥1,200`、`满50元减10元`）；
 * 2. **方向词**：取最具体的一条（优先级降序 → 词长降序 → 位置靠前）；
 * 3. **分类词兜底方向**：有商户 / 场景词但没有动作词时（`支付宝 星巴克 ¥38`），
 *    用分类词声明的方向定方向 —— 否则这类最常见的小额支付会全部掉进「判不出方向」；
 * 4. **都没有**：`DIRECTION_UNKNOWN`，交人工确认。
 *
 * 「短词不许进已占区间」的机制在 [KeywordMatcher] 里实现（共用给 S3）：
 * 它保证「支付」不会命中「支付宝」里的子串，而 `支付宝 支付 ¥88` 里的裸「支付」照常识别。
 *
 * 词表通过参数传入（P0 常量 / P1 数据库），本类不含任何来源假设，纯函数、可单测。
 */
@Singleton
class DirectionRouter @Inject constructor() {

    fun route(rawText: String, source: SourceType, keywords: MatchKeywordTables): DirectionHit {
        KeywordMatcher.best(keywords.exclude, rawText, source, keywords.reserved)?.let { hit ->
            return DirectionHit(route = MatchRoute.EXCLUDE, matchedKeyword = hit.keyword)
        }

        KeywordMatcher.best(keywords.direction, rawText, source, keywords.reserved)?.let { hit ->
            return DirectionHit(route = hit.route, matchedKeyword = hit.keyword)
        }

        KeywordMatcher.best(keywords.category, rawText, source, keywords.reserved)?.let { hit ->
            return DirectionHit(route = hit.direction.toRoute(), matchedKeyword = hit.keyword)
        }

        return DirectionHit(route = null, matchedKeyword = null)
    }
}

/**
 * 方向判定的结果。
 *
 * @property route 命中的走向；null 表示既没有方向词也没有分类词命中（判不出方向）
 * @property matchedKeyword 命中的词，待确认列表直接用它向用户解释「为什么把你叫过来」
 */
data class DirectionHit(
    val route: MatchRoute?,
    val matchedKeyword: String?
) {

    /** 可直接落库的类型；待确认 / 排除时为 null */
    val type: BillType? get() = route?.type

    /**
     * 待确认时的建议方向（用户点「接受建议」用的类型）。
     *
     * 判不出方向时为 null —— 那时连倾向都给不出来，列表上也不该给「接受建议」按钮。
     */
    val suggestedType: BillType? get() = route?.suggestedType

    /** 需要人工确认时的原因；判不出方向也是一种「待确认」 */
    val pendingReason: PendingReason?
        get() = if (route == null) PendingReason.DIRECTION_UNKNOWN else route.pendingReason

    /** 是否被排除词命中（静默丢弃） */
    val isExcluded: Boolean get() = route == MatchRoute.EXCLUDE

    /** 是否需要进待确认列表 */
    val isPending: Boolean get() = !isExcluded && pendingReason != null

    /** 是否计入统计：只有明确类型且不是转账才计入 */
    val countInStats: Boolean
        get() = type != null && type != BillType.TRANSFER
}

/** 分类词声明的方向 → 走向 */
private fun BillType.toRoute(): MatchRoute = when (this) {
    BillType.EXPENSE -> MatchRoute.EXPENSE
    BillType.INCOME -> MatchRoute.INCOME
    BillType.TRANSFER -> MatchRoute.TRANSFER
}
