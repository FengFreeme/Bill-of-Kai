package com.kai.bill.data.ingest

import com.kai.bill.data.capture.signal.CategorySignal
import com.kai.bill.data.capture.signal.CategorySignalOrigin
import com.kai.bill.data.parser.CategoryRouter
import com.kai.bill.data.parser.MatchKeywordTables
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.CaptureResult
import com.kai.bill.domain.model.SourceType
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 一条类别信号的最终处理结果。
 *
 * 取值会原样写进诊断（见 `KaiPrefs.recordSignalResult`）并在引导页展示，
 * 因此**新增取值时必须同步 `PermissionCheckScreen` 的文案表** ——
 * 这与 `CaptureResult` 是同一条约定：用户只有这一个自查入口。
 *
 * 其中 [CREATED] 与 [ENRICHED] 还承担另一个职责：它们是**确认卡片**（M-L2-4）的触发条件 ——
 * 只有「确实记上了」才值得弹卡片，其余结果弹了也没东西可改。
 */
enum class ReconcileOutcome {

    /** 成功回填了已有账单的分类 */
    ENRICHED,

    /** 候选不唯一 —— 既不回填也不建账（怕改错，更怕凭空多记一笔） */
    AMBIGUOUS,

    /** 有唯一候选，但信号文本也定不出分类 */
    NO_MATCH,

    /** 无账可配，已由信号生成新账单 */
    CREATED,

    /** 建账时发现期间已有同笔（L1 已落账），属正常竞态 */
    DUPLICATE,

    /** 方向判不出，已进待确认列表 */
    PENDING,

    /** 信号文本抽不到可信金额，不建账 */
    NO_AMOUNT,

    /** 命中排除词（失败 / 预告 / 营销） */
    EXCLUDED,

    /** 解析或落库异常 */
    FAILED;

    /**
     * 是否值得进诊断历史。
     *
     * 「没抽到金额」是压倒性的高频结果 —— 无障碍服务每次读到页面都会过一遍，
     * 绝大多数页面根本不是账单。把它收进历史会瞬间刷满列表，
     * 真正要查的那条（补了分类 / 多记了一笔）反而被顶掉。
     * 这条取舍与通知侧「只记抽到金额的通知」完全一致。
     */
    val worthHistory: Boolean
        get() = this != NO_AMOUNT
}

/**
 * 信号决策的完整结果。
 *
 * @property outcome 结果编码；写进诊断，并驱动引导页文案
 * @property billId 受影响的账单主键；未产生也未改动账单时为 null
 */
data class ReconcileResult(
    val outcome: ReconcileOutcome,
    val billId: Long?
) {

    /**
     * 需要弹确认卡片的账单 id；不需要弹时为 null。
     *
     * 只有「确实记上了」才值得打扰用户：`CREATED`（凭空多了一笔，用户最需要知道）
     * 与 `ENRICHED`（分类被改了，需要知道改成了什么）。其余情形弹了也没有可操作的内容。
     */
    val reviewBillId: Long?
        get() = billId?.takeIf {
            outcome == ReconcileOutcome.CREATED || outcome == ReconcileOutcome.ENRICHED
        }
}

/**
 * 类别信号到达后的**唯一决策入口**。
 *
 * 三条出路，顺序不可换：
 * ```
 * ① 时间窗内命中共唯一账单 → 回填分类
 * ② 命中多条               → 放弃（AMBIGUOUS），既不回填也不建账
 * ③ 一条都没命中           → 等宽限期再复核 → 仍没有 → 由信号建账
 * ```
 *
 * **宽限期默认是 0（不等）**：真实使用形态是「付款之后（或收到通知之后）再点开账单详情页」，
 * 那一刻通知早已落库 —— 有通知就命中回填、没有就直接建账，两种都不需要等。
 * 宽限期只为兜住「页面信号比通知还早约 0.5 秒」这一种竞态；真的遇到时把它调大即可，
 * 届时 L1 一落库就会发事件把这里叫醒（见 [IngestEvents]），不会真的睡满。
 *
 * **建账完全复用 [IngestPipeline]**：金额闸门、方向判定、分类路由、账户路由、去重落库
 * 一行都不重写，所以不存在「信号记账与通知记账口径不一致」的可能。
 */
