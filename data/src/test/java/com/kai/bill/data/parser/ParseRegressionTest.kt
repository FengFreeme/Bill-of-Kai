package com.kai.bill.data.parser

import com.kai.bill.data.presets.DefaultMatchKeywords
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.PendingReason
import com.kai.bill.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知解析回归测试（纯 JVM，可在 CI / 本地直接运行，无需真机）。
 *
 * 用例全部沿用 M4 阶段的真机通知样本，断言从「旧规则的命中 + 金额」改写为新链路的
 * 「金额 + 走向（方向 / 待确认原因 / 排除）」：规则从整包正则换成词表后，这些样本
 * 必须仍然落到同一笔账上 —— 这就是「旧规则退休不等于放弃覆盖」的那道基线。
 *
 * 三处刻意保留的行为差异（都是本次重构的目标，不是回归）：
 * - `支付宝 转账 ¥1,234.56` 现在归**转账**（不计统计），旧实现记成支出；
 * - `您尾号…入账人民币100.00元` 现在进**待确认**（无法区分真实收入与账户互转），
 *   旧实现按「银行通用入账」直接记成支出；
 * - `你收到一笔转账` 现在归**转账·转入**（不计统计），旧实现记成收入 ——
 *   个人转账不是收入，见下方 `微信-收到一笔转账-判转账且不计统计`。
 */
class ParseRegressionTest {

    private val amountGate = AmountGate()
    private val directionRouter = DirectionRouter()
    private val tables = DefaultMatchKeywords.tables

    private fun route(text: String) = directionRouter.route(text, SourceType.NOTIFICATION, tables)

    // —— 支付宝 ——

    @Test
    fun `支付宝-带元字能命中并归一化`() {
        val text = "支付宝 账单提醒 支出 ¥88.50 元"
        assertEquals(8850L, amountGate.extract(text))
        assertEquals(MatchRoute.EXPENSE, route(text).route)
    }

    @Test
    fun `支付宝-无元字也能命中（真机扫码到账样本）`() {
        val text = "支付宝 收款 ¥12.00"
        assertEquals(1200L, amountGate.extract(text))
        assertEquals(MatchRoute.INCOME, route(text).route)
    }

    @Test
    fun `支付宝-千分位金额`() {
        assertEquals(123456L, amountGate.extract("支付宝 转账 ¥1,234.56"))
    }

    @Test
    fun `支付宝-转账归转账且不计统计`() {
        // 「转账」只在分类词表里（分类 19 转账），方向由分类词兜底得出
        val hit = route("支付宝 转账 ¥1,234.56")
        assertEquals(MatchRoute.TRANSFER, hit.route)
        assertEquals(BillType.TRANSFER, hit.type)
        assertTrue(!hit.countInStats)
    }

    @Test
    fun `支付宝-交易提醒-支出通知无支付宝关键字`() {
        // 通知正文里没有「支付宝」三字，旧实现必须靠独立规则覆盖
        val text = "交易提醒 你有一笔0.50元的支出，点此查看详情。"
        assertEquals(50L, amountGate.extract(text))
        assertEquals(MatchRoute.EXPENSE, route(text).route)
    }

    @Test
    fun `支付宝-收款到账`() {
        val text = "收款到账 ￥0.50 已存入余额"
        assertEquals(50L, amountGate.extract(text))
        // 「收款到账」必须压过泛化的「到账 / 存入」，否则会掉进待确认
        assertEquals(MatchRoute.INCOME, route(text).route)
    }

    @Test
    fun `支付宝-退款成功`() {
        val text = "退款成功 ￥12.00 已退回原账户"
        assertEquals(1200L, amountGate.extract(text))
        assertEquals(MatchRoute.INCOME, route(text).route)
    }

    @Test
    fun `支付宝-收到退款-金额在退款前`() {
        // 金额在动作词**之前**，所以消歧只能靠距离、不能靠先后
        val text = "你收到一笔29.8元退款"
        assertEquals(2980L, amountGate.extract(text))
        assertEquals(MatchRoute.INCOME, route(text).route)
    }

    @Test
    fun `支付宝-余额宝收益`() {
        val text = "余额宝 收益发放 ¥3.21"
        assertEquals(321L, amountGate.extract(text))
        assertEquals(MatchRoute.INCOME, route(text).route)
    }

    @Test
    fun `支付宝-转出成功-进待确认`() {
        // 自己的钱搬家：方向可能是转账、也可能什么都不是，交人工确认
        val text = "转出成功 ¥0.80 到账账户 支付宝余额"
        assertEquals(80L, amountGate.extract(text))
        val hit = route(text)
        assertEquals(PendingReason.SELF_TRANSFER, hit.pendingReason)
        assertTrue(hit.isPending)
    }

    // —— 微信 ——

    @Test
    fun `微信支付-能命中`() {
        val text = "微信支付 支付成功 ¥0.01"
        assertEquals(1L, amountGate.extract(text))
        assertEquals(MatchRoute.EXPENSE, route(text).route)
    }

    @Test
    fun `微信红包-进待确认`() {
        val text = "微信红包 你领取了XX的红包，金额 ¥5.20"
        assertEquals(520L, amountGate.extract(text))
        assertEquals(PendingReason.GIFT, route(text).pendingReason)
    }

