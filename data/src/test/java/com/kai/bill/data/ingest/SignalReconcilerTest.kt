package com.kai.bill.data.ingest

import com.kai.bill.data.capture.signal.CategorySignal
import com.kai.bill.data.capture.signal.CategorySignalOrigin
import com.kai.bill.data.capture.signal.CategorySignalStore
import com.kai.bill.data.notify.BillPendingNotifier
import com.kai.bill.data.notify.BillSavedNotifier
import com.kai.bill.data.parser.AccountRouter
import com.kai.bill.data.parser.AmountGate
import com.kai.bill.data.parser.CategoryRouter
import com.kai.bill.data.parser.DirectionRouter
import com.kai.bill.data.presets.DefaultMatchKeywords
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.model.PendingBill
import com.kai.bill.domain.model.PendingReason
import com.kai.bill.domain.model.SourceType
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.PendingBillRepository
import com.kai.bill.domain.time.FixedClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 信号决策器的单测 —— 本方案最核心的一段业务规则。
 *
 * 盯住三件会直接写坏用户账目的事：
 * 1. **只有唯一候选才敢回填** —— 多候选时改错分类比不补分类更糟；
 * 2. **「有候选但定不出分类」绝不能转去建账** —— 那会给同一笔凭空多记一次；
 * 3. **宽限期内通知落库要能转为回填** —— 否则扫码场景会被记成两笔。
 *
 * 宽限期在测试里设为 0：既保留「复核一次」的语义，又不必真的等 20 秒。
 */
class SignalReconcilerTest {

    private val now = 1_700_000_000_000L
    private val billRepository = FakeBillRepository()
    private val signalStore = CategorySignalStore(FixedClock(now))

    /** 与生产一致：流水线落库后发事件、决策器等事件 —— 两边必须共用同一个实例 */
    private val ingestEvents = IngestEvents()

    private val pipeline = IngestPipeline(
        keywords = DefaultMatchKeywords.tables,
        amountGate = AmountGate(),
        directionRouter = DirectionRouter(),
        categoryRouter = CategoryRouter(),
        signalStore = signalStore,
        accountRouter = AccountRouter(),
        billRepository = billRepository,
        pendingBillRepository = StubPendingBillRepository(),
        clock = FixedClock(now),
        billSavedNotifier = NoopSavedNotifier,
        billPendingNotifier = NoopPendingNotifier,
        events = ingestEvents
    )

    private val reconciler = SignalReconciler(
        billRepository = billRepository,
        categoryRouter = CategoryRouter(),
        keywords = DefaultMatchKeywords.tables,
        ingestor = BillIngestor(pipeline),
        clock = FixedClock(now),
        config = ReconcileConfig(graceDelayMillis = 0L),
        events = ingestEvents
    )

    // ---------------- 回填 ----------------

    @Test
    fun `唯一候选-回填分类且不新建账单`() = runBlocking {
        billRepository.seed(bill(amountCents = 2830L, categoryId = 12L))

        val outcome = reconcile(signal(amountCents = 2830L, text = "美团外卖 ¥28.30"))

        assertEquals(ReconcileOutcome.ENRICHED, outcome)
        assertEquals(1, billRepository.saved.size)
        assertEquals(21L, billRepository.saved.single().categoryId)
    }

    @Test
    fun `多条候选-放弃且既不回填也不建账`() = runBlocking {
        val first = bill(amountCents = 2830L, categoryId = 12L)
        val second = bill(amountCents = 2830L, categoryId = 12L)
        billRepository.seed(first)
        billRepository.seed(second)

        // 信号不带金额 → 无法收窄，候选仍是两条
        val outcome = reconcile(signal(amountCents = null, text = "美团外卖 ¥28.30"))

        assertEquals(ReconcileOutcome.AMBIGUOUS, outcome)
        assertEquals(2, billRepository.saved.size)
        assertTrue(billRepository.saved.all { it.categoryId == 12L })
    }

    @Test
    fun `信号带金额时按金额收窄-只命中同额那一条`() = runBlocking {
        val target = bill(amountCents = 2830L, categoryId = 12L)
        billRepository.seed(target)
        billRepository.seed(bill(amountCents = 5500L, categoryId = 12L))

        val outcome = reconcile(signal(amountCents = 2830L, text = "美团外卖 ¥28.30"))

        assertEquals(ReconcileOutcome.ENRICHED, outcome)
        assertEquals(21L, billRepository.saved.first().categoryId)
        assertEquals(12L, billRepository.saved.last().categoryId)
    }

