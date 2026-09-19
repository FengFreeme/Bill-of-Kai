package com.kai.bill.data.ingest

import com.kai.bill.data.capture.signal.CategorySignal
import com.kai.bill.data.capture.signal.CategorySignalOrigin
import com.kai.bill.data.capture.signal.CategorySignalStore
import com.kai.bill.data.notify.BillPendingNotifier
import com.kai.bill.data.notify.BillSavedNotifier
import com.kai.bill.data.parser.AccountRouter
import com.kai.bill.data.parser.AmountGate
import com.kai.bill.data.parser.CategoryRouter
import com.kai.bill.data.parser.DedupKey
import com.kai.bill.data.parser.DirectionRouter
import com.kai.bill.data.presets.DefaultMatchKeywords
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.CaptureResult
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.model.PendingBill
import com.kai.bill.domain.model.PendingReason
import com.kai.bill.domain.model.SourceType
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.PendingBillRepository
import com.kai.bill.domain.time.FixedClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 四级流水线的端到端测试（纯 JVM，用假仓储与假通知器）。
 *
 * 盯住的是「落不落库、计不计统计、进不进待确认、发不发通知」这四件事 ——
 * 它们直接决定统计数字对不对，而统计数字错了用户是发现不了的（不像漏记还能补）。
 */
class IngestPipelineTest {

    private val now = 1_700_000_000_000L
    private val billRepository = FakeBillRepository()
    private val pendingRepository = FakePendingBillRepository()
    private val notifier = RecordingNotifier()
    private val pendingNotifier = RecordingPendingNotifier()

    /** 与流水线共用同一个信号窗口，便于「先投信号、再跑通知」 */
    private val signalStore = CategorySignalStore(FixedClock(now))

    private val pipeline = IngestPipeline(
        keywords = DefaultMatchKeywords.tables,
        amountGate = AmountGate(),
        directionRouter = DirectionRouter(),
        categoryRouter = CategoryRouter(),
        signalStore = signalStore,
        accountRouter = AccountRouter(),
        billRepository = billRepository,
        pendingBillRepository = pendingRepository,
        clock = FixedClock(now),
        billSavedNotifier = notifier,
        billPendingNotifier = pendingNotifier,
        events = IngestEvents()
    )

    private fun run(text: String, packageName: String? = null): CaptureResult =
        runAt(text, now, packageName)

    /** 指定交易时间的变体：用于验证时间窗去重（跨秒格重发 / 超窗各记一笔） */
    private fun runAt(text: String, timeMillis: Long, packageName: String? = null): CaptureResult =
        runBlocking { pipeline.run(text, SourceType.NOTIFICATION, packageName, timeMillis) }

    @Test
    fun `支出-落库并计入统计-账户按来源判定`() {
        val result = run("支付宝 支付成功 ¥88.50", "com.eg.android.AlipayGphone")

        assertEquals(CaptureResult.PARSED_AND_SAVED, result)
        val bill = billRepository.saved.single()
        assertEquals(8850L, bill.amountCents)
        assertEquals(BillType.EXPENSE, bill.type)
        assertTrue(bill.countInStats)
        assertEquals(12L, bill.categoryId)   // 未命中商户词 → 兜底「其他」
        assertEquals(2L, bill.accountId)     // 支付宝
        assertEquals(now, bill.tradeTimeMillis)
        assertEquals(1, notifier.saved.size)
        assertTrue(pendingRepository.saved.isEmpty())
    }

    @Test
    fun `商户词命中-落到具体分类`() {
        run("支付宝 星巴克 ¥35.00", "com.eg.android.AlipayGphone")
        assertEquals(24L, billRepository.saved.single().categoryId)
    }

    @Test
    fun `还款-落转账但不计入统计`() {
        val result = run("信用卡还款 ¥5,000.00", "cmb.pb")

        assertEquals(CaptureResult.PARSED_AND_SAVED, result)
        val bill = billRepository.saved.single()
        assertEquals(BillType.TRANSFER, bill.type)
        assertFalse(bill.countInStats)
        assertEquals(20L, bill.categoryId)   // 兜底「还款」
        assertEquals(4L, bill.accountId)     // 储蓄卡
    }

