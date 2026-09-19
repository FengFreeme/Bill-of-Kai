package com.kai.bill.data.mapper

import com.kai.bill.core.db.entity.ParseRuleEntity
import com.kai.bill.domain.model.ParseRule

/**
 * ParseRule Entity ⇄ Domain。
 *
 * `SourceType` 转换走同包的公共入口（见 `SourceTypeMapping.kt`）——
 * 本文件原先自带一份私有实现，第三张表（待确认账单）也要用时就会撞成重复定义。
 */
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
