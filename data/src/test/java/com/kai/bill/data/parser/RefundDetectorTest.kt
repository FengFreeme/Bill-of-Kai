package com.kai.bill.data.parser

import com.kai.bill.domain.model.RefundCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 退款识别的单测。
 *
 * 判错的代价不对称：**把消费判成退款**会去冲抵另一笔支出的分类与月份（记错账），
 * 把退款漏判成收入只是数字偏大（还记得住）。因此这里的重点是否定用例：
 * 「报销 / 理赔」「正文里出现退款字样的消费」都必须**不**被判成退款。
 */
class RefundDetectorTest {

    @Test
    fun `微信退款页-命中方向词`() {
        assertTrue(
            RefundDetector.isRefund(
                rawText = "全部账单 退款-商家 +12.39 当前状态 已退款 退款时间 2026年9月19日 13:55:00",
                directionKeyword = "退款成功",
                categoryId = RefundCategory.ID
            )
        )
    }

    @Test
    fun `支付宝退款通知-命中方向词`() {
        assertTrue(
            RefundDetector.isRefund(
                rawText = "退款成功 ￥12.00 已退回原账户",
                directionKeyword = "退款成功",
                categoryId = 12L
            )
        )
    }

    @Test
    fun `分类落到退款-即使方向词不是退款类`() {
        assertTrue(
            RefundDetector.isRefund(
                rawText = "退货 ¥9.90 已受理",
                directionKeyword = null,
                categoryId = RefundCategory.ID
            )
        )
    }

    @Test
    fun `报销到账-不是退款`() {
        assertFalse(
            RefundDetector.isRefund(
                rawText = "报销到账 ￥200.00",
                directionKeyword = "报销到账",
                categoryId = 93L
            )
        )
    }

    @Test
    fun `理赔到账-不是退款`() {
        assertFalse(
            RefundDetector.isRefund(
                rawText = "理赔款已到账 ￥1,200.00",
                directionKeyword = "理赔",
                categoryId = 93L
            )
        )
    }

    @Test
    fun `收款到账-不是退款`() {
        assertFalse(
            RefundDetector.isRefund(
                rawText = "收款到账 ￥58.00",
                directionKeyword = "收款到账",
                categoryId = 18L
            )
        )
    }
}