    @Test
    fun `支付宝-营销提醒-不落账单但保留在待确认`() {
        // 「你有淘宝闪购4元红包今晚失效」是一条**券到期提醒**，不是任何一笔钱进出。
        // 它会被方向词「红包」命中而走待确认分支 —— 这个结果是**刻意保留**的：
        //
        // - **不落账单**：统计与预算都不会被它污染（底线，也是本用例的主要断言）；
        // - **但也不静默丢弃**：金额规则确实从「4元」抽出了金额，机器没有依据断定它一定是营销，
        //   宁可多问一句让用户自己划掉，也好过把一条可能真实的信息悄悄丢掉。
        //   而且打扰很轻 —— 它带建议值（不是「判不出方向」），所以**不弹悬浮横幅**，
        //   只进待确认列表与角标。
        //
        // 代价是列表里偶尔要多划掉一条营销条目。这是明确的取舍，不是漏判：
        // 反过来用「元红包」这类宽泛的排除词去拦，会把「收到4元红包」这种真实收入一起丢掉。
        val result = run("你有淘宝闪购4元红包今晚失效", "com.eg.android.AlipayGphone")

        assertEquals(CaptureResult.NEEDS_REVIEW, result)
        assertTrue("营销提醒绝不能落成账单", billRepository.saved.isEmpty())

        val pending = pendingRepository.saved.single()
        assertEquals(400L, pending.amountCents)
        assertEquals(BillType.INCOME, pending.suggestedType)
        assertEquals(PendingReason.GIFT, pending.reason)
        assertEquals("红包", pending.matchedKeyword)
    }

    @Test
    fun `自己的钱搬家-两侧都不进统计-且进待确认`() {
        // 用户明确要求的边界：一笔自己的钱在两个账户之间搬家，不能凭空多出支出或收入。
        // 两条用不同金额：去重键是「金额 + 秒级时间桶 + 类型 + 商户」（见 DedupKey），
        // 用例里两条通知的时间戳相同，只有金额不同才保证是两个独立的键。
        val out = run("转出成功 ¥100.00 到账账户 支付宝余额", "com.eg.android.AlipayGphone")
        val inbound = run("您尾号1234账户入账人民币120.00元", "cmb.pb")

        assertEquals(CaptureResult.NEEDS_REVIEW, out)
        assertEquals(CaptureResult.NEEDS_REVIEW, inbound)
        assertTrue(billRepository.saved.isEmpty())
        assertTrue(notifier.saved.isEmpty())

        // 建议值：方向为转账、不计统计、分类建议「转账」（而不是还款）
        val outPending = pendingRepository.saved.first()
        assertEquals(BillType.TRANSFER, outPending.suggestedType)
        assertFalse(outPending.suggestedCountInStats)
        assertEquals(PendingReason.SELF_TRANSFER, outPending.reason)
        assertEquals("转出成功", outPending.matchedKeyword)
        assertEquals(19L, outPending.suggestedCategoryId)
        assertEquals(2L, outPending.suggestedAccountId)
        assertTrue(outPending.hasSuggestion)

        // 弱档不打扰：有明确建议值的待确认只进列表与角标，不弹悬浮横幅
        assertTrue(pendingNotifier.alerts.isEmpty())

        // 泛化入账：建议按收入落账但**不计统计**，这样即使用户直接接受也不会虚增本月收入
        val inboundPending = pendingRepository.saved.last()
        assertEquals(BillType.INCOME, inboundPending.suggestedType)
        assertFalse(inboundPending.suggestedCountInStats)
        assertEquals(PendingReason.GENERIC_INBOUND, inboundPending.reason)
        assertEquals(18L, inboundPending.suggestedCategoryId)   // 兜底「其他收入」
        assertEquals(4L, inboundPending.suggestedAccountId)     // 储蓄卡
    }

    @Test
    fun `判不出方向-也进待确认且没有方向建议`() {
        val result = run("您尾号1234账户人民币88.50元", "cmb.pb")

        assertEquals(CaptureResult.NEEDS_REVIEW, result)
        val pending = pendingRepository.saved.single()
        assertEquals(PendingReason.DIRECTION_UNKNOWN, pending.reason)
        assertNull(pending.suggestedType)
        assertNull(pending.suggestedCategoryId)   // 方向都没定，分类不能瞎猜
        assertFalse(pending.suggestedCountInStats)
        assertFalse(pending.hasSuggestion)

        // 强档提醒：这种通知没给出任何线索，不提醒就等于静默丢弃
        assertEquals(1, pendingNotifier.alerts.size)
        assertEquals(8850L, pendingNotifier.alerts.single().first)
    }

    @Test
    fun `同一笔通知重复投递-待确认不重复入队也不重复提醒`() {
        val text = "您尾号1234账户人民币88.50元"
        assertEquals(CaptureResult.NEEDS_REVIEW, run(text, "cmb.pb"))
        // 第二次是「已在列表中」，不是「新入队」——诊断里要能分辨出来
        assertEquals(CaptureResult.PENDING_DUPLICATE, run(text, "cmb.pb"))

        assertEquals(1, pendingRepository.saved.size)
        assertEquals(1, pendingNotifier.alerts.size)
    }

