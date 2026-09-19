package com.kai.bill.data.parser

import com.kai.bill.data.capture.signal.CategorySignal
import com.kai.bill.data.capture.signal.CategorySignalOrigin
import com.kai.bill.data.presets.DefaultCategories
import com.kai.bill.data.presets.DefaultMatchKeywords
import com.kai.bill.data.presets.PackageCategoryPreset
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3 分类路由在**引入类别信号之后**的行为。
 *
 * 盯住的是四级优先级的**顺序**，以及一条容易被忽略的兼容性要求：
 * 没有信号时必须与引入信号前完全一致 —— 否则这次改动会悄悄改变所有历史通知的分类结果。
 */
class CategoryRouterSignalTest {

    private val router = CategoryRouter()
    private val tables = DefaultMatchKeywords.tables

    private fun signal(
        text: String,
        packageName: String? = null,
        origin: CategorySignalOrigin = CategorySignalOrigin.ACCESSIBILITY,
        capturedAt: Long = 1_000L
    ) = CategorySignal(
        origin = origin,
        packageName = packageName,
        text = text,
        amountCents = null,
        tradeTimeMillis = null,
        capturedAtMillis = capturedAt
    )

    private fun resolve(
        rawText: String,
        direction: BillType = BillType.EXPENSE,
        signals: List<CategorySignal> = emptyList()
    ): CategoryMatch = router.resolve(rawText, direction, SourceType.NOTIFICATION, tables, signals)

    @Test
    fun `通知正文命中时优先于信号文本`() {
        val match = resolve(
            rawText = "支付宝 星巴克 ¥35.00",
            signals = listOf(signal(text = "滴滴打车 ¥18"))
        )

        assertEquals(CategoryMatchOrigin.NOTIFICATION_TEXT, match.origin)
        assertEquals(24L, match.categoryId)
    }

    @Test
    fun `通知正文无线索时用信号文本定分类`() {
        val match = resolve(
            rawText = "支付宝 支付成功 ¥28.30",
            signals = listOf(signal(text = "美团外卖 已支付 ¥28.30", packageName = "com.sankuai.meituan"))
        )

        assertEquals(CategoryMatchOrigin.SIGNAL_TEXT, match.origin)
        assertEquals(21L, match.categoryId)
    }

    @Test
    fun `信号文本无线索时用包名默认分类`() {
        val match = resolve(
            rawText = "支付宝 支付成功 ¥18.00",
            signals = listOf(signal(text = "支付成功", packageName = "com.luckin.coffee"))
        )

        assertEquals(CategoryMatchOrigin.SIGNAL_PACKAGE, match.origin)
        assertEquals(24L, match.categoryId)
    }

    @Test
    fun `包名默认分类不参与收入方向`() {
        // 咖啡 App 不可能产生收入账单；方向是收入说明前面判错了，
        // 此时再配一个支出分类只会把错误固化，正确的做法是落收入兜底等用户修正
        val match = resolve(
            rawText = "收款 ￥100.00",
            direction = BillType.INCOME,
            signals = listOf(signal(text = "收款", packageName = "com.luckin.coffee"))
        )

        assertEquals(CategoryMatchOrigin.FALLBACK, match.origin)
        assertEquals(18L, match.categoryId)
    }

    @Test
    fun `多条信号时取排序靠前的那条`() {
        // 调用方（CategorySignalStore.recent）保证已按可信度排序：无障碍优先于截图。
        // 路由只信任传入顺序，不自行重排 —— 排序口径只有一处实现。
        val match = resolve(
            rawText = "支付宝 支付成功 ¥28.30",
            signals = listOf(
                signal(
                    text = "美团外卖 ¥28.30",
                    origin = CategorySignalOrigin.ACCESSIBILITY,
                    capturedAt = 2_000L
                ),
                signal(
                    text = "滴滴打车 ¥28.30",
                    origin = CategorySignalOrigin.SCREENSHOT,
                    capturedAt = 3_000L
                )
            )
        )

        assertEquals(21L, match.categoryId)
    }

    @Test
    fun `无信号时与引入信号前的行为完全一致`() {
        val match = resolve(rawText = "支付宝 支付成功 ¥88.50")

        assertEquals(CategoryMatchOrigin.FALLBACK, match.origin)
        assertEquals(12L, match.categoryId)
    }

    @Test
    fun `包名无映射时不参与分类`() {
        val match = resolve(
            rawText = "支付宝 支付成功 ¥18.00",
            signals = listOf(signal(text = "支付成功", packageName = "com.unknown.app"))
        )

        assertEquals(CategoryMatchOrigin.FALLBACK, match.origin)
    }

    @Test
    fun `包名预设引用的分类 id 必须真实存在`() {
        // 写错一个 id 就会让账单落进语义不符的分类，且不会报错、只会在统计里慢慢显形
        val validIds = DefaultCategories.all.map { it.id }.toSet()

        PackageCategoryPreset.mappedCategoryIds.forEach { id ->
            assertTrue("包名预设引用了不存在的分类 id=$id", id in validIds)
        }
    }
}
