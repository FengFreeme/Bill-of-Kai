package com.kai.bill.data.capture.signal

import com.kai.bill.core.common.overlay.CaptureHint
import com.kai.bill.core.common.overlay.CaptureHintKind
import com.kai.bill.core.common.overlay.CaptureHintOverlay
import com.kai.bill.core.common.overlay.ReviewCardReason
import com.kai.bill.core.prefs.KaiPrefs
import com.kai.bill.data.capture.accessibility.BillPageHeuristics
import com.kai.bill.data.ingest.ReconcileOutcome
import com.kai.bill.data.ingest.ReconcileResult
import com.kai.bill.data.ingest.SignalReconciler
import com.kai.bill.data.notify.BillReviewNotifier
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.CategoryRepository
import com.kai.bill.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 类别信号的统一写入入口 —— L2 / L3 采集层只认这一个类。
 *
 * 存在意义：把「信号从哪来」与「信号怎么用」隔开。采集层（无障碍服务 / 截图观察者）
 * 只管把看到的文本交上来，不需要知道它是用于回填旧账单还是生成新账单、要不要弹卡片。
 *
 * 写入顺序有约定：**先入窗口，再做决策**。若通知与信号几乎同时到达，
 * 先入窗口能让流水线的前向路径（S3）直接吃到这条信号，而不必只依赖后面的决策分支；
 * 建账分支又会回头读这个窗口，晚于建账写入就会丢掉分类。
 *
 * 本类也是**副作用收口处**：决策器只做判断与落库，诊断写入与卡片弹出都在这里，
 * 于是决策器可以在纯 JVM 单测里完整跑通（不必构造 DataStore，也不必拉起 Activity）。
 */
