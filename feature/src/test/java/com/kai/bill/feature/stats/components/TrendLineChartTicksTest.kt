package com.kai.bill.feature.stats.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 折线图 x 轴刻度取点的单测。
 *
 * 钉住的是真机上报过的一个视觉缺陷：原来按「每 step 个取一个 + 末尾补一个」取点，
 * 末尾那个常紧贴前一个刻度（26 天数据取到 24 与 25），两个日期标签叠字。
 */
class TrendLineChartTicksTest {

    @Test
    fun `26天数据-刻度均分且不会挤在一起`() {
        val ticks = axisTickIndices(count = 26, maxTicks = 6)

        assertEquals(listOf(0, 5, 10, 15, 20, 25), ticks)
        // 相邻刻度至少差 5 个索引 ≈ 20% 图宽，9sp 的「9/26」放得下
        ticks.zipWithNext().forEach { (a, b) -> assertTrue("刻度 $a 与 $b 太近", b - a >= 5) }
    }

    @Test
    fun `末尾点与起点一定被标注`() {
        listOf(7, 12, 26, 30, 31).forEach { count ->
            val ticks = axisTickIndices(count = count, maxTicks = 6)

            assertEquals("起点未标注（count=$count）", 0, ticks.first())
            assertEquals("末尾未标注（count=$count）", count - 1, ticks.last())
        }
    }

    @Test
    fun `刻度数量不超过上限`() {
        (1..40).forEach { count ->
            assertTrue(
                "count=$count 取出了 ${axisTickIndices(count, 6).size} 个刻度",
                axisTickIndices(count, 6).size <= 6
            )
        }
    }

    @Test
    fun `点数不足上限时全部画出`() {
        assertEquals(listOf(0, 1, 2, 3), axisTickIndices(count = 4, maxTicks = 6))
    }

    @Test
    fun `单点与空数据不会崩`() {
        assertEquals(listOf(0), axisTickIndices(count = 1, maxTicks = 6))
        assertEquals(emptyList<Int>(), axisTickIndices(count = 0, maxTicks = 6))
    }
}
