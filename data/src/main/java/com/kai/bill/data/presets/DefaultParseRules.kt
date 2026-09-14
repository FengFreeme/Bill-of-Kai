package com.kai.bill.data.presets

import com.kai.bill.domain.model.ParseRule
import com.kai.bill.domain.model.SourceType

/**
 * 预置解析规则（通知监听，M4）。
 *
 * 规则按来源入库，支付宝 / 微信的支付通知格式相对稳定；分组含义：
 * - amountGroup：金额捕获组（含可能的 ¥/￥ 前缀与千分位）
 * - merchantGroup：0 表示本规则暂不抽取商户（避免误抓），留待规则管理页按真机样本调优
 * - defaultAccountId：命中后套用账户（支付宝=2 / 微信=3，见 [DefaultAccounts]）
 * - defaultCategoryId：先用「其他支出=12」兜底，后续可按商户细分
 *
 * 必须使用**固定 id**（1..N）：[com.kai.bill.core.db.dao.ParseRuleDao.upsertAll]
 * 带 id 时会按主键更新，实现「改版后重新播种即覆盖」，不会出现重复规则。
 */
object DefaultParseRules {

    // 金额捕获组：必须落在「货币语境」中才认作金额，避免把卡号尾号（如 2836）等纯整数误读为金额。
    // 三种合法形态（整体为 1 个捕获组）：
    //   1) 带货币符号：        [¥￥]\s*数字
    //   2) 带小数点：          数字.dd
    //   3) 带「元」后缀：       数字\s*元
    // 纯整数（如「您账户2836」）三种都不满足 → 不会被视为金额。
    private val AMOUNT = "((?:[¥￥]\\s*[\\d,]+(?:\\.\\d{1,2})?)|[\\d,]+\\.\\d{1,2}|[\\d,]+\\s*元)"