    @Test
    fun `有候选但信号也定不出分类-放弃而不是建账`() = runBlocking {
        billRepository.seed(bill(amountCents = 2830L, categoryId = 12L))

        // 文本里没有任何商户/场景词，包名也没有映射
        val outcome = reconcile(signal(amountCents = 2830L, text = "支付成功", packageName = null))

        assertEquals(ReconcileOutcome.NO_MATCH, outcome)
        assertEquals(1, billRepository.saved.size)
        assertEquals(12L, billRepository.saved.single().categoryId)
    }

    // ---------------- 建账 ----------------

    @Test
    fun `零候选-由信号建账并落具体分类`() = runBlocking {
        val outcome = reconcile(
            signal(amountCents = 2830L, text = "美团外卖 已支付 ¥28.30", packageName = "com.sankuai.meituan")
        )

        assertEquals(ReconcileOutcome.CREATED, outcome)
        val created = billRepository.saved.single()
        assertEquals(2830L, created.amountCents)
        assertEquals(BillType.EXPENSE, created.type)
        assertEquals(21L, created.categoryId)
        assertEquals(SourceType.ACCESSIBILITY, created.source)
        assertEquals(now, created.tradeTimeMillis)
    }

    @Test
    fun `信号抽不到金额-不建账`() = runBlocking {
        val outcome = reconcile(signal(amountCents = null, text = "美团外卖 订单详情"))

        assertEquals(ReconcileOutcome.NO_AMOUNT, outcome)
        assertTrue(billRepository.saved.isEmpty())
    }

    @Test
    fun `手动账不进候选-且建账被去重拦下-不会多记一笔`() = runBlocking {
        // 用户自己记过一笔同额同时间的账（分类是「其他」）：
        // 候选查询按来源排除它 → 走到建账分支 → 再被 S5 的金额+类型时间窗去重拦下。
        // 净效果：既没改用户的账，也没多记一笔。
        billRepository.seed(bill(amountCents = 2830L, categoryId = 12L, source = SourceType.MANUAL))

        val outcome = reconcile(signal(amountCents = 2830L, text = "美团外卖 ¥28.30"))

        assertEquals(ReconcileOutcome.DUPLICATE, outcome)
        assertEquals(1, billRepository.saved.size)
        assertEquals(SourceType.MANUAL, billRepository.saved.single().source)
    }

    // ---------------- 宽限期 ----------------

    @Test
    fun `宽限期内通知落库-转为回填而不是建账`() = runBlocking {
        // 第一次查询时一条候选都没有 → 进入宽限期；复核之前 L1 把通知落了库
        billRepository.onSecondCandidateQuery = {
            billRepository.seed(bill(amountCents = 2830L, categoryId = 12L))
        }

        val outcome = reconcile(signal(amountCents = 2830L, text = "美团外卖 ¥28.30"))

        assertEquals(ReconcileOutcome.ENRICHED, outcome)
        assertEquals(1, billRepository.saved.size)
        assertEquals(21L, billRepository.saved.single().categoryId)
    }

    /**
     * 真机复现过的丢账 bug 的回归用例（2026-09-19）：
     *
     * 信号先到 → 通知在宽限期内落库 → 通知落库时 S3 从信号窗口读到本条信号，
     * 于是那笔账单**一落库就带着正确分类**（21，不在兜底集合里）。
     * 若复核只查兜底分类，就会「看不见」它，误判成无账可配而把同一笔记两次。
     */
    @Test
    fun `宽限期内通知落库且已被信号分好类-不重复建账`() = runBlocking {
        billRepository.onSecondCandidateQuery = {
            // 已经分好类的那一笔 —— 它不是兜底分类，但绝不代表「没记过」
            billRepository.seed(bill(amountCents = 2830L, categoryId = 21L))
        }

        val outcome = reconcile(signal(amountCents = 2830L, text = "美团外卖 ¥28.30"))

        assertEquals(ReconcileOutcome.DUPLICATE, outcome)
        assertEquals(1, billRepository.saved.size)
    }

