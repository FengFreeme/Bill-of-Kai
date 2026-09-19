package com.kai.bill.data.parser

import com.kai.bill.data.presets.DefaultMatchKeywords
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S3 分类路由的单测（**通知正文**一级）。
 *
 * 用例来自旧 `CategoryKeywordMatcherTest`（原 8 个用例）—— 词表迁移不等于可以丢掉覆盖，
 * 这些真机样本必须继续钉住分类 id，尤其是曾经出过错的两处：
 * 「退款」被错记成支出、「报销」被并进退款。
 *
 * 引入类别信号后的**四级优先级、包名默认分类**见 [CategoryRouterSignalTest]。
 */
class CategoryRouterTest {

    private val router = CategoryRouter()
    private val tables = DefaultMatchKeywords.tables

    /** 只关心落到哪个分类；来源判定另有用例 */
    private fun categoryId(text: String, direction: BillType): Long =
        router.resolve(text, direction, SourceType.NOTIFICATION, tables).categoryId

    @Test
    fun `品牌词-星巴克-映射到咖啡奶茶`() {
        assertEquals(24L, categoryId("在星巴克点了杯拿铁 ¥35", BillType.EXPENSE))
    }

    // —— 转账：转入(96) / 转出(97)，挂在 19 转账 之下 ——

    @Test
    fun `微信-对方已收款-落转出`() {
        // 「周建鑫已收款」= 我转出去、对方收到了 → 转出(97)。
        // 之前方向被判成收入，分类一路落到 18 其他收入（真机复现）。
        val text = "更多信息 周建鑫已收款¥200.00 账单详情 周建鑫已收款 ¥200.00 " +
            "转账时间 2026年09月10日 22:22:08 收款时间 2026年09月10日 22:22:44"
        assertEquals(97L, categoryId(text, BillType.TRANSFER))
    }

    @Test
    fun `微信-你已收款存零钱-落转入`() {
        // 收款方视角：钱进来了 → 转入(96)。
        // 「资金已存入零钱」比「已收款」长，同级比长度时优先生效
        val text = "你已收款，资金已存入零钱¥100.00 账单详情 你已收款，资金已存入零钱 ¥100.00 " +
            "零钱余额 转账时间 2026年09月10日 17:24:47 收款时间 2026年09月10日 17:24:55"
        assertEquals(96L, categoryId(text, BillType.TRANSFER))
    }

    @Test
    fun `打车-滴滴-映射到打车`() {
        assertEquals(27L, categoryId("滴滴打车 ¥18.5", BillType.EXPENSE))
    }

    @Test
    fun `加油-中石化-映射到加油`() {
        assertEquals(28L, categoryId("中石化加油 ¥300", BillType.EXPENSE))
    }

    @Test
    fun `口语化-搓了一顿火锅-映射到早午晚餐`() {
        assertEquals(21L, categoryId("今天搓了一顿火锅", BillType.EXPENSE))
    }

    @Test
    fun `退款成功-映射到退款`() {
        assertEquals(94L, categoryId("退款成功 ￥12.00 已退回", BillType.INCOME))
    }

    @Test
    fun `报销-映射到报销而非退款`() {
        // 修正项：旧实现把「报销」并进了 94 退款
        assertEquals(93L, categoryId("报销到账 ￥200.00", BillType.INCOME))
    }

    @Test
    fun `微信红包-映射到红包`() {
        assertEquals(90L, categoryId("微信红包 你收到一个红包", BillType.INCOME))
    }

    @Test
    fun `收款到账-映射到其他收入`() {
        assertEquals(18L, categoryId("收款到账 ￥0.50 已存入余额", BillType.INCOME))
    }

    @Test
    fun `发工资了-映射到工资`() {
        assertEquals(13L, categoryId("发工资了 到账 ￥8000", BillType.INCOME))
    }

    @Test
    fun `方向不符时不硬猜-落该方向兜底分类`() {
        // 分类词只在已定方向内匹配，避免「同一个商户词在两个方向下都命中」。
        // 方向不符时不能借用另一个方向的词，而是明确落到用户可修正的兜底位。
        val match = router.resolve(
            "在星巴克点了杯拿铁 ¥35",
            BillType.INCOME,
            SourceType.NOTIFICATION,
            tables
        )

        assertEquals(CategoryMatchOrigin.FALLBACK, match.origin)
        assertEquals(18L, match.categoryId)
    }

    @Test
    fun `命中时标记来源与命中词`() {
        val match = router.resolve("星巴克 ¥35", BillType.EXPENSE, SourceType.NOTIFICATION, tables)

        assertEquals(CategoryMatchOrigin.NOTIFICATION_TEXT, match.origin)
        assertEquals("星巴克", match.matchedKeyword)
        assertEquals(false, match.isFallback)
    }

    @Test
    fun `未命中回落方向兜底分类`() {
        assertEquals(12L, router.fallbackOf(BillType.EXPENSE))
        assertEquals(18L, router.fallbackOf(BillType.INCOME))
        assertEquals(20L, router.fallbackOf(BillType.TRANSFER))

        assertEquals(12L, categoryId("支付宝 支付成功 ¥88.50", BillType.EXPENSE))
    }

    @Test
    fun `兜底分类集合锁定为其他-其他收入-还款`() {
        // 回填允许修改的范围全项目只依赖这一个集合，钉住它防止被无意放宽
        assertEquals(setOf(12L, 18L, 20L), router.fallbackIds)
        assertEquals(true, router.isFallback(12L))
        assertEquals(false, router.isFallback(24L))
    }
}