    @Test
    fun `支付失败-不落库不提示`() {
        val result = run("支付宝 支付失败 ¥88.00", "com.eg.android.AlipayGphone")

        assertEquals(CaptureResult.EXCLUDED, result)
        assertTrue(billRepository.saved.isEmpty())
        assertTrue(pendingRepository.saved.isEmpty())
        assertTrue(notifier.saved.isEmpty())
    }

    @Test
    fun `无金额-静默丢弃`() {
        val result = run("微信 收到一条消息：在吗", "com.tencent.mm")

        assertEquals(CaptureResult.NO_AMOUNT, result)
        assertTrue(billRepository.saved.isEmpty())
        assertTrue(pendingRepository.saved.isEmpty())
        assertTrue(notifier.saved.isEmpty())
    }

    @Test
    fun `重复账单-第二次被判重复且不重复提示`() {
        val text = "支付宝 支付成功 ¥88.50"
        assertEquals(CaptureResult.PARSED_AND_SAVED, run(text, "com.eg.android.AlipayGphone"))
        assertEquals(CaptureResult.DUPLICATE_SKIPPED, run(text, "com.eg.android.AlipayGphone"))

        assertEquals(1, billRepository.saved.size)
        assertEquals(1, notifier.saved.size)
    }

    @Test
    fun `跨秒格重发-被时间窗拦下且不重复记账`() {
        // 相隔 1.5 秒：必然跨出秒格，dedupHash 唯一索引已拦不住，只能靠时间窗兜住
        assertEquals(
            CaptureResult.PARSED_AND_SAVED,
            runAt("支付宝 支付成功 ¥88.50", now, "com.eg.android.AlipayGphone")
        )
        assertEquals(
            CaptureResult.DUPLICATE_SKIPPED,
            runAt("支付宝 支付成功 ¥88.50", now + 1_500L, "com.eg.android.AlipayGphone")
        )

        assertEquals(1, billRepository.saved.size)
        assertEquals(1, notifier.saved.size)
    }

    @Test
    fun `超出时间窗的同额同类消费-各记一笔`() {
        assertEquals(
            CaptureResult.PARSED_AND_SAVED,
            runAt("支付宝 支付成功 ¥88.50", now, "com.eg.android.AlipayGphone")
        )
        assertEquals(
            CaptureResult.PARSED_AND_SAVED,
            runAt(
                "支付宝 支付成功 ¥88.50",
                now + DedupKey.MATCH_WINDOW_MILLIS + 1,
                "com.eg.android.AlipayGphone"
            )
        )

        assertEquals(2, billRepository.saved.size)
    }

    @Test
    fun `待确认跨秒格重发-不重复入队也不重复提醒`() {
        val text = "您尾号1234账户人民币88.50元"
        assertEquals(CaptureResult.NEEDS_REVIEW, runAt(text, now, "cmb.pb"))
        assertEquals(CaptureResult.PENDING_DUPLICATE, runAt(text, now + 1_500L, "cmb.pb"))

        assertEquals(1, pendingRepository.saved.size)
        assertEquals(1, pendingNotifier.alerts.size)
    }

    // —— 类别信号接入 S3 后的行为（L2 / L3 通道）——

    @Test
    fun `窗口内信号带商户词时-通知也能落到具体分类`() {
        signalStore.record(signal(text = "美团外卖 已支付 ¥28.30", packageName = "com.sankuai.meituan"))

        run("支付宝 支付成功 ¥28.30", "com.eg.android.AlipayGphone")

        // 通知正文里只有「支付成功」，本来只会落「其他(12)」
        assertEquals(21L, billRepository.saved.single().categoryId)
    }

    @Test
    fun `信号文本无线索时-回落包名默认分类`() {
        signalStore.record(signal(text = "支付成功", packageName = "com.luckin.coffee"))

        run("支付宝 支付成功 ¥18.00", "com.eg.android.AlipayGphone")

        assertEquals(24L, billRepository.saved.single().categoryId)
    }

    @Test
    fun `无信号时分类仍为方向兜底-既有行为不变`() {
        run("支付宝 支付成功 ¥88.50", "com.eg.android.AlipayGphone")

        assertEquals(12L, billRepository.saved.single().categoryId)
    }