    val all: List<ParseRule> = listOf(
        // —— 支付宝：高优先级具体模板（priority 110）——
        // 这类通知标题常为「交易提醒 / 收款到账 / 退款成功 / 余额宝」等，正文不一定带「支付宝」三字，
        // 通用「支付宝」规则（priority 100）抓不到，必须独立覆盖；放前面保证优先于通用规则。
        ParseRule(
            id = 4,
            source = SourceType.NOTIFICATION,
            matcher = "交易提醒",
            regex = "交易提醒.*?$AMOUNT(?:\\s*元)?",
            amountGroup = 1,
            merchantGroup = 0,
            timeGroup = 0,
            defaultCategoryId = 12L,
            defaultAccountId = 2L,
            priority = 110,
            enabled = true
        ),
        ParseRule(
            id = 3,
            source = SourceType.NOTIFICATION,
            matcher = "转出成功",
            regex = "转出成功.*?$AMOUNT(?:\\s*元)?",
            amountGroup = 1,
            merchantGroup = 0,
            timeGroup = 0,
            defaultCategoryId = 12L,
            defaultAccountId = 2L,
            priority = 110,
            enabled = true
        ),
        ParseRule(
            id = 5,
            source = SourceType.NOTIFICATION,
            matcher = "收款到账",
            regex = "收款到账.*?$AMOUNT(?:\\s*元)?",
            amountGroup = 1,
            merchantGroup = 0,
            timeGroup = 0,
            defaultCategoryId = 12L,
            defaultAccountId = 2L,
            priority = 110,
            enabled = true
        ),
        ParseRule(
            id = 6,
            source = SourceType.NOTIFICATION,
            matcher = "退款成功",
            regex = "退款成功.*?$AMOUNT(?:\\s*元)?",
            amountGroup = 1,
            merchantGroup = 0,
            timeGroup = 0,
            defaultCategoryId = 12L,
            defaultAccountId = 2L,
            priority = 110,
            enabled = true
        ),
        ParseRule(
            id = 7,
            source = SourceType.NOTIFICATION,
            matcher = "余额宝",
            regex = "余额宝.*?$AMOUNT(?:\\s*元)?",
            amountGroup = 1,
            merchantGroup = 0,
            timeGroup = 0,
            defaultCategoryId = 12L,
            defaultAccountId = 2L,
            priority = 110,
            enabled = true
        ),
        ParseRule(
            id = 13,
            source = SourceType.NOTIFICATION,
            matcher = "退款",
            // 金额既可能在「退款」之前（你收到一笔29.8元退款），也可能在其后（退款成功 ¥12.00 已由 id=6 覆盖），双向兜底
            regex = "(?:$AMOUNT.*?退款|退款.*?$AMOUNT)",
            amountGroup = 1,
            merchantGroup = 0,
            timeGroup = 0,
            defaultCategoryId = 12L,
            defaultAccountId = 2L,
            priority = 110,
            enabled = true
        ),
        // —— 微信：高优先级具体模板（priority 110）——
        ParseRule(
            id = 8,
            source = SourceType.NOTIFICATION,
            matcher = "微信红包",
            regex = "微信红包.*?$AMOUNT",
            amountGroup = 1,
            merchantGroup = 0,
            timeGroup = 0,
            defaultCategoryId = 12L,
            defaultAccountId = 3L,
            priority = 110,
            enabled = true
        ),
        ParseRule(
            id = 9,
            source = SourceType.NOTIFICATION,
            matcher = "收到一笔转账",
            regex = "收到一笔转账.*?$AMOUNT",
            amountGroup = 1,
            merchantGroup = 0,
            timeGroup = 0,
            defaultCategoryId = 12L,
            defaultAccountId = 3L,
            priority = 110,
            enabled = true
        ),
        // —— 银行 App：高优先级具体模板（priority 110）——
        // 招商银行通知示例：「您账户2836于09月13日22:49在【财付通-微信支付-微信转账】发生快捷支付扣款，人民币0.10元」
        // 用「快捷支付扣款」做 matcher，能覆盖快捷支付扣款、消费扣款等动账类通知。
        ParseRule(
            id = 10,
            source = SourceType.NOTIFICATION,
            matcher = "快捷支付扣款",
            regex = "快捷支付扣款.*?$AMOUNT(?:\\s*元)?",
            amountGroup = 1,
            merchantGroup = 0,
            timeGroup = 0,
            defaultCategoryId = 12L,
            defaultAccountId = 4L,          // 储蓄卡，见 [DefaultAccounts]
            priority = 110,
            enabled = true
        ),
        // 银行通用扣款/支出（priority 110）：覆盖「您账户…（扣款/消费/支出…）金额」类
        // 如工行「您账户****1234于…发生消费支出人民币88.50元」
        ParseRule(
            id = 11,
            source = SourceType.NOTIFICATION,
            matcher = "您账户",
            regex = "您账户.*?(扣款|消费|支出|转出|取现|代付|还款|缴费).*?$AMOUNT(?:\\s*元)?",
            amountGroup = 2,
            merchantGroup = 0,
            timeGroup = 0,
            defaultCategoryId = 12L,
            defaultAccountId = 4L,
            priority = 110,
            enabled = true
        ),
        // 银行通用动账（priority 110）：覆盖「您尾号…（扣款/消费/入账/存入…）金额」类
        // 如建行「您尾号1234的储蓄卡9月13日POS消费支出人民币88.50元」、中行「您尾号5678账户入账人民币100.00元」
        ParseRule(
            id = 12,
            source = SourceType.NOTIFICATION,
            matcher = "您尾号",
            regex = "您尾号.*?(扣款|消费|支出|转出|取现|代付|还款|缴费|入账|存入|收款|代发).*?$AMOUNT(?:\\s*元)?",
            amountGroup = 2,
            merchantGroup = 0,
            timeGroup = 0,
            defaultCategoryId = 12L,
            defaultAccountId = 4L,
            priority = 110,
            enabled = true
        ),
        // —— 通用兜底（priority 100）：标题即「支付宝」/「微信支付」的其余通知 ——
        ParseRule(
            id = 1,
            source = SourceType.NOTIFICATION,
            matcher = "支付宝",
            // 金额后「元」字可选；部分通知为「¥12.00」无「元」（扫码到账、转账），放宽以降低漏抓。
            regex = "支付宝.*?$AMOUNT(?:\\s*元)?",
            amountGroup = 1,
            merchantGroup = 0,
            timeGroup = 0,
            defaultCategoryId = 12L,
            defaultAccountId = 2L,
            priority = 100,
            enabled = true
        ),
        ParseRule(
            id = 2,
            source = SourceType.NOTIFICATION,
            matcher = "微信支付",
            regex = "微信支付.*?$AMOUNT",
            amountGroup = 1,
            merchantGroup = 0,
            timeGroup = 0,
            defaultCategoryId = 12L,
            defaultAccountId = 3L,
            priority = 100,
            enabled = true
        )
    )
}
