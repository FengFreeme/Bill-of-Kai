package com.kai.bill.data.capture.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.kai.bill.core.prefs.KaiPrefs
import com.kai.bill.data.capture.signal.CategorySignal
import com.kai.bill.data.capture.signal.CategorySignalOrigin
import com.kai.bill.data.capture.signal.CategorySignalRecorder
import com.kai.bill.data.capture.signal.SignalTextParser
import com.kai.bill.domain.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * L2 · 无障碍采集入口：白名单 App 的页面变化时，抽出当前页面的可见文本投给
 * [CategorySignalRecorder]。它**不解析金额与方向**、也**不落库** —— 那是流水线的职责。
 *
 * 三条安全约束：
 * 1. **白名单前置**：非白名单包在事件回调的第一行就被丢弃，不读节点、不建对象；
 * 2. **总开关前置**：用户在「权限与采集」页关掉自动采集后立即停止产出；
 * 3. **全部包 `runCatching`**：任何一次采集异常都不能打断系统的事件分发。
 *
 * 时序决定「多快能判定这是不是账单详情页」：换页立即评估、同页按 [POLL_INTERVAL_MS] 短去抖、
 * 单页最多评估 [PAGE_EVAL_MAX_MS]（抽到金额就收手）、一页一信号（[pageEmitted]，优先单窗）。
 *
 * 服务由系统在用户授权后绑定，**不能也不应**由应用主动创建或启动。
 *
 * @see com.google.android.accessibility.selecttospeak.SelectToSpeakService 清单里声明的入口
 */
abstract class KaiAccessibilityCore : AccessibilityService() {

    @Inject
    lateinit var recorder: CategorySignalRecorder

    @Inject
    lateinit var kaiPrefs: KaiPrefs

    @Inject
    lateinit var clock: Clock

    @Inject
    lateinit var serviceHolder: AccessibilityServiceHolder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 用户主开关的本地镜像：事件回调在主线程且可能高频触发，不能每次都去读 DataStore（挂起函数），
     * 由 [onServiceConnected] 里的订阅持续更新。
     */
    @Volatile
    private var captureEnabled = false

    /** 当前页面的标识（包名 + 窗口 id）；换窗口即视为进入新页面 */
    private var lastPageKey: String? = null

    /** 当前页面的评估任务；换页会取消并重启 */
    private var pageJob: Job? = null

    /**
     * 本页的评估截止时刻（毫秒）：从页面首个事件算起，最多评估 [PAGE_EVAL_MAX_MS]。
     *
     * 设为**有界**，是为了不再「等页面彻底静止」—— 微信账单页异步渲染会让
     * 「静默一段时间」的判定被无限推迟（真机上曾因此拖到数秒才出结果）。
     */
    @Volatile
    private var pageDeadline = 0L

    /** 本页是否已产出过信号：「一页一信号」，同页加载过程不会反复记账 */
    @Volatile
    private var pageEmitted = false

    override fun onServiceConnected() {
        super.onServiceConnected()

        // 运行时兜底：XML 是主配置，这里覆盖 ROM 合并 / 旧进程残留。
        runCatching {
            serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                // 取 FEEDBACK_ALL_MASK：不缩小可读面。
                feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                notificationTimeout = 100
                flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
        }

        // 登记自身上下文：确认卡片的悬浮层需要一个有效的窗口 token，
        // 只有服务自己的上下文能给出（Application 上下文的 token 是 null，实测会抛 BadTokenException）
        serviceHolder.attach(this)

        // 真实连通态：与「系统设置里勾选了没有」是两件事，服务可能被 ROM 杀掉而设置仍显示已开启
        scope.launch { runCatching { kaiPrefs.setAccessibilityEnabled(true) } }

        // 主开关变化时同步本地镜像；退出作用域无需手动收尾，scope 在 onDestroy 里统一取消
        kaiPrefs.captureState
            .map { it.captureEnabled }
            .distinctUntilChanged()
            .onEach { captureEnabled = it }
            .launchIn(scope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!captureEnabled) return

        val packageName = event?.packageName?.toString() ?: return
        if (!AccessibilityWhitelist.isWatched(packageName)) return

        // 页面身份 = 包名 + 窗口 id；「窗口状态变化」表示换页
        // （微信常复用同一个窗口展示不同账单，此时 windowId 不变，只能靠事件类型识别换页）
        val windowId = event.windowId
        val pageKey = "$packageName#$windowId"
        val isNewPage = pageKey != lastPageKey ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        if (isNewPage) {
            lastPageKey = pageKey
            pageDeadline = clock.nowMillis() + PAGE_EVAL_MAX_MS
            pageEmitted = false
        }
        if (pageEmitted) return

        // 换页时**立即**评估一次 —— 这是「快速判定」的关键，不再等页面静止；
        // 同页内容变化则用短去抖复评。两者都受本页截止时刻约束，延迟有界。
        pageJob?.cancel()
        pageJob = scope.launch {
            if (!isNewPage) delay(POLL_INTERVAL_MS)
            evaluatePage(packageName, windowId)
        }
    }