    @Test
    fun `微信-收到一笔转账-判转账且不计统计`() {
        // ⚠️ 行为变更（2026-09-20）：旧实现判为**收入**并计入统计。
        // 个人转账不是收入 —— 与「转账 / 还款」同一口径：直接落库、不计统计，
        // 分类落到「转入(97)」。用户确实想算收入，在编辑页打开「计入统计」即可。
        val text = "微信 你收到一笔转账 ¥88.00"
        assertEquals(8800L, amountGate.extract(text))
        val hit = route(text)
        assertEquals(MatchRoute.TRANSFER, hit.route)
        assertEquals(BillType.TRANSFER, hit.type)
        assertTrue(!hit.countInStats)
    }

    // —— 银行 ——

    @Test
    fun `招商银行-快捷支付扣款`() {
        val text = "您账户2836于09月13日22:49在【财付通-微信支付-微信转账】发生快捷支付扣款，人民币0.10元"
        assertEquals(10L, amountGate.extract(text))
        assertEquals(MatchRoute.EXPENSE, route(text).route)
    }

    @Test
    fun `银行-您账户-消费支出`() {
        val text = "您账户****1234于09月13日发生消费支出人民币88.50元"
        assertEquals(8850L, amountGate.extract(text))
        assertEquals(MatchRoute.EXPENSE, route(text).route)
    }

    @Test
    fun `银行-您尾号-POS消费`() {
        val text = "您尾号1234的储蓄卡9月13日POS消费支出人民币88.50元"
        assertEquals(8850L, amountGate.extract(text))
        assertEquals(MatchRoute.EXPENSE, route(text).route)
    }

    @Test
    fun `银行-您尾号-入账降级为待确认`() {
        val text = "您尾号5678账户09月13日入账人民币100.00元"
        assertEquals(10000L, amountGate.extract(text))
        assertEquals(PendingReason.GENERIC_INBOUND, route(text).pendingReason)
    }

    @Test
    fun `银行-通知无金额-不误读卡号尾号2836`() {
        // 招行真实通知栏文本（金额只在 App 详情页，不在通知里）
        val text = "您账户2836于09月13日22:49在【财付通-微信支付-微信转账】发生快捷支付扣款"
        assertNull(amountGate.extract(text))
    }

    @Test
    fun `无关通知不命中`() {
        assertNull(amountGate.extract("微信 收到一条消息：在吗"))
        assertNull(amountGate.extract("今日天气晴"))
    }

    // —— 金额归一化 / 去重键（原样保留）——

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
    fun `DedupKey-同额同类型同秒-键稳定且不同金额不同`() {
        val a = DedupKey.build(1200, ALIGNED_T, BillType.EXPENSE, null)
        val b = DedupKey.build(1200, ALIGNED_T, BillType.EXPENSE, null)
        val c = DedupKey.build(1300, ALIGNED_T, BillType.EXPENSE, null)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `DedupKey-类型参与身份-同秒同额的支出与收入不互相顶掉`() {
        // 类型进了键，所以「支出 ¥10」与「收入 ¥10」落在同一时刻也各记一笔
        assertNotEquals(
            DedupKey.build(1000, ALIGNED_T, BillType.EXPENSE, null),
            DedupKey.build(1000, ALIGNED_T, BillType.INCOME, null)
        )
    }

    @Test
    fun `DedupKey-同额同类型但间隔超过1秒-各记一笔`() {
        // 两笔 ¥10 相隔 30 秒：键必须不同，否则第二笔会被静默丢掉
        assertNotEquals(
            DedupKey.build(1000, ALIGNED_T, BillType.EXPENSE, null),
            DedupKey.build(1000, ALIGNED_T + 30_000L, BillType.EXPENSE, null)
        )
    }

    @Test
    fun `DedupKey-一秒内的重复投递合并（不看原文）`() {
        // 同一笔被投递两次、postTime 抖了 400ms → 同一秒桶 → 判为同一笔。
        // 原文写什么都不影响：文案微变不再多记一笔（这正是去掉原文指纹的目的）。
        //
        // 已知代价：同一秒内两笔「金额 / 类型 / 商户」全同的真实消费与此完全同形，
        // 也会被判成同一笔 —— 两者在数据上无法区分，见 DedupKey 的取舍说明。
        assertEquals(
            DedupKey.build(1000, ALIGNED_T, BillType.EXPENSE, null),
            DedupKey.build(1000, ALIGNED_T + 400L, BillType.EXPENSE, null)
        )
    }

    @Test
    fun `DedupKey-跨出秒桶边界就不再合并-已知取舍`() {
        // 刻意的行为：超过桶宽一律视为两笔。真机上若发现「同一笔被延迟几秒重发」多记，
        // 调 DedupKey_TIME_BUCKET_MILLIS，而不是回退到看原文
        assertNotEquals(
            DedupKey.build(1000, ALIGNED_T, BillType.EXPENSE, null),
            DedupKey.build(1000, ALIGNED_T + DedupKey.TIME_BUCKET_MILLIS, BillType.EXPENSE, null)
        )
    }

    private companion object {

        /**
         * 对齐到整秒的时间戳。
         *
         * 去重键按秒分桶，用例必须落在**同一个秒桶**内，「±400ms 仍算同一笔」这类断言才成立；
         * 随便取一个毫秒值很可能刚好跨界，让测试变得看运气。
         */
        val ALIGNED_T: Long = 1_700_000_000_000L / 1_000L * 1_000L
    }
}
