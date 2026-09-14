package com.kai.bill.data.parser

import com.kai.bill.domain.model.ParseRule
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 金额 / 商户 / 时间字段抽取。
 *
 * 按 [ParseRule] 的分组序号从正则首个匹配里取出对应捕获组；分组为 0 表示该规则不抽取该字段。
 */
@Singleton
class FieldExtractor @Inject constructor() {

    fun extract(rawText: String, rule: ParseRule): ExtractedFields {
        val match = runCatching { rule.regex.toRegex().find(rawText) }.getOrNull() ?: return ExtractedFields()
        fun group(index: Int): String? =
            if (index > 0) match.groups[index]?.value?.takeIf { it.isNotBlank() } else null
        return ExtractedFields(
            amountText = group(rule.amountGroup),
            merchant = group(rule.merchantGroup),
            timeText = group(rule.timeGroup)
        )
    }
}

data class ExtractedFields(
    val amountText: String? = null,
    val merchant: String? = null,
    val timeText: String? = null
)