    /**
     * 评估「当前这一页」：确认为账单详情页才产出信号，且一页最多产出一次。
     *
     * 用「轮询到抽到金额 / 到达本页时限」取代「等页面彻底静止」：
     * - 抽到金额就**立刻**收手 → 快；
     * - 金额晚到时最多等到 [PAGE_EVAL_MAX_MS] → 有界；
     * - 到时限仍抽不到金额也产出一次（这种信号只能补分类、不能建账，见 [CategorySignal]）。
     */
    private suspend fun evaluatePage(packageName: String, windowId: Int) {
        while (true) {
            val signal = runCatching { buildSignal(packageName, windowId) }.getOrNull()
            if (signal != null) {
                val expired = clock.nowMillis() >= pageDeadline
                if (signal.amountCents != null || expired) {
                    // 取消检查必须放在 runCatching 之外：runCatching 会把
                    // CancellationException 一并吞掉，导致已作废的页面仍投出信号
                    coroutineContext.ensureActive()
                    pageEmitted = true
                    runCatching { recorder.record(signal) }
                    return
                }
            }
            if (clock.nowMillis() >= pageDeadline) return
            delay(POLL_INTERVAL_MS)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        scope.launch { runCatching { kaiPrefs.setAccessibilityEnabled(false) } }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        // 服务上下文即将失效，必须注销：留着会让悬浮层挂在一个死掉的上下文上
        serviceHolder.detach(this)
        scope.cancel()
        super.onDestroy()
    }

    /**
     * 把「当前这一页」变成一条类别信号；判定规则按包名分发到 [BillPageHeuristics]。
     *
     * 前置条件：`accessibility_service_config` 必须带 `flagRetrieveInteractiveWindows`，
     * 否则 [windows] 恒为空列表。它**不扩大可读的 App 范围** —— 白名单照旧在第一行过滤。
     */
    private fun buildSignal(packageName: String, windowId: Int): CategorySignal? {
        val text = billDetailText(packageName, windowId) ?: return null
        return CategorySignal(
            origin = CategorySignalOrigin.ACCESSIBILITY,
            packageName = packageName,
            text = text,
            // 支付宝 / 微信走详情页结构抽取；其它走标签邻近。
            // 抽不到即 null，此时信号只能回填、不能建账 —— 宁可不建，也不建错账
            amountCents = SignalTextParser.extractAmountCents(text, packageName),
            tradeTimeMillis = SignalTextParser.extractTradeTimeMillis(text, clock),
            capturedAtMillis = clock.nowMillis()
        )
    }

    /**
     * 取「当前这一页」对应的账单详情文本。优先级：
     * 1. 事件来源窗口（单窗即可判详情）
     * 2. 同包名里**唯一**一个像账单详情的窗口（多个完整详情并存 → 放弃，防混账）
     * 3. 活动窗口兜底
     * 4. **仅微信**：单窗都判不过时，合并同包名各窗文本再判一次
     *
     * 需要第 4 步是因为微信把详情拆成「顶栏/卡片」与「底栏按钮」等多个窗口，锚点分散后
     * 单窗永远过不了页型；合并只在没有单窗是完整详情时启用，不会把两份完整账单拼成一笔。
     */
    private fun billDetailText(packageName: String, windowId: Int): String? {
        val samePackage = runCatching {
            windows.filter { it.root?.packageName == packageName }
        }.getOrDefault(emptyList())

        fun isDetail(text: String) = BillPageHeuristics.isBillDetail(text, packageName)

        // 1) 事件来源窗口（用户正在看的那一屏）
        samePackage.firstOrNull { it.id == windowId }
            ?.root
            ?.let { PageTextExtractor.extract(it) }
            ?.takeIf(::isDetail)
            ?.let { return it }

        // 2) 同包名里唯一一个像账单详情的窗口（多个候选即放弃，避免两页混成一页）
        val roots = samePackage.mapNotNull { it.root }
        val details = roots.asSequence()
            .mapNotNull { PageTextExtractor.extract(it) }
            .filter(::isDetail)
            .toList()
        details.singleOrNull()?.let { return it }
        if (details.size > 1) return null

        // 3) 活动窗口兜底：个别窗口不出现在 [windows] 里
        val active = rootInActiveWindow
        if (active != null && active.packageName?.toString() == packageName) {
            PageTextExtractor.extract(active)?.takeIf(::isDetail)?.let { return it }
        }

        // 4) 微信多窗拼页：单窗都缺锚点时，合并同包根节点再判
        if (BillPageHeuristics.isWechat(packageName) && roots.isNotEmpty()) {
            PageTextExtractor.extract(roots)?.takeIf(::isDetail)?.let { return it }
        }
        return null
    }

    private companion object {

        /** 同页内容变化后的复评间隔：短去抖，避免页面加载过程高频重复抽取 */
        const val POLL_INTERVAL_MS = 120L

        /**
         * 单页评估时限：从页面首个事件算起，最多评估这么久（含等待金额渲染）。
         *
         * 取 900ms 而不是「等页面彻底静止」：微信账单页异步渲染会不断刷新事件，
         * 静止判定可能被推迟到数秒；这里给出硬上限，保证「判定」在一秒内出结果。
         */
        const val PAGE_EVAL_MAX_MS = 900L
    }
}
