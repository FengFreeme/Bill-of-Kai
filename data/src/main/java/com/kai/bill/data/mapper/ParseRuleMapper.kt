package com.kai.bill.data.mapper

import com.kai.bill.core.db.entity.ParseRuleEntity
import com.kai.bill.core.db.entity.SourceType as EntitySourceType
import com.kai.bill.domain.model.ParseRule
import com.kai.bill.domain.model.SourceType

/** ParseRule Entity ⇄ Domain */
object ParseRuleMapper {

    fun toDomain(entity: ParseRuleEntity): ParseRule = ParseRule(
        id = entity.id,
        source = entity.source.toDomain(),
        matcher = entity.matcher,
        regex = entity.regex,
        amountGroup = entity.amountGroup,
        merchantGroup = entity.merchantGroup,
        timeGroup = entity.timeGroup,
        defaultCategoryId = entity.defaultCategoryId,
        defaultAccountId = entity.defaultAccountId,
        priority = entity.priority,
        enabled = entity.enabled
    )

    fun toEntity(rule: ParseRule): ParseRuleEntity = ParseRuleEntity(
        id = rule.id,
        source = rule.source.toEntity(),
        matcher = rule.matcher,
        regex = rule.regex,
        amountGroup = rule.amountGroup,
        merchantGroup = rule.merchantGroup,
        timeGroup = rule.timeGroup,
        defaultCategoryId = rule.defaultCategoryId,
        defaultAccountId = rule.defaultAccountId,
        priority = rule.priority,
        enabled = rule.enabled
    )
}

private fun EntitySourceType.toDomain(): SourceType = when (this) {
    EntitySourceType.NOTIFICATION -> SourceType.NOTIFICATION
    EntitySourceType.SMS -> SourceType.SMS
    EntitySourceType.MANUAL -> SourceType.MANUAL
}

private fun SourceType.toEntity(): EntitySourceType = when (this) {
    SourceType.NOTIFICATION -> EntitySourceType.NOTIFICATION
    SourceType.SMS -> EntitySourceType.SMS
    SourceType.MANUAL -> EntitySourceType.MANUAL
}