    /**
     * 「事件唤醒」回归用例：通知在宽限期内落库时，应被**立刻**叫醒并转回填，
     * 而不是睡满整个宽限期再复核。
     *
     * 判据两条缺一不可：
     * 1. 结果正确 —— 这笔已被 L1 记下（且分类已由信号窗口定好），判重即可，不重复建账；
     * 2. 耗时远小于宽限期本身 —— 证明是被落库事件唤醒的，而不是超时后才复核。
     */
    @Test
    fun `通知在宽限期内落库-被事件唤醒提前返回`() = runBlocking {
        val reconcilerWithGrace = SignalReconciler(
            billRepository = billRepository,
            categoryRouter = CategoryRouter(),
            keywords = DefaultMatchKeywords.tables,
            ingestor = BillIngestor(pipeline),
            clock = FixedClock(now),
            config = ReconcileConfig(graceDelayMillis = 3_000L),
            events = ingestEvents
        )

        // 信号先进窗口：L1 落库时 S3 会读到它，于是那笔一落库就带上了正确分类
        signalStore.record(signal(amountCents = 2830L, text = "美团外卖 已支付 ¥28.30"))

        val startedAt = System.currentTimeMillis()
        launch {
            delay(100)
            pipeline.run(
                rawText = "美团外卖 已支付 ¥28.30",
                source = SourceType.NOTIFICATION,
                packageName = "com.sankuai.meituan",
                eventTimeMillis = now
            )
        }

        val result = reconcilerWithGrace.reconcile(
            signal(amountCents = 2830L, text = "美团外卖 已支付 ¥28.30")
        )
        val elapsedMillis = System.currentTimeMillis() - startedAt

        assertEquals(ReconcileOutcome.DUPLICATE, result.outcome)
        assertEquals(1, billRepository.saved.size)
        assertTrue(
            "应被落库事件唤醒而不是睡满宽限期（实际 ${elapsedMillis}ms）",
            elapsedMillis < 1_500L
        )
    }

    @Test
    fun `窗口内同额账单已存在-即便分类已定-也不再建账`() = runBlocking {
        // 不论它是通知落的、还是外部同步进来的，只要窗口内已有同额自动来源账单
        // 就说明这笔已经被记过，再记一次就是重复
        billRepository.seed(bill(amountCents = 2830L, categoryId = 21L, source = SourceType.SMS))

        val outcome = reconcile(signal(amountCents = 2830L, text = "美团外卖 ¥28.30"))

        assertEquals(ReconcileOutcome.DUPLICATE, outcome)
        assertEquals(1, billRepository.saved.size)
    }

    @Test
    fun `窗口内金额不同的已分类账单-不构成重复-仍会建账`() = runBlocking {
        // 判重只看金额：另一笔钱不该把这一笔挡掉
        billRepository.seed(bill(amountCents = 5500L, categoryId = 21L))

        val outcome = reconcile(signal(amountCents = 2830L, text = "美团外卖 已支付 ¥28.30"))

        assertEquals(ReconcileOutcome.CREATED, outcome)
        assertEquals(2, billRepository.saved.size)
    }

    // ---------------- 确认卡片的触发凭据 ----------------

    @Test
    fun `建账成功时带回账单id-供卡片定位刚记下的那一笔`() = runBlocking {
        val result = reconcileResult(signal(amountCents = 2830L, text = "美团外卖 已支付 ¥28.30"))

        assertEquals(ReconcileOutcome.CREATED, result.outcome)
        assertEquals(billRepository.saved.single().id, result.reviewBillId)
    }

    @Test
    fun `回填成功时也带回账单id`() = runBlocking {
        billRepository.seed(bill(amountCents = 2830L, categoryId = 12L))

        val result = reconcileResult(signal(amountCents = 2830L, text = "美团外卖 ¥28.30"))

        assertEquals(ReconcileOutcome.ENRICHED, result.outcome)
        assertEquals(billRepository.saved.single().id, result.reviewBillId)
    }

    @Test
    fun `多候选放弃时不弹卡片-没有可改可撤的账单`() = runBlocking {
        billRepository.seed(bill(amountCents = 2830L, categoryId = 12L))
        billRepository.seed(bill(amountCents = 2830L, categoryId = 12L))

        val result = reconcileResult(signal(amountCents = null, text = "美团外卖 ¥28.30"))

        assertEquals(ReconcileOutcome.AMBIGUOUS, result.outcome)
        assertNull(result.reviewBillId)
    }

    @Test
    fun `抽不到金额时不弹卡片`() = runBlocking {
        val result = reconcileResult(signal(amountCents = null, text = "美团外卖 订单详情"))

        assertEquals(ReconcileOutcome.NO_AMOUNT, result.outcome)
        assertNull(result.reviewBillId)
    }

