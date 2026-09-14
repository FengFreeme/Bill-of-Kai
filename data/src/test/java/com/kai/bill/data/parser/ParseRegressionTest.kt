package com.kai.bill.data.parser

import com.kai.bill.data.presets.DefaultParseRules
import com.kai.bill.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 通知解析回归测试（纯 JVM，可在 CI / 本地直接运行，无需真机）。
 *
 * 覆盖 M4 的支付宝 / 微信预置规则在不同真机通知文本形态下的命中与金额归一化，
 * 作为「多机型真机回归」的代码层基线：规则改版 / regex 调整后用本测试守住不回退。
 */
class ParseRegressionTest {

    private val rules = DefaultParseRules.all
    private val ruleEngine = RuleEngine()
    private val fieldExtractor = FieldExtractor()

    /** 模拟 BillIngestor 的解析链路：match → extract → 归一化，返回金额（分）。 */
    private fun parseAmount(text: String): Long? {
        val rule = ruleEngine.match(text, SourceType.NOTIFICATION, rules) ?: return null
        return AmountNormalizer.toCents(fieldExtractor.extract(text, rule).amountText)
    }

    @Test
    fun `支付宝-带元字能命中并归一化`() {
        assertEquals(8850L, parseAmount("支付宝 账单提醒 支出 ¥88.50 元"))
    }

    @Test
    fun `支付宝-无元字也能命中（真机扫码到账样本）`() {
        // 真机样本多为「¥12.00」无「元」，原规则强制「元」会落 PARSE_FAILED，已放宽
        assertEquals(1200L, parseAmount("支付宝 收款 ¥12.00"))
    }

    @Test
    fun `支付宝-千分位金额`() {
        assertEquals(123456L, parseAmount("支付宝 转账 ¥1,234.56"))
    }

    @Test
    fun `支付宝-交易提醒-支出通知无支付宝关键字`() {
        // 截图新样本：文本中无「支付宝」，通用支付宝规则无法命中
        assertEquals(50L, parseAmount("交易提醒 你有一笔0.50元的支出，点此查看详情。"))
    }

    @Test
    fun `支付宝-收款到账`() {
        assertEquals(50L, parseAmount("收款到账 ￥0.50 已存入余额"))
    }

    @Test
    fun `支付宝-退款成功`() {
        assertEquals(1200L, parseAmount("退款成功 ￥12.00 已退回原账户"))
    }

    @Test
    fun `支付宝-收到退款-金额在退款前`() {
        // 真实样本：「你收到一笔29.8元退款」，金额在「退款」之前，原规则漏抓
        assertEquals(2980L, parseAmount("你收到一笔29.8元退款"))
    }

    @Test
    fun `支付宝-余额宝收益`() {
        assertEquals(321L, parseAmount("余额宝 收益发放 ¥3.21"))
    }

    @Test
    fun `支付宝-转出成功-金额在支付宝之前`() {
        // 截图场景对应通知文本：金额在「支付宝余额」之前，通用支付宝规则会漏抓
        assertEquals(80L, parseAmount("转出成功 ¥0.80 到账账户 支付宝余额"))
    }

    @Test
    fun `微信支付-能命中`() {
        assertEquals(1L, parseAmount("微信支付 支付成功 ¥0.01"))
    }

    @Test
    fun `微信红包-能命中`() {
        assertEquals(520L, parseAmount("微信红包 你领取了XX的红包，金额 ¥5.20"))
    }

    @Test
    fun `微信-收到一笔转账`() {
        assertEquals(8800L, parseAmount("微信 你收到一笔转账 ¥88.00"))
    }

    @Test
    fun `招商银行-快捷支付扣款`() {
        // 通知中心原文：「您账户2836于09月13日22:49在【财付通-微信支付-微信转账】发生快捷支付扣款，人民币0.10元」
        assertEquals(10L, parseAmount("您账户2836于09月13日22:49在【财付通-微信支付-微信转账】发生快捷支付扣款，人民币0.10元"))
    }

    @Test
    fun `银行-您账户-消费支出`() {
        // 工行等常见格式：「您账户****1234于09月13日发生消费支出人民币88.50元」
        assertEquals(8850L, parseAmount("您账户****1234于09月13日发生消费支出人民币88.50元"))
    }

    @Test
    fun `银行-您尾号-消费`() {
        // 建行等常见格式：「您尾号1234的储蓄卡9月13日POS消费支出人民币88.50元」
        assertEquals(8850L, parseAmount("您尾号1234的储蓄卡9月13日POS消费支出人民币88.50元"))
    }

    @Test
    fun `银行-您尾号-入账`() {
        // 中行等常见格式：「您尾号5678账户09月13日入账人民币100.00元」
        assertEquals(10000L, parseAmount("您尾号5678账户09月13日入账人民币100.00元"))
    }

    @Test
    fun `银行-通知无金额-不误读卡号尾号2836`() {
        // 招行真实通知栏文本（金额只在 App 详情页，不在通知里）：
        // 「您账户2836于09月13日22:49在【财付通-微信支付-微信转账】发生快捷支付扣款」
        // 金额正则已收紧，2836 这类纯整数不再被误读为金额，应整体不命中。
        assertNull(parseAmount("您账户2836于09月13日22:49在【财付通-微信支付-微信转账】发生快捷支付扣款"))
    }

    @Test
    fun `无关通知不命中`() {
        assertNull(parseAmount("微信 收到一条消息：在吗"))
        assertNull(parseAmount("今日天气晴"))
    }

    @Test
    fun `AmountNormalizer-各种金额形态`() {
        assertEquals(2500L, AmountNormalizer.toCents("¥25.00"))
        assertEquals(2500L, AmountNormalizer.toCents("25元"))
        assertEquals(10000L, AmountNormalizer.toCents("￥100"))
        assertEquals(123456L, AmountNormalizer.toCents("1,234.56"))
        assertEquals(null, AmountNormalizer.toCents(""))
        assertEquals(null, AmountNormalizer.toCents("abc"))
    }

    @Test
    fun `DedupKey-相同输入稳定-不同金额不同`() {
        val a = DedupKey.build(1200, 1_700_000_000_000L, null)
        val b = DedupKey.build(1200, 1_700_000_000_000L, null)
        val c = DedupKey.build(1300, 1_700_000_000_000L, null)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
