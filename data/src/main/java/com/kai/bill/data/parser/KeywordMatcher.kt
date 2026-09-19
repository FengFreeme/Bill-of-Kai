package com.kai.bill.data.parser

import com.kai.bill.domain.model.SourceType

/**
 * 词条匹配的公共实现 —— 「怎么从词表里挑一条」全项目只有这一份。
 *
 * S2 方向路由与 S3 分类路由共用它，避免出现「方向词按一套规则、分类词按另一套」的漂移：
 * 排序口径一旦不一致，同一个词在两级下会给出不同结果，这类问题极难排查。
 *
 * 规则：
 * 1. 先按 `reserved` 圈出**已占区间**，落在其中的出现位置一律作废
 *    （「支付」不许命中「支付宝」里的子串，详见 [MatchKeywordTables.reserved]）；
 * 2. 在剩下的出现位置里按「优先级降序 → 词长降序 → 位置靠前」取第一条。
 */
internal object KeywordMatcher {

    /**
     * 取最具体的一条命中。
     *
     * @return 命中的词条；没有可用命中返回 null
     */
    fun <T : Matchable> best(
        candidates: List<T>,
        rawText: String,
        source: SourceType,
        reserved: List<String>
    ): T? {
        val blocked = reservedSpans(rawText, reserved)
        return candidates
            .asSequence()
            .filter { it.appliesTo(source) }
            .mapNotNull { candidate ->
                position(rawText, candidate.keyword, blocked)?.let { candidate to it }
            }
            .sortedWith(
                compareByDescending<Pair<T, IntRange>> { it.first.priority }
                    .thenByDescending { it.first.keyword.length }
                    .thenBy { it.second.first }
            )
            .firstOrNull()
            ?.first
    }

    /** 保留词占用的区间；无序也无妨，[position] 只做重叠判断 */
    private fun reservedSpans(rawText: String, reserved: List<String>): List<IntRange> =
        reserved
            .filter { it.isNotBlank() }
            .flatMap { occurrences(rawText, it) }

    /** 某个词在原文中的全部出现区间 */
    private fun occurrences(rawText: String, keyword: String): List<IntRange> = buildList {
        var from = 0
        while (true) {
            val at = rawText.indexOf(keyword, from, ignoreCase = true)
            if (at < 0) break
            add(at until at + keyword.length)
            from = at + keyword.length
        }
    }

    /**
     * 该词第一个**未落入已占区间**的出现位置。
     *
     * 逐个出现位置往后找，而不是「第一次出现被占用就作废」——
     * `支付宝 支付 ¥88` 里「支付」出现两次：第一次在保留词内，第二次才是真正的动作词。
     */
    private fun position(rawText: String, keyword: String, blocked: List<IntRange>): IntRange? {
        if (keyword.isBlank()) return null
        var from = 0
        while (true) {
            val at = rawText.indexOf(keyword, from, ignoreCase = true)
            if (at < 0) return null
            val range = at until at + keyword.length
            if (blocked.none { overlaps(it, range) }) return range
            from = at + 1
        }
    }

    /** 两个区间是否相交（不用 `IntRange.overlaps`：该扩展在当前 Kotlin 版本不可用） */
    private fun overlaps(a: IntRange, b: IntRange): Boolean =
        a.first <= b.last && b.first <= a.last
}
