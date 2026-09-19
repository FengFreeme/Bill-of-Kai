package com.kai.bill.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1 金额闸门的单测。
 *
 * 盯住两件事：
 * 1. 三种合法形态都能抽到（带货币符号 / 带小数点 / 带「元」后缀）；
 * 2. **不是金额的数字绝不误读** —— 卡号尾号、纯整数一旦被当成金额，会直接产生一笔假账。
 */
class AmountGateTest {

    private val gate = AmountGate()

    @Test
    fun `带货币符号-可抽到`() {
        assertEquals(8850L, gate.extract("支付宝 账单提醒 支出 ¥88.50 元"))
    }

    @Test
    fun `无货币符号带小数-可抽到`() {
        // 真机样本：「你收到一笔29.8元退款」
        assertEquals(2980L, gate.extract("你收到一笔29.8元退款"))
    }

    @Test
    fun `只有元后缀-可抽到`() {
        assertEquals(5000L, gate.extract("收款 50元"))
    }

    @Test
    fun `千分位-可抽到`() {
        assertEquals(123456L, gate.extract("支付宝 转账 ¥1,234.56"))
    }

    @Test
    fun `多个金额-取第一个`() {
        assertEquals(1200L, gate.extract("退款 ¥12.00，原支付 ¥25.00"))
    }

    @Test
    fun `卡号尾号不是金额`() {
        // 招行真实通知：金额不在通知里，只有卡号尾号 2836
        assertNull(
            gate.extract("您账户2836于09月13日22:49在【财付通-微信支付-微信转账】发生快捷支付扣款")
        )
    }

    @Test
    fun `纯整数不是金额`() {
        assertNull(gate.extract("您尾号1234的储蓄卡"))
    }

    @Test
    fun `零元不算金额`() {
        // 金额恒为正数，0 元没有记账意义
        assertNull(gate.extract("本次优惠 0.00元"))
    }

    @Test
    fun `不含金额的文本返回null`() {
        assertNull(gate.extract("微信 收到一条消息：在吗"))
        assertNull(gate.extract("今日天气晴"))
    }
}
