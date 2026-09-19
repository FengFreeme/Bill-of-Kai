package com.kai.bill.data.capture.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 页面文本归一化的单测。
 *
 * 节点遍历本身依赖 Android 类型、只能真机验证；但「去重 / 压缩 / 截断」这三条
 * 会直接影响分类匹配结果（例如重复文本把关键词挤出截断窗口），必须能脱离设备钉住。
 */
class PageTextExtractorTest {

    @Test
    fun `没有有效文本时返回null`() {
        assertNull(PageTextExtractor.normalizeText(emptyList()))
        assertNull(PageTextExtractor.normalizeText(listOf("", "   ", "\n")))
    }

    @Test
    fun `去重且保持出现顺序`() {
        assertEquals(
            "美团 外卖 已支付",
            PageTextExtractor.normalizeText(listOf("美团", "外卖", "美团", "已支付"))
        )
    }

    @Test
    fun `去掉首尾空白并压缩连续空白`() {
        assertEquals(
            "美团 外卖 ¥28.30",
            PageTextExtractor.normalizeText(listOf("  美团  ", "外卖\n", "  ¥28.30 "))
        )
    }

    @Test
    fun `超长文本被截断到上限`() {
        val tooLong = "字".repeat(PageTextExtractor.MAX_CHARS + 500)

        val normalized = PageTextExtractor.normalizeText(listOf(tooLong))

        assertEquals(PageTextExtractor.MAX_CHARS, normalized?.length)
    }
}
