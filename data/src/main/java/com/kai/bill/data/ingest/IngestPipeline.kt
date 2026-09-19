package com.kai.bill.data.ingest

import com.kai.bill.data.capture.signal.CategorySignalStore
import com.kai.bill.data.notify.BillPendingNotifier
import com.kai.bill.data.notify.BillSavedNotifier
import com.kai.bill.data.parser.AccountRouter
import com.kai.bill.data.parser.AmountGate
import com.kai.bill.data.parser.CategoryRouter
import com.kai.bill.data.parser.DedupKey
import com.kai.bill.data.parser.DirectionHit
import com.kai.bill.data.parser.DirectionRouter
import com.kai.bill.data.parser.MatchKeywordTables
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.CaptureResult
import com.kai.bill.domain.model.PendingBill
import com.kai.bill.domain.model.PendingReason
import com.kai.bill.domain.model.SourceType
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.PendingBillRepository
import com.kai.bill.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「原始文本 → 账单」的四级路由流水线。
 *
 * 取代旧实现里「一条正则同时决定 认不认 / 金额 / 收支 / 分类」的整包式匹配：
 * 每级只做一件事、输入输出都是纯数据，因此每一级都能单独单测，
 * 出错时也能明确回答「卡在哪一级」（旧实现只有 `NO_RULE` / `NO_AMOUNT` 两个失败值，
 * 分不出是方向判不出还是分类没命中）。
 *
 * ```
 * S1 AmountGate        金额闸门   抽不到 → NO_AMOUNT（静默）
 * S2 DirectionRouter   方向路由   支出/收入/转账 → 继续；待确认 → 入待确认列表；排除词 → EXCLUDED
 * S3 CategoryRouter    分类路由   通知正文 → 类别信号文本 → 信号包名默认分类 → 方向兜底
 * S4 AccountRouter     账户路由   按来源包名落账户
 * S5                   去重落库   dedupHash 唯一索引；冲突即重复
 * ```
 *
 * 与统计口径的关系：`countInStats` 由**类型推导**（[com.kai.bill.data.parser.DirectionHit.countInStats]
 * = `type != TRANSFER`），不再像旧实现那样硬编码 `true` —— 否则还款类会混进「本月支出」。
 * 统计 SQL 一行未改，正确性仍由 `StatsDao` 与 `Bill.isCounted` 守着。
 */