@Singleton
class CategorySignalRecorder @Inject constructor(
    private val store: CategorySignalStore,
    private val reconciler: SignalReconciler,
    private val reviewNotifier: BillReviewNotifier,
    private val hintOverlay: CaptureHintOverlay,
    private val kaiPrefs: KaiPrefs,
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 记录一条信号，并异步完成「决策 + 落诊断 + 弹卡片」。
     *
     * 空白文本直接丢弃：它既匹配不出分类，也没有任何诊断价值。
     *
     * 本方法**不会抛异常** —— 采集回调（无障碍事件、截图通知）里任何一次失败
     * 都不该影响系统的事件分发或后续采集。
     */
    fun record(signal: CategorySignal) {
        if (signal.text.isBlank()) return
        store.record(signal)
        scope.launch { reconcileAndRecord(signal) }
    }

    /** 决策 → 落诊断 → 弹反馈（确认卡片 / 提示条） */
    private suspend fun reconcileAndRecord(signal: CategorySignal) {
        val result = runCatching { reconciler.reconcile(signal) }
            .getOrElse { ReconcileResult(ReconcileOutcome.FAILED, null) }

        runCatching {
            kaiPrefs.recordSignalResult(
                result = result.outcome.name,
                text = withSummary(signal, result),
                atMillis = clock.nowMillis(),
                keepHistory = result.outcome.worthHistory
            )
        }

        // 反馈一：确认卡片。动过账（新建 / 补分类）需要用户过目；
        // 没定出分类时卡片是用户补上分类的唯一入口，同样要弹。
        // 放弃 / 重复 / 抽不到金额时弹一张空卡片只会打扰用户。
        // 来由必须一起传下去：卡片要说清「刚记了一笔」「只补了分类」还是「等你选分类」。
        result.reviewBillId?.let { billId ->
            runCatching { reviewNotifier.notifyForReview(billId, result.cardReason()) }
        }

        // 反馈二：提示条。只补「本来完全没有反馈」的结果（见 [CaptureHintOverlay] 的分工表）——
        // 没有它，用户点开一个早就记过的账单详情页时会以为自动记账根本没生效。
        hintFor(result, signal)?.let { hint ->
            runCatching { hintOverlay.show(hint) }
        }
    }

    /** 卡片要解释的来由：新建 / 补分类 / 没定出分类是三件事，文案不能共用 */
    private fun ReconcileResult.cardReason(): ReviewCardReason = when (outcome) {
        ReconcileOutcome.ENRICHED -> ReviewCardReason.ENRICHED
        ReconcileOutcome.NO_MATCH -> ReviewCardReason.NO_MATCH
        else -> ReviewCardReason.CREATED
    }

    /**
     * 这次结果要不要给一条屏幕提示；不需要时返回 null。
     *
     * 只收两种「用户当下看不到任何变化」的结果：已记录（[ReconcileOutcome.DUPLICATE]，
     * 最高频形态）与待确认（[ReconcileOutcome.PENDING]）。其余不提示 ——
     * 尤其是 `NO_AMOUNT`（多数页面不是账单）与 `EXCLUDED`，提示它们等于刷屏。
     *
     * 两条提示都带 [pageKey]：宿主靠它判「是不是同一件事」，少了它连续切两笔不同的
     * 重复账单时第二笔会被误判成重复而不再提示。
     */
    private fun hintFor(result: ReconcileResult, signal: CategorySignal): CaptureHint? =
        when (result.outcome) {
            ReconcileOutcome.DUPLICATE -> CaptureHint(
                kind = CaptureHintKind.ALREADY_RECORDED,
                amountCents = signal.amountCents,
                key = signal.pageKey()
            )

            ReconcileOutcome.PENDING -> CaptureHint(
                kind = CaptureHintKind.NEEDS_REVIEW,
                amountCents = signal.amountCents,
                key = signal.pageKey()
            )

            else -> null
        }

    /**
     * 「这一页」的身份：包名 + 页面全文。
     *
     * 无障碍层保证「一页一信号」，因此按页判重只挡「在同一页进进出出」的反复闪条；
     * 切到另一笔账单必须照常提示 —— 用提示自身字段判重做不到这点（见 `CaptureHint.key`）。
     */
    private fun CategorySignal.pageKey(): String = "${packageName.orEmpty()}|$text"

    /**
     * 给诊断文本加一段「结论摘要」前缀。
     *
     * 页面文本动辄上千字，而 BFS 抽取常把「商户名 + 金额」排在**整页末尾**
     * （金额所在的详情卡片在树里很深）；诊断只截断展示前 200 字，于是
     * 「到底认出了多少钱、分到了哪一类」恰好被截掉 —— 用户会误以为金额根本没读到
     * （真机反馈正是如此：账单已经正确入库，诊断里却看不到 `-1.00`）。
     * 把结论前置到最前，无论怎么截断都看得到。
     */
    private suspend fun withSummary(signal: CategorySignal, result: ReconcileResult): String {
        val parts = mutableListOf<String>()
        signal.amountCents?.let { parts += "金额 ${formatYuan(it)}" }
        result.billId?.let { id ->
            // 分类名只有落库后才知道：按受影响账单的分类反查，失败就不显示，不影响采集
            val name = runCatching {
                billRepository.getById(id)?.let { bill ->
                    categoryRepository.getById(bill.categoryId)?.name
                }
            }.getOrNull()
            if (!name.isNullOrBlank()) parts += "分类 $name"
        }
        // 识别方式：页面/金额判定走哪套规则（支付宝专属 / 微信专属 / 通用），与采集通道「来源」不是一回事
        parts += "识别方式 ${signal.heuristicsLabel()}"
        parts += "来源 ${signal.origin.sourceLabel()}"
        return parts.joinToString(SUMMARY_SEP) + SUMMARY_SEP + signal.text
    }

    /** 分 → `¥12.30`；诊断展示用，固定两位小数 */
    private fun formatYuan(cents: Long): String =
        "¥${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

    /**
     * 本条信号实际走的判定规则。
     *
     * 支付宝 / 微信包名走专属规则；其余走通用字段规则。
     * 写进诊断后，引导页「最近识别记录」能直接看出是哪套逻辑命中的。
     */
    private fun CategorySignal.heuristicsLabel(): String {
        val pkg = packageName ?: return "通用"
        return when {
            BillPageHeuristics.isAlipay(pkg) -> "支付宝专属"
            BillPageHeuristics.isWechat(pkg) -> "微信专属"
            else -> "通用"
        }
    }

    /** 信号来源 → 用户看得懂的两个字（采集通道，不是判定规则） */
    private fun CategorySignalOrigin.sourceLabel(): String = when (this) {
        CategorySignalOrigin.ACCESSIBILITY -> "无障碍"
        CategorySignalOrigin.SCREENSHOT -> "截图"
    }

    private companion object {
        /** 摘要与原文之间的分隔符；只用于本地展示 */
        const val SUMMARY_SEP = " ｜ "
    }
}
