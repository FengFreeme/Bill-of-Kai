package com.kai.bill.data.capture.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 微信详情页判定与金额结构抽取 —— 对照真机截图版式。
 *
 * 页型：`(交易详情 || 全部账单) && (支付时间 || 支付方式 || 转账时间)`
 * 金额：「当前状态」之前的第一个 `±x.xx`
 */
class WechatBillPageHeuristicsTest {

    // ---------------- 页型 ----------------

    @Test
    fun `全部账单加支付时间-是详情页`() {
        assertTrue(
            WechatBillPageHeuristics.isBillDetail(
                "全部账单 本服务由财付通提供 当前状态 支付成功 支付时间 2026年9月18日 19:49:03 " +
                    "商品 余额充值 支付方式 零钱 交易单号 4500000479202609184073820364 账单服务"
            )
        )
    }

    @Test
    fun `全部账单加转账时间-是详情页`() {
        // 转账页常无「支付时间」，靠「转账时间」配套
        assertTrue(
            WechatBillPageHeuristics.isBillDetail(
                "全部账单 转账详情 凯 -1.00 当前状态 对方已收钱 " +
                    "转账时间 2026年9月22日 22:46:48 转账单号 1000050001202609220329865215980 账单服务"
            )
        )
    }

    @Test
    fun `全部账单加支付方式-是详情页`() {
        assertTrue(
            WechatBillPageHeuristics.isBillDetail(
                "全部账单 当前状态 支付成功 收款方备注 二维码收款 支付方式 零钱 账单服务"
            )
        )
    }

    @Test
    fun `交易详情加支付时间-是详情页`() {
        // 小程序交易详情：顶栏是「交易详情」而非「全部账单」
        assertTrue(
            WechatBillPageHeuristics.isBillDetail(
                "交易详情 当前状态 支付成功 支付时间 2026年9月18日 19:49:03 支付方式 零钱"
            )
        )
    }

    @Test
    fun `仅有全部账单无字段-不是详情页`() {
        assertFalse(
            WechatBillPageHeuristics.isBillDetail("全部账单 筛选 结余 -11.80")
        )
    }

    @Test
    fun `仅有交易详情无字段-不是详情页`() {
        // 小程序「服务」Tab 树里常仍有「交易详情」四字，但没有时间/方式字段
        assertFalse(
            WechatBillPageHeuristics.isBillDetail("交易详情 服务 推荐")
        )
    }

    @Test
    fun `仅有支付时间无顶栏-不是详情页`() {
        assertFalse(
            WechatBillPageHeuristics.isBillDetail("支付时间 2026年9月18日 19:49:03 支付方式 零钱")
        )
    }

    @Test
    fun `空文本-不是详情页`() {
        assertFalse(WechatBillPageHeuristics.isBillDetail(""))
        assertFalse(WechatBillPageHeuristics.isBillDetail("   "))
    }

    // ---------------- 金额 ----------------

    @Test
    fun `支出-当前状态前取第一个金额`() {
        assertEquals(
            1180L,
            WechatBillPageHeuristics.extractAmountCents(
                "全部账单 得力文具官方旗舰店 -11.80 当前状态 支付成功 " +
                    "支付时间 2026年9月19日 21:37:05 商品 得力按动笔芯 " +
                    "支付方式 零钱 交易单号 4200002947202609191234567890 账单服务"
            )
        )
        assertEquals(
            5500L,
            WechatBillPageHeuristics.extractAmountCents(
                "全部账单 滴滴出行 -55.00 当前状态 支付成功 " +
                    "支付时间 2026年9月19日 15:41:05 支付方式 零钱 账单服务"
            )
        )
    }

    @Test
    fun `退款-取当前状态前金额而非已退款后缀`() {
        // 退款页后面还有「已退款¥12.39」，必须取「当前状态」之前的第一个，不能取最后一个
        assertEquals(
            1239L,
            WechatBillPageHeuristics.extractAmountCents(
                "全部账单 退款-商家 +12.39 当前状态 已退款 " +
                    "退款时间 2026年9月19日 13:55:00 已退款¥12.39 账单服务"
            )
        )
    }

    @Test
    fun `转账-当前状态前取金额`() {
        assertEquals(
            100L,
            WechatBillPageHeuristics.extractAmountCents(
                "全部账单 转账详情 凯 -1.00 当前状态 对方已收钱 " +
                    "转账时间 2026年9月22日 22:46:48 转账单号 1000050001202609220329865215980 账单服务"
            )
        )
    }

    @Test
    fun `带元后缀也能抽`() {
        assertEquals(
            1400L,
            WechatBillPageHeuristics.extractAmountCents(
                "全部账单 商户 14.00元 当前状态 支付成功 支付时间 2026年9月18日 19:49:03"
            )
        )
    }

    @Test
    fun `无当前状态-不抽金额`() {
        assertNull(
            WechatBillPageHeuristics.extractAmountCents(
                "全部账单 滴滴出行 -55.00 支付成功 支付时间 2026年9月19日 15:41:05"
            )
        )
    }

    @Test
    fun `当前状态前无金额-不抽`() {
        assertNull(
            WechatBillPageHeuristics.extractAmountCents(
                "全部账单 当前状态 支付成功 支付时间 2026年9月19日 15:41:05 已退款¥12.39"
            )
        )
    }

    @Test
    fun `空文本-不抽金额`() {
        assertNull(WechatBillPageHeuristics.extractAmountCents(""))
    }
}
