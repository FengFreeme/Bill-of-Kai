package com.kai.bill.data.parser

import com.kai.bill.domain.model.BillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 关键词 → 分类映射的单测（纯 JVM）。
 *
 * 覆盖品牌词、场景词、口语化变体，以及收支类型修正（收入类被错记成支出的回归）。
 */
class CategoryKeywordMatcherTest {

    @Test
    fun `品牌词-星巴克-映射到咖啡奶茶-支出`() {
        assertEquals(24L to BillType.EXPENSE, CategoryKeywordMatcher.match("在星巴克点了杯拿铁 ¥35"))
    }

    @Test
    fun `打车类-滴滴-映射到打车-支出`() {
        assertEquals(27L to BillType.EXPENSE, CategoryKeywordMatcher.match("滴滴打车 ¥18.5"))
    }

    @Test
    fun `加油-中石化-映射到加油-支出`() {
        assertEquals(28L to BillType.EXPENSE, CategoryKeywordMatcher.match("中石化加油 ¥300"))
    }

    @Test
    fun `口语化-搓了一顿火锅-映射到早午晚餐-支出`() {
        assertEquals(21L to BillType.EXPENSE, CategoryKeywordMatcher.match("今天搓了一顿火锅"))
    }

    @Test
    fun `收入-退款成功-映射到退款-收入`() {
        // 修正「收入被错记成支出」：退款通知应记 INCOME + 退款分类
        assertEquals(94L to BillType.INCOME, CategoryKeywordMatcher.match("退款成功 ￥12.00 已退回"))
    }

    @Test
    fun `收入-微信红包-映射到微信红包-收入`() {
        assertEquals(90L to BillType.INCOME, CategoryKeywordMatcher.match("微信红包 你收到一个红包"))
    }

    @Test
    fun `收入-收款到账-映射到其他收入-收入`() {
        assertEquals(18L to BillType.INCOME, CategoryKeywordMatcher.match("收款到账 ￥0.50 已存入余额"))
    }

    @Test
    fun `收入-发工资了-映射到工资-收入`() {
        assertEquals(13L to BillType.INCOME, CategoryKeywordMatcher.match("发工资了 到账 ￥8000"))
    }

    @Test
    fun `无关键词-返回null-回退规则默认`() {
        assertNull(CategoryKeywordMatcher.match("支付宝 账单提醒 支出 ¥88.50 元"))
        assertNull(CategoryKeywordMatcher.match("今天天气晴"))
    }
}
