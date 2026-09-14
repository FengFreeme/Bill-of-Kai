package com.kai.bill.data.parser

import com.kai.bill.domain.model.ParseRule
import com.kai.bill.domain.model.SourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按优先级匹配 [ParseRule]。
 *
 * 先用 [ParseRule.matcher] 子串粗筛（contains），命中再跑完整正则，避免每条通知都做全量正则回溯。
 * 规则来自 [com.kai.bill.domain.repository.ParseRuleRepository]（已按 priority 降序），这里取第一条命中即止。
 */
@Singleton
class RuleEngine @Inject constructor() {

    fun match(rawText: String, source: SourceType, rules: List<ParseRule>): ParseRule? {
        for (rule in rules) {
            if (rule.source != source || !rule.enabled) continue
            if (rule.matcher.isNotEmpty() && !rawText.contains(rule.matcher, ignoreCase = true)) continue
            if (rule.regex.toRegex().containsMatchIn(rawText)) return rule
        }
        return null
    }
}
