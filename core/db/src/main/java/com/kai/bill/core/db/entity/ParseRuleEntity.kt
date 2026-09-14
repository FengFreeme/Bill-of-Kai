package com.kai.bill.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 解析规则表 —— **规则入库而非硬编码**。
 *
 * 支付宝 / 微信 / 银行的通知文案会改版，若规则写死在代码里就只能发版修复；
 * 入库后可在 App 内编辑，甚至将来支持用户之间分享规则。
 *
 * @property source 该规则适用的来源；匹配时先按来源过滤，减少无谓的正则尝试
 * @property matcher 快速预筛子串；先用 `contains` 粗筛再跑正则，避免上千条规则全量回溯
 * @property regex 完整正则，含金额 / 商户 / 时间三个命名或编号分组
 * @property amountGroup 金额分组序号，从 1 开始；0 表示该规则不抽取金额
 * @property merchantGroup 商户分组序号，规则同上
 * @property timeGroup 时间分组序号，规则同上
 * @property defaultCategoryId 命中后套用的默认分类
 * @property defaultAccountId 命中后套用的默认账户
 * @property priority 优先级，**降序**匹配，数值越大越先尝试
 * @property enabled 是否启用；禁用而非删除，便于回滚
 */
@Entity(
    tableName = "parse_rule",
    indices = [Index("source"), Index("priority")]
)
data class ParseRuleEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val source: SourceType,

    val matcher: String,

    val regex: String,

    val amountGroup: Int,

    val merchantGroup: Int,

    val timeGroup: Int,

    val defaultCategoryId: Long?,

    val defaultAccountId: Long?,

    val priority: Int,

    val enabled: Boolean
)
