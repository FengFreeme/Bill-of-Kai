package com.kai.bill.domain.model

/**
 * 解析规则 —— **规则是数据而非代码**。
 *
 * 支付宝 / 微信 / 银行的通知文案会改版，若规则写死在代码里就只能靠发版修复；
 * 入库后可在 App 内编辑，将来还能在用户之间分享。
 *
 * @property id 主键，0 表示尚未落库
 * @property source 适用的来源；匹配前先按来源过滤，避免上千条规则全量参与正则回溯
 * @property matcher 快速预筛子串；先用 `contains` 粗筛再跑正则，是主要的性能保障
 * @property regex 完整正则，含金额 / 商户 / 时间三个分组
 * @property amountGroup 金额分组序号，从 1 开始；0 表示该规则不抽取金额
 * @property merchantGroup 商户分组序号，规则同上
 * @property timeGroup 时间分组序号，规则同上
 * @property defaultCategoryId 命中后套用的默认分类
 * @property defaultAccountId 命中后套用的默认账户
 * @property priority 优先级，**降序**匹配，数值越大越先尝试
 * @property enabled 是否启用；禁用而非删除，便于回滚
 */
data class ParseRule(

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
