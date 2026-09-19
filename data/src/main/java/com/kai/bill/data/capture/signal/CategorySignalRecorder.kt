package com.kai.bill.data.capture.signal

import com.kai.bill.core.common.overlay.CaptureToast
import com.kai.bill.core.prefs.KaiPrefs
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
    private val toast: CaptureToast,
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
     *
     * @param signal 采集层构造好的信号
     */
    fun record(signal: CategorySignal) {
        if (signal.text.isBlank()) return
        store.record(signal)
        scope.launch { reconcileAndRecord(signal) }
    }

    /** 决策 → 落诊断 → （确实记上了才）弹确认卡片 */
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

        // 卡片负责「有事要你决定」：凭空补记了一笔，或补全了分类。
        // 反过来，放弃 / 重复 / 抽不到金额时弹一张空卡片只会打扰用户，所以不弹。
        val reviewBillId = result.reviewBillId
        val reviewKind = result.reviewKind
        if (reviewBillId != null && reviewKind != null) {
            runCatching { reviewNotifier.notifyForReview(reviewBillId, reviewKind) }
        }

        // 轻提示负责「告诉你结果」—— 尤其是**已有账单、什么都没做**那种情况：
        // 以前屏幕上完全没动静，用户会怀疑到底识别到没有，于是反复打开同一个页面
        // （真机反馈就是这么来的）。这里刻意不提示 NO_AMOUNT / NO_MATCH：
        // 它们表示「这一屏不是账单 / 定不出分类」，是压倒性的高频结果，提示等于每翻一页弹一次。
        result.feedbackMessage()?.let { message ->
            runCatching { toast.show(message) }
        }
    }

    /**
     * 需要「轻提示」告知的结果文案；不需要提示时返回 null。
     *
     * 只收「用户确实在看某个账单页、而我们也有结论」的几种结果 ——
     * 依据是决策结果而不是页面内容，所以随手划过的普通页面不会发声。
     */
    private fun ReconcileResult.feedbackMessage(): String? = when (outcome) {
        ReconcileOutcome.DUPLICATE -> "此账单已记录，无需重复记录"
        ReconcileOutcome.AMBIGUOUS -> "附近有多笔相似账单，未自动处理"
        ReconcileOutcome.FAILED -> "这次识别没处理成功，可在引导页看详情"
        else -> null
    }

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
        parts += "来源 ${signal.origin.sourceLabel()}"
        return parts.joinToString(SUMMARY_SEP) + SUMMARY_SEP + signal.text
    }

    /** 分 → `¥12.30`；诊断展示用，固定两位小数 */
    private fun formatYuan(cents: Long): String =
        "¥${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

    /** 信号来源 → 用户看得懂的两个字 */
    private fun CategorySignalOrigin.sourceLabel(): String = when (this) {
        CategorySignalOrigin.ACCESSIBILITY -> "无障碍"
        CategorySignalOrigin.SCREENSHOT -> "截图"
    }

    private companion object {
        /** 摘要与原文之间的分隔符；只用于本地展示 */
        const val SUMMARY_SEP = " ｜ "
    }
}