    @Test
    fun `超出信号窗口的信号不参与分类`() {
        // 信号在 ±15s 之外：不能被拿来定分类，否则会把上一笔消费的商户带进这一笔
        signalStore.record(signal(text = "美团外卖 ¥28.30", capturedAt = now - 60_000L))

        run("支付宝 支付成功 ¥28.30", "com.eg.android.AlipayGphone")

        assertEquals(12L, billRepository.saved.single().categoryId)
    }

    private fun signal(
        text: String,
        packageName: String? = null,
        capturedAt: Long = now
    ) = CategorySignal(
        origin = CategorySignalOrigin.ACCESSIBILITY,
        packageName = packageName,
        text = text,
        amountCents = null,
        tradeTimeMillis = null,
        capturedAtMillis = capturedAt
    )

    // —— 假实现：只用内存，不碰 Android ——

    private class FakeBillRepository : BillRepository {

        val saved = mutableListOf<Bill>()
        private val hashes = mutableSetOf<String>()

        override fun observeBills(range: DateRange, filter: BillFilter?): Flow<List<Bill>> =
            flowOf(saved.toList())

        override fun observeById(id: Long): Flow<Bill?> = flowOf(saved.firstOrNull { it.id == id })

        override suspend fun getById(id: Long): Bill? = saved.firstOrNull { it.id == id }

        /** 与真实实现同构：dedupHash 冲突时返回 -1（对应 Room `insert IGNORE`） */
        override suspend fun save(bill: Bill): Long {
            if (!hashes.add(bill.dedupHash)) return -1L
            val stored = bill.copy(id = saved.size + 1L)
            saved += stored
            return stored.id
        }

        override suspend fun deleteById(id: Long) {
            saved.removeAll { it.id == id }
        }

        /** 与真实实现同构：按「金额 + 类型 + 时间差 ≤ 窗口」判断，不看秒格对齐 */
        override suspend fun existsInWindow(
            amountCents: Long,
            type: BillType,
            timeMillis: Long,
            windowMillis: Long
        ): Boolean = saved.any {
            it.amountCents == amountCents &&
                it.type == type &&
                kotlin.math.abs(it.tradeTimeMillis - timeMillis) <= windowMillis
        }

        /** 与真实实现同构：只返回自动来源的账单（手动账永不入列），**不按分类过滤** */
        override suspend fun findAutoBillsInWindow(
            startMillis: Long,
            endMillis: Long
        ): List<Bill> = saved.filter {
            it.tradeTimeMillis in startMillis..endMillis && it.source != SourceType.MANUAL
        }
    }

    private class FakePendingBillRepository : PendingBillRepository {

        val saved = mutableListOf<PendingBill>()
        private val hashes = mutableSetOf<String>()

        override fun observeAll(): Flow<List<PendingBill>> = flowOf(saved.toList())

        override fun observeCount(): Flow<Int> = flowOf(saved.size)

        /** 与真实实现同构：dedupHash 唯一索引冲突返回 -1（同一笔通知不重复入队） */
        override suspend fun enqueue(bill: PendingBill): Long {
            if (!hashes.add(bill.dedupHash)) return -1L
            val stored = bill.copy(id = saved.size + 1L)
            saved += stored
            return stored.id
        }

        override suspend fun getAll(): List<PendingBill> = saved.toList()

        override suspend fun getById(id: Long): PendingBill? = saved.firstOrNull { it.id == id }

        override suspend fun existsInWindow(
            amountCents: Long,
            suggestedType: BillType?,
            timeMillis: Long,
            windowMillis: Long
        ): Boolean = saved.any {
            it.amountCents == amountCents &&
                it.suggestedType == suggestedType &&
                kotlin.math.abs(it.tradeTimeMillis - timeMillis) <= windowMillis
        }

        override suspend fun deleteById(id: Long) {
            saved.removeAll { it.id == id }
        }

        override suspend fun deleteByIds(ids: List<Long>) {
            saved.removeAll { it.id in ids }
        }

        override suspend fun deleteOlderThan(cutoffMillis: Long): Int {
            val before = saved.size
            saved.removeAll { it.createdAt < cutoffMillis }
            return before - saved.size
        }
    }

    private class RecordingNotifier : BillSavedNotifier {

        val saved = mutableListOf<Triple<Long, BillType, SourceType>>()

        override fun notifySaved(amountCents: Long, type: BillType, source: SourceType) {
            saved += Triple(amountCents, type, source)
        }
    }

    private class RecordingPendingNotifier : BillPendingNotifier {

        val alerts = mutableListOf<Triple<Long, PendingReason, String?>>()

        override fun notifyPending(amountCents: Long, reason: PendingReason, matchedKeyword: String?) {
            alerts += Triple(amountCents, reason, matchedKeyword)
        }
    }
}
