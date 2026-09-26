package com.kai.bill.data.capture.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 支付宝详情页判定与金额结构抽取 —— 对照真机截图版式。
 */
class AlipayBillPageHeuristicsTest {

    // ---------------- 页型 ----------------

    @Test
    fun `账单详情加全部账单-是详情页`() {
        assertTrue(
            AlipayBillPageHeuristics.isBillDetail(
                "全部账单 账单详情 滴滴出行 -55.00 交易成功 账单分类 交通出行 账单管理"
            )
        )
    }

    @Test
    fun `账单详情加账单管理-是详情页`() {
        // 无「全部账单」时，「账单管理」可作配套
        assertTrue(
            AlipayBillPageHeuristics.isBillDetail(
                "账单详情 -11.80 交易成功 账单分类 日用百货 账单管理"
            )
        )
    }

    @Test
    fun `仅有全部账单加账单管理无账单详情-不是详情页`() {
        // 「账单详情」是必须项；不能只靠全部账单+账单管理
        assertFalse(
            AlipayBillPageHeuristics.isBillDetail("全部账单 -11.80 交易成功 账单管理")
        )
    }

    @Test
    fun `仅有账单详情无配套-不是详情页`() {
        assertFalse(
            AlipayBillPageHeuristics.isBillDetail("账单详情 -55.00 交易成功")
        )
    }

    @Test
    fun `有账单分类但无详情组合-不是详情页`() {
        // 「账单分类」是详情字段，绝不能当否定；但也绝不能单独当正向
        assertFalse(
            AlipayBillPageHeuristics.isBillDetail("账单分类 餐饮美食 月支出 100.00")
        )
    }

    // ---------------- 金额：六张截图形态 ----------------

    @Test
    fun `支出-交易成功-抽到金额`() {
        // 图1 / 图2 / 图5
        assertEquals(
            5500L,
            AlipayBillPageHeuristics.extractAmountCents(
                "返回 全部账单 账单详情 滴滴出行 -55.00 交易成功 " +
                    "账单分类 交通出行 支付时间 2026-09-19 15:41:05 账单管理"
            )
        )
        assertEquals(
            5584L,
            AlipayBillPageHeuristics.extractAmountCents(
                "返回 全部账单 账单详情 小九烧烤外卖订单 -55.84 交易成功 " +
                    "账单分类 餐饮美食 支付时间 2026-09-19 15:41:05 更多 账单管理"
            )
        )
        assertEquals(
            1180L,
            AlipayBillPageHeuristics.extractAmountCents(
                "返回 全部账单 账单详情 凯 -11.80 交易成功 账单分类 日用百货 账单管理"
            )
        )
    }

    @Test
    fun `退款-交易成功-抽到金额`() {
        // 图3 / 图6：退款但状态仍是「交易成功」
        assertEquals(
            1680L,
            AlipayBillPageHeuristics.extractAmountCents(
                "返回 全部账单 账单详情 退款-美团 +16.80 交易成功 " +
                    "对方账户 美团 账单分类 退款 支付时间 2026-09-19 12:00:00 账单管理"
            )
        )
        assertEquals(
            1280L,
            AlipayBillPageHeuristics.extractAmountCents(
                "返回 全部账单 账单详情 退款-美团 +12.80 交易成功 对方账户 美团 账单管理"
            )
        )
    }

    @Test
    fun `退款成功-抽到金额`() {
        // 图4：状态是「退款成功」，不能只认「交易成功」
        assertEquals(
            1239L,
            AlipayBillPageHeuristics.extractAmountCents(
                "返回 全部账单 账单详情 退款-商家 +12.39 退款成功 " +
                    "对方账户 得力按动笔芯 账单分类 退款 支付时间 2026-09-19 13:55:00 账单管理"
            )
        )
    }

    @Test
    fun `带元后缀也能抽`() {
        assertEquals(
            5584L,
            AlipayBillPageHeuristics.extractAmountCents(
                "全部账单 账单详情 55.84元 交易成功 账单管理"
            )
        )
    }

    @Test
    fun `无状态行-不抽金额`() {
        assertNull(
            AlipayBillPageHeuristics.extractAmountCents(
                "全部账单 账单详情 滴滴出行 -55.00 账单分类 交通出行"
            )
        )
    }

    @Test
    fun `空文本-不抽金额`() {
        assertNull(AlipayBillPageHeuristics.extractAmountCents(""))
    }
}