    // ---------------- 辅助 ----------------

    /** 复刻 `CategorySignalRecorder` 的约定：先入窗口，再做决策 */
    private suspend fun reconcileResult(signal: CategorySignal): ReconcileResult {
        signalStore.record(signal)
        return reconciler.reconcile(signal)
    }

    private suspend fun reconcile(signal: CategorySignal): ReconcileOutcome =
        reconcileResult(signal).outcome

    private fun signal(
        amountCents: Long?,
        text: String,
        packageName: String? = null,
        origin: CategorySignalOrigin = CategorySignalOrigin.ACCESSIBILITY
    ) = CategorySignal(
        origin = origin,
        packageName = packageName,
        text = text,
        amountCents = amountCents,
        tradeTimeMillis = null,
        capturedAtMillis = now
    )

    private fun bill(
        amountCents: Long,
        categoryId: Long,
        source: SourceType = SourceType.NOTIFICATION,
        type: BillType = BillType.EXPENSE,
        time: Long = now
    ) = Bill(
        amountCents = amountCents,
        type = type,
        countInStats = true,
        categoryId = categoryId,
        accountId = null,
        merchant = null,
        note = null,
        tradeTimeMillis = time,
        source = source,
        rawText = null,
        dedupHash = "seed-$amountCents-$categoryId-${source.name}",
        createdAt = time,
        updatedAt = time
    )

    // ---------------- 假实现 ----------------

    private class FakeBillRepository : BillRepository {

        val saved = mutableListOf<Bill>()
        private val hashes = mutableSetOf<String>()

        /** 第 2 次候选查询（即宽限期复核）之前触发，用于模拟「期间通知落库」 */
        var onSecondCandidateQuery: (() -> Unit)? = null

        private var candidateQueryCount = 0

        fun seed(bill: Bill) {
            hashes += bill.dedupHash
            saved += bill.copy(id = saved.size + 1L)
        }

        override fun observeBills(range: DateRange, filter: BillFilter?): Flow<List<Bill>> =
            flowOf(saved.toList())

        override fun observeById(id: Long): Flow<Bill?> = flowOf(saved.firstOrNull { it.id == id })

        override suspend fun getById(id: Long): Bill? = saved.firstOrNull { it.id == id }

        override suspend fun save(bill: Bill): Long {
            if (bill.id == 0L) {
                if (!hashes.add(bill.dedupHash)) return -1L
                val stored = bill.copy(id = saved.size + 1L)
                saved += stored
                return stored.id
            }
            val index = saved.indexOfFirst { it.id == bill.id }
            if (index >= 0) saved[index] = bill
            return bill.id
        }

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

        override suspend fun findAutoBillsInWindow(
            startMillis: Long,
            endMillis: Long
        ): List<Bill> {
            candidateQueryCount++
            if (candidateQueryCount == 2) onSecondCandidateQuery?.invoke()
            return saved.filter {
                it.tradeTimeMillis in startMillis..endMillis && it.source != SourceType.MANUAL
            }
        }

        override suspend fun deleteById(id: Long) {
            saved.removeAll { it.id == id }
        }
    }

    /** 建账路径在本类用例里不会走到待确认分支，因此只给最小实现 */
    private class StubPendingBillRepository : PendingBillRepository {

        override fun observeAll(): Flow<List<PendingBill>> = flowOf(emptyList())

        override fun observeCount(): Flow<Int> = flowOf(0)

        override suspend fun enqueue(bill: PendingBill): Long = -1L

        override suspend fun getAll(): List<PendingBill> = emptyList()

        override suspend fun getById(id: Long): PendingBill? = null

        override suspend fun existsInWindow(
            amountCents: Long,
            suggestedType: BillType?,
            timeMillis: Long,
            windowMillis: Long
        ): Boolean = false

        override suspend fun deleteById(id: Long) = Unit

        override suspend fun deleteByIds(ids: List<Long>) = Unit

        override suspend fun deleteOlderThan(cutoffMillis: Long): Int = 0
    }

    private object NoopSavedNotifier : BillSavedNotifier {
        override fun notifySaved(amountCents: Long, type: BillType, source: SourceType) = Unit
    }

    private object NoopPendingNotifier : BillPendingNotifier {
        override fun notifyPending(amountCents: Long, reason: PendingReason, matchedKeyword: String?) = Unit
    }
}