@Singleton
class IngestPipeline @Inject constructor(
    private val keywords: MatchKeywordTables,
    private val amountGate: AmountGate,
    private val directionRouter: DirectionRouter,
    private val categoryRouter: CategoryRouter,
    private val signalStore: CategorySignalStore,
    private val accountRouter: AccountRouter,
    private val billRepository: BillRepository,
    private val pendingBillRepository: PendingBillRepository,
    private val clock: Clock,
    private val billSavedNotifier: BillSavedNotifier,
    private val billPendingNotifier: BillPendingNotifier,
    private val events: IngestEvents
) {

    /**
     * 采集一次，只关心结果编码。
     *
     * @param rawText 已清洗成单行的通知 / 短信原文
     * @param source 采集来源
     * @param packageName 通知来源包名，用于 S4 账户路由（短信来源传 null）
     * @param eventTimeMillis 事件发生时间；通知场景传 `postTime`，比「处理时刻」更接近真实交易时间。
     *        为 null 时退回 [Clock.nowMillis]（与旧实现一致）
     */
    suspend fun run(
        rawText: String,
        source: SourceType,
        packageName: String? = null,
        eventTimeMillis: Long? = null
    ): CaptureResult = runOutcome(rawText, source, packageName, eventTimeMillis).result

    /**
     * 与 [run] **完全同一条流水线**，额外返回落库账单 id。
     *
     * 为什么要把 id 带出来：确认卡片要在「刚记下这一笔」之后让用户改 / 撤，
     * 而两者都要以账单主键为凭据。由流水线顺带返回，比事后按「金额 + 时间」反查可靠得多 ——
     * 反查一旦遇到同额多笔就无法确定是哪一笔，而改错 / 删错账单是不可接受的。
     *
     * @return 结果编码 + 落库 id（未落库时为 null）
     */
    suspend fun runOutcome(
        rawText: String,
        source: SourceType,
        packageName: String? = null,
        eventTimeMillis: Long? = null
    ): IngestOutcome {

        // —— S1 金额闸门：没有金额的文本（账单提醒 / 活动推送 / 聊天）到此为止 ——
        val amountCents = amountGate.extract(rawText)
            ?: return IngestOutcome(CaptureResult.NO_AMOUNT, null)

        val tradeTime = eventTimeMillis ?: clock.nowMillis()

        // —— S2 方向路由 ——
        val direction = directionRouter.route(rawText, source, keywords)
        if (direction.isExcluded) {
            // 失败 / 预告 / 营销文本：静默丢弃。它同样带金额，落库就是错账。
            return IngestOutcome(CaptureResult.EXCLUDED, null)
        }
        if (direction.isPending) {
            // 待确认只写 pending_bill，没有正式账单 id 可返回
            val pendingResult = enqueuePending(
                rawText = rawText,
                source = source,
                packageName = packageName,
                tradeTime = tradeTime,
                amountCents = amountCents,
                direction = direction
            )
            return IngestOutcome(pendingResult, null)
        }
        // 防御：isPending 为 false 且未被排除时类型必然存在；真出现 null 说明新增了走向却漏改这里
        val type = direction.type ?: return IngestOutcome(CaptureResult.PARSE_FAILED, null)

        // —— S3 分类路由 + S4 账户路由 ——
        // 取「交易时间 ± 15s」内的类别信号：信号可能早于通知（页面先出现、通知稍后到），
        // 也可能晚于通知（通知先落库、用户再点进详情页），两个方向都要覆盖。
        val signals = signalStore.recent(tradeTime)
        val categoryId = categoryRouter.resolve(rawText, type, source, keywords, signals).categoryId
        val accountId = accountRouter.resolve(packageName, source)

        // —— S5 去重落库 ——
        // 第二道去重（时间窗）：唯一索引只认「同一秒格」，两条投递跨过秒格边界就会漏网；
        // 这里按实际时间差再判一次，与秒格对齐无关。两道任一命中即视为重复。
        val windowHit = billRepository.existsInWindow(
            amountCents = amountCents,
            type = type,
            timeMillis = tradeTime,
            windowMillis = DedupKey.MATCH_WINDOW_MILLIS
        )
        if (windowHit) return IngestOutcome(CaptureResult.DUPLICATE_SKIPPED, null)

        val now = clock.nowMillis()
        val bill = Bill(
            amountCents = amountCents,
            type = type,
            countInStats = direction.countInStats,
            categoryId = categoryId,
            accountId = accountId,
            merchant = null,
            note = null,
            tradeTimeMillis = tradeTime,
            source = source,
            rawText = rawText,
            dedupHash = DedupKey.build(amountCents, tradeTime, type, merchant = null),
            createdAt = now,
            updatedAt = now
        )

        val savedId = billRepository.save(bill)
        return if (savedId == -1L) {
            // 唯一索引冲突：同一笔消费的通知 / 短信双通道到达，后到者被忽略
            IngestOutcome(CaptureResult.DUPLICATE_SKIPPED, null)
        } else {
            billSavedNotifier.notifySaved(amountCents, type, source)
            // 通知「有账单落库」：等在宽限期里的决策器据此立刻醒来复核（见 SignalReconciler）
            events.notifyBillSaved()
            IngestOutcome(CaptureResult.PARSED_AND_SAVED, savedId)
        }
    }

    /**
     * 待确认分支：落 `pending_bill` 等用户拍板。
     *
     * 三条约定：
     * - `dedupHash` 与 `bill` 表**同一套算法**：同一笔通知不会重复入队（唯一索引冲突返回 -1），
     *   用户确认落库时也直接复用这个 hash，正式账单的防重自动生效；
     * - **建议值一律 `suggestedCountInStats = false`**：待确认的含义就是「还不确定」，
     *   不确定的东西不进统计是最安全的默认；用户确认后可在编辑页打开；
     * - **写失败不影响主流程**：待确认是「锦上添花」的兜底，写不进去只补一条诊断，
     *   绝不能让一次 DB 异常把整条采集链路打断。
     */
    private suspend fun enqueuePending(
        rawText: String,
        source: SourceType,
        packageName: String?,
        tradeTime: Long,
        amountCents: Long,
        direction: DirectionHit
    ): CaptureResult {
        val suggestedType = direction.suggestedType

        // 与正常账单同样的第二道去重：跨秒格的同一笔由时间窗拦下，不入队也不再提醒。
        val windowHit = pendingBillRepository.existsInWindow(
            amountCents = amountCents,
            suggestedType = suggestedType,
            timeMillis = tradeTime,
            windowMillis = DedupKey.MATCH_WINDOW_MILLIS
        )
        if (windowHit) return CaptureResult.PENDING_DUPLICATE

        val pending = PendingBill(
            amountCents = amountCents,
            suggestedType = suggestedType,
            // 方向明确（转出 / 红包）时给出具体分类建议；判不出方向时只能留空
            suggestedCategoryId = suggestedType?.let { type ->
                categoryRouter
                    .resolve(rawText, type, source, keywords, signalStore.recent(tradeTime))
                    .categoryId
            },
            suggestedAccountId = accountRouter.resolve(packageName, source),
            suggestedCountInStats = false,
            reason = direction.pendingReason ?: PendingReason.DIRECTION_UNKNOWN,
            matchedKeyword = direction.matchedKeyword,
            tradeTimeMillis = tradeTime,
            source = source,
            rawText = rawText,
            // 用「建议方向」参与身份：用户在待确认页接受建议时，落成的正式账单
            // 会用同一个键，因此「这笔此前其实已经落过账」能被 bill 表的唯一索引拦下
            dedupHash = DedupKey.build(amountCents, tradeTime, suggestedType, merchant = null),
            createdAt = clock.nowMillis()
        )

        return runCatching { pendingBillRepository.enqueue(pending) }
            .fold(
                onSuccess = { rowId ->
                    // rowId == -1 表示这笔已在待确认列表里（dedupHash 撞唯一索引）：
                    // 不做任何提醒，且结果标成「已存在」而不是「新入队」——
                    // 同一条通知反复投递会把用户吵到关掉通知通道，诊断里也不该看着像又加了一条。
                    if (rowId <= 0L) {
                        return@fold CaptureResult.PENDING_DUPLICATE
                    }
                    // 分档：只有「判不出方向」才值得弹悬浮横幅；转出 / 红包 / 泛化入账都有
                    // 明确建议值，进列表与角标即可（汇总通知由条数观察驱动）。
                    if (pending.reason == PendingReason.DIRECTION_UNKNOWN) {
                        billPendingNotifier.notifyPending(
                            amountCents = amountCents,
                            reason = pending.reason,
                            matchedKeyword = pending.matchedKeyword
                        )
                    }
                    CaptureResult.NEEDS_REVIEW
                },
                onFailure = { CaptureResult.PARSE_FAILED }
            )
    }
}

/**
 * 一次采集的完整结果。
 *
 * @property result 结果编码，含义与 [IngestPipeline.run] 完全一致
 * @property billId 成功落库的账单主键；未落库（无金额 / 被排除 / 重复 / 入待确认）时为 null
 */
data class IngestOutcome(
    val result: CaptureResult,
    val billId: Long?
)