@Singleton
class SignalReconciler @Inject constructor(
    private val billRepository: BillRepository,
    private val categoryRouter: CategoryRouter,
    private val keywords: MatchKeywordTables,
    private val ingestor: BillIngestor,
    private val clock: Clock,
    private val config: ReconcileConfig,
    private val events: IngestEvents
) {

    /**
     * 处理一条信号。
     *
     * @param signal 采集层构造好的信号
     * @return 处理结果；调用方据此写诊断、决定是否弹确认卡片
     */
    suspend fun reconcile(signal: CategorySignal): ReconcileResult {
        when (val step = matchOnce(signal)) {
            is MatchStep.Done -> return step.result
            MatchStep.NoCandidate -> Unit
        }

        awaitBillSavedOrTimeout(signal)?.let { return it }

        when (val step = matchOnce(signal)) {
            is MatchStep.Done -> return step.result
            MatchStep.NoCandidate -> Unit
        }
        return createBill(signal)
    }

    /**
     * 宽限期内「等落库事件 或 等到超时」，谁先到算谁。
     *
     * 与「盲等一个 delay」的差别：L1 通知一落库就会发事件（见 [IngestEvents]），
     * 这里被叫醒后**立刻复核**并转成回填，把「补记一笔」的等待从「睡满宽限期」
     * 压到「通知到达的那一刻」；期间若被**别的**账单叫醒，复核不命中就继续等，直到超时。
     *
     * @return 宽限期内已有结论时返回该结论（回填 / 放弃 / 判重）；超时且仍无候选返回 null，
     *         由调用方进入建账分支
     */
    private suspend fun awaitBillSavedOrTimeout(signal: CategorySignal): ReconcileResult? {
        // 默认不等待：绝大多数场景是「付款后才点开详情页」，通知早已落库，
        // 首次匹配就能给出结论；没有通知时也不必空等。
        if (config.graceDelayMillis <= 0L) return null

        var early: ReconcileResult? = null
        withTimeoutOrNull(config.graceDelayMillis) {
            while (true) {
                events.billSaved.first()
                when (val step = matchOnce(signal)) {
                    is MatchStep.Done -> {
                        early = step.result
                        return@withTimeoutOrNull
                    }
                    // 只是别的账单落库：继续等，直到宽限期结束
                    MatchStep.NoCandidate -> Unit
                }
            }
        }
        return early
    }

    /**
     * 一次「查窗口 → 回填 / 判重」。
     *
     * 两步顺序不可换：
     * 1. **先试回填** —— 只对「分类仍是兜底值」的账单，这才是允许被程序改写的东西；
     * 2. **再判这笔是否已经记过** —— 见下方长注释，这是防重复记账的关键一步。
     *
     * @return 有结论返回 [MatchStep.Done]；确认「窗口内没这笔」才返回 [MatchStep.NoCandidate] 放行建账
     */
    private suspend fun matchOnce(signal: CategorySignal): MatchStep {
        val reference = signal.effectiveTimeMillis
        val windowBills = billRepository.findAutoBillsInWindow(
            startMillis = reference - config.matchWindowMillis,
            endMillis = reference + config.matchWindowMillis
        )

        // —— 第一步：回填 ——
        // 候选只认「分类仍是兜底值」的账单：已命中分类或已被回填过的没有可改的东西，
        // 它们是这条链路的**纪律线**（程序永远不覆盖已经定好的分类）。
        val fallbacks = windowBills.filter { it.categoryId in categoryRouter.fallbackIds }

        // 金额是更强的身份：信号带金额时先按金额收窄，能大幅降低「同额两笔」的歧义。
        // 信号没带金额（L2 常见）时只能靠「唯一性」兜住，这也是下面必须 singleOrNull 的原因。
        val narrowed = signal.amountCents
            ?.let { amount -> fallbacks.filter { it.amountCents == amount } }
            ?: fallbacks

        if (narrowed.isNotEmpty()) {
            // 只认唯一候选：宁可漏回填，也不能改错账
            val bill = narrowed.singleOrNull()
                ?: return MatchStep.Done(ReconcileResult(ReconcileOutcome.AMBIGUOUS, null))

            // 用**账单自己的来源**过滤词条：有的分类词声明了来源限定（如仅短信场景）
            val match = categoryRouter.resolve(signal.text, bill.type, bill.source, keywords)
            if (match.isFallback) {
                // 有账单可配，但信号也定不出分类 → 什么都不做。
                // 这里绝不能转去建账：那会给同一笔凭空多记一次。
                return MatchStep.Done(ReconcileResult(ReconcileOutcome.NO_MATCH, null))
            }

            val enriched = enrich(bill, match.categoryId)
            return MatchStep.Done(
                if (enriched) {
                    ReconcileResult(ReconcileOutcome.ENRICHED, bill.id)
                } else {
                    ReconcileResult(ReconcileOutcome.FAILED, null)
                }
            )
        }

        // —— 第二步：这笔是不是其实已经记过了？ ——
        // 没有可回填的候选 ≠ 没有账。真机上最高频的形态恰恰是「有账，但已经分好类」：
        //   信号比通知早约 0.5s 到达 → 通知落库时 S3 从信号窗口读到本条信号
        //   → 那笔通知账单**一落库就带正确分类** → 不在兜底集合里 → 被上面筛掉。
        // 少了这一步，就会把它误判成「无账可配」，把同一笔再记一次（真机已复现）
        // ——而且 S5 那两道去重都拦不住：秒桶不同、时间差 5.4s 又超出 3s 时间窗。
        //
        // 身份只能用金额：方向要在流水线里才判得出，这里拿不到。
        // 代价是「同一窗口内、同金额的第二笔真实消费」会被判成重复而漏记 ——
        // 与本项目一贯的取向一致（宁可漏记，也不记错），且诊断里能看到 `重复跳过`。
        // 信号不带金额时 `alreadyRecorded` 恒为 false，而那种信号本就建不了账（NO_AMOUNT），无风险。
        val alreadyRecorded = signal.amountCents
            ?.let { amount -> windowBills.any { it.amountCents == amount } }
            ?: false
        if (alreadyRecorded) {
            return MatchStep.Done(ReconcileResult(ReconcileOutcome.DUPLICATE, null))
        }

        return MatchStep.NoCandidate
    }

    /**
     * 回填分类。
     *
     * **只改 `categoryId` 与 `updatedAt`**：分类不参与去重键（见 `DedupKey`），
     * 所以改分类不会触发唯一索引冲突，也不会把一笔账变成两笔。
     */
    private suspend fun enrich(bill: Bill, categoryId: Long): Boolean = runCatching {
        billRepository.save(bill.copy(categoryId = categoryId, updatedAt = clock.nowMillis()))
    }.isSuccess

    /**
     * 由信号生成一笔新账单 —— **完全复用 L1 的流水线**。
     *
     * 这里没有任何「怎么建账」的重复逻辑：S1 金额闸门、S2 方向、S3 分类
     * （会自动从信号窗口取到本条信号）、S4 账户、S5 去重落库全部由 [IngestPipeline] 承担。
     */
    private suspend fun createBill(signal: CategorySignal): ReconcileResult {
        val ingested = runCatching {
            // 用 ingestOutcome 而不是 ingest：需要拿回刚落库的账单 id 交给确认卡片
            ingestor.ingestOutcome(
                rawText = signal.text.take(config.maxRawTextChars),
                source = signal.origin.toSourceType(),
                packageName = signal.packageName,
                eventTimeMillis = signal.effectiveTimeMillis
            )
        }.getOrElse { return ReconcileResult(ReconcileOutcome.FAILED, null) }

        // 不写 else：CaptureResult 已穷尽，新增枚举项时编译期就会在这里报错
        val outcome = when (ingested.result) {
            CaptureResult.PARSED_AND_SAVED -> ReconcileOutcome.CREATED
            CaptureResult.DUPLICATE_SKIPPED -> ReconcileOutcome.DUPLICATE
            CaptureResult.NEEDS_REVIEW, CaptureResult.PENDING_DUPLICATE -> ReconcileOutcome.PENDING
            CaptureResult.NO_AMOUNT -> ReconcileOutcome.NO_AMOUNT
            CaptureResult.EXCLUDED -> ReconcileOutcome.EXCLUDED
            CaptureResult.PARSE_FAILED, CaptureResult.FILTERED_OUT -> ReconcileOutcome.FAILED
        }
        return ReconcileResult(outcome, ingested.billId)
    }

    /** 一次匹配的结论；用密封类型而不是可空值，避免「无候选」与「有候选但放弃」被混为一谈 */
    private sealed interface MatchStep {

        /** 已有结论，不再进入建账分支 */
        data class Done(val result: ReconcileResult) : MatchStep

        /** 零候选 —— 放行到建账分支 */
        data object NoCandidate : MatchStep
    }
}

/** 信号来源 → 账单来源。两个枚举一一对应，集中一处，避免散落的 `when` */
private fun CategorySignalOrigin.toSourceType(): SourceType = when (this) {
    CategorySignalOrigin.ACCESSIBILITY -> SourceType.ACCESSIBILITY
    CategorySignalOrigin.SCREENSHOT -> SourceType.SCREENSHOT
}
