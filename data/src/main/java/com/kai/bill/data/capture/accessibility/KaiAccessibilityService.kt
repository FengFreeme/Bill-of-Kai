package com.kai.bill.data.capture.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.kai.bill.core.prefs.KaiPrefs
import com.kai.bill.data.capture.signal.CategorySignal
import com.kai.bill.data.capture.signal.CategorySignalOrigin
import com.kai.bill.data.capture.signal.CategorySignalRecorder
import com.kai.bill.data.capture.signal.SignalTextParser
import com.kai.bill.domain.time.Clock
import dagger.hilt.android.AndroidEntryPoint
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
 * L2 · 无障碍采集入口。
 *
 * 职责：在**白名单 App 的页面发生变化**时，把当前页面的可见文本抽出来投给
 * [CategorySignalRecorder]，由它进入类别信号窗口。它**不解析金额与方向**，
 * 也**不落库** —— 那些都是流水线的职责。
 *
 * 三条安全约束：
 * 1. **白名单前置**：非白名单包在事件回调的第一行就被丢弃，不读节点、不建对象；
 * 2. **总开关前置**：用户在「权限与采集」页关掉自动采集后，本服务立即停止产出；
 * 3. **全部包 `runCatching`**：任何一次采集异常都不能打断系统的事件分发。
 *
 * 时序设计（决定「多快能判定这是不是账单详情页」）：
 * - **换页立即评估**：`TYPE_WINDOW_STATE_CHANGED` 或窗口 id 变化时不等待，马上抽一次；
 * - **同页短去抖**：内容变化按 [POLL_INTERVAL_MS] 复评，避免加载过程高频重复抽取；
 * - **单页时限 [PAGE_EVAL_MAX_MS]**：抽到金额就立刻收手，抽不到则最多等到时限 ——
 *   既不「等页面静止」把延迟拖到数秒，也不会无限期反复抽；
 * - **一页一信号**：每页最多产出一次（[pageEmitted]），且只取**单个**窗口的文本，
 *   绝不把多页内容拼在一起。
 *
 * 服务由系统在用户授权后绑定，**不能也不应**由应用主动创建或启动。
 */
@AndroidEntryPoint
class KaiAccessibilityService : AccessibilityService() {

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
     * 用户主开关的本地镜像。
     *
     * 事件回调在主线程且可能高频触发，不能每次都去读 DataStore（挂起函数）；
     * 这里缓存一份，由 [onServiceConnected] 里的订阅持续更新。
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
     * 把「当前这一页」变成一条类别信号。
     *
     * **一次只取一个窗口**：要么事件来源窗口，要么同包名里唯一一个像账单详情的窗口。
     * 绝不把多个窗口的文本拼成一条 —— 那会把两份账单 / 两个页面混在一起，
     * 金额与商户名对不上号（这是明确要避免的）。
     *
     * 判定标准是「**必须出现账单详情页专有字段**，且不含聊天 / 主界面 / 列表 / 账户页特征词」，
     * 具体规则集中在 [BillPageHeuristics]（抽成纯函数以便用真实页面文本写回归测试）——
     * 微信主界面 / 聊天 / 账单列表同样含「支付 / 账单」等词，早期按关键词收录时
     * 把聊天里的「1.6MB」抽成 ¥1.60、把支付宝「小荷包」的「总金额 564.18」记成一笔交易，
     * 都是真机复现过的假账。
     *
     * 出现两个以上「像账单详情」的窗口时**直接放弃**：宁可漏记，也不混淆。
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
            // 页面数字多（订单号 / 余额 / 优惠），必须用「标签邻近」规则抽；
            // 抽不到即 null，此时信号只能回填、不能建账 —— 宁可不建，也不建错账
            amountCents = SignalTextParser.extractAmountCents(text),
            tradeTimeMillis = SignalTextParser.extractTradeTimeMillis(text, clock),
            capturedAtMillis = clock.nowMillis()
        )
    }

    /**
     * 取「当前这一页」对应的**单个**窗口文本。
     *
     * 优先级：事件来源窗口 → 同包名里唯一像账单详情的窗口 → 活动窗口。
     * 出现多个像账单详情的窗口（多份账单并存）时返回 null，交由调用方放弃，绝不合并。
     */
    private fun billDetailText(packageName: String, windowId: Int): String? {
        val samePackage = runCatching {
            windows.filter { it.root?.packageName == packageName }
        }.getOrDefault(emptyList())

        // 1) 事件来源窗口（用户正在看的那一屏）
        samePackage.firstOrNull { it.id == windowId }
            ?.root
            ?.let { PageTextExtractor.extract(it) }
            ?.takeIf(BillPageHeuristics::isBillDetail)
            ?.let { return it }

        // 2) 同包名里唯一一个像账单详情的窗口（多个候选即放弃，避免两页混成一页）
        val details = samePackage.asSequence()
            .mapNotNull { it.root }
            .mapNotNull { PageTextExtractor.extract(it) }
            .filter(BillPageHeuristics::isBillDetail)
            .toList()
        details.singleOrNull()?.let { return it }

        // 3) 活动窗口兜底：个别窗口不出现在 [windows] 里
        val active = rootInActiveWindow
        if (active != null && active.packageName?.toString() == packageName) {
            PageTextExtractor.extract(active)?.takeIf(BillPageHeuristics::isBillDetail)?.let { return it }
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
