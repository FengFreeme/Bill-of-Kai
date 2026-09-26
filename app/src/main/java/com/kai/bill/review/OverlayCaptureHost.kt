package com.kai.bill.review

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kai.bill.core.common.overlay.CaptureHint
import com.kai.bill.core.common.overlay.CaptureHintOverlay
import com.kai.bill.core.common.overlay.ReviewCardOverlay
import com.kai.bill.core.common.overlay.ReviewCardReason
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.prefs.KaiPrefs
import com.kai.bill.data.capture.accessibility.AccessibilityServiceHolder
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.CategoryNode
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.model.toCategoryTree
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.CategoryRepository
import com.kai.bill.domain.time.Clock
import com.kai.bill.domain.usecase.bill.DeleteBillUseCase
import com.kai.bill.domain.usecase.bill.UpdateBillUseCase
import com.kai.bill.feature.review.CaptureHintPill
import com.kai.bill.feature.review.ReviewCardContent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 无障碍**悬浮反馈**的统一宿主 —— 承载两种内容：
 *
 * | 内容 | 触发结果 | 尺寸与触摸 |
 * |---|---|---|
 * | 确认卡片（[ReviewCardOverlay]） | 新建 / 补分类 | 整屏遮罩 + 底部卡片，**可触摸** |
 * | 提示条（[CaptureHintOverlay]） | 已记录 / 待确认 | `WRAP_CONTENT` 胶囊，**不可触摸** |
 *
 * 两者共用一个宿主：容易踩坑的是窗口脚手架本身（`BadTokenException`、Compose 的宿主校验、
 * `addView` 静默失败），复制一份迟早会漂移；差异只在各自的 `params` 里。
 *
 * 窗口类型固定 `TYPE_ACCESSIBILITY_OVERLAY` —— 只要本应用的无障碍服务在运行，它**不需要任何权限**
 * 就能盖在别的 App 上。别退回「后台拉半透明 Activity」：那条路会被系统静默拦截，不抛异常也不留日志
 * （详见 `BillReviewNotifier` 的类注释）。
 *
 * 数据直接注入仓储获取，不引入 ViewModel：省掉在 WindowManager 上搭
 * `ViewModelStoreOwner` / `SavedStateRegistryOwner` 的一整套脚手架。
 */
@Singleton
class OverlayCaptureHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val deleteBillUseCase: DeleteBillUseCase,
    private val updateBillUseCase: UpdateBillUseCase,
    private val clock: Clock,
    private val serviceHolder: AccessibilityServiceHolder,
    private val kaiPrefs: KaiPrefs
) : ReviewCardOverlay, CaptureHintOverlay {

    /** 悬浮内容上的操作都从主线程发起；DB 调用由 Room 自己切走 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 当前挂着的内容；null 表示没有显示 */
    private var shown: ShownWindow? = null

    /** 当前挂着的是哪一类内容 —— 决定新内容能不能顶掉它（卡片优先） */
    private var shownKind: ShownKind? = null

    /** 上一条提示的指纹与时刻，用于「同一条提示短时间内不重复弹」 */
    private var lastHintSignature: String? = null
    private var lastHintAtMillis: Long = 0L

    // ---------------- 确认卡片 ----------------

    override suspend fun show(billId: Long, reason: ReviewCardReason): Boolean {
        val bill = billRepository.getById(billId) ?: return false
        return show(bill, reason)
    }

    /** 直接把内存里的账单挂上卡片；[showLatest] 用它免掉一次多余的查库 */
    private suspend fun show(bill: Bill, reason: ReviewCardReason): Boolean {
        val categoryName = categoryRepository.getById(bill.categoryId)?.name ?: "未分类"
        // 分类树按账单类型取：卡片里那个「改分类」选择器要用它渲染两级网格。
        // 这里离 UI 很近，取不到就让选择器空着，绝不能因此把整张卡片挡掉。
        val tree = runCatching {
            categoryRepository.observeByType(bill.type).first().toCategoryTree()
        }.getOrDefault(emptyList())
        return withContext(Dispatchers.Main.immediate) {
            attachCard(bill, reason, categoryName, tree)
        }
    }

    override suspend fun showLatest(): Boolean {
        val now = clock.nowMillis()
        // 用一年窗口而不是「最近几天」：测试入口的目的是验证卡片能不能显示，
        // 而自动记账产生的账单可能带着较早的交易时间（页面里抽到的日期），窗口太窄会误判成「没有账单」。
        val latest = billRepository
            .observeBills(DateRange(now - LOOKBACK_MILLIS, now + ONE_DAY_MILLIS), null)
            .first()
            .firstOrNull()
        if (latest == null) {
            recordCardDelivery(DELIVERY_NO_BILL)
            return false
        }
        return show(latest, ReviewCardReason.CREATED)
    }

    /**
     * 构建并挂上确认卡片。
     *
     * @return true 表示已成功显示
     */
    private fun attachCard(
        bill: Bill,
        reason: ReviewCardReason,
        categoryName: String,
        categoryTree: List<CategoryNode>
    ): Boolean {
        // 同一时刻只允许一张：先撤掉旧的（可能是上一条提示条），避免叠出多层
        dismiss()

        val windowManager = serviceWindowManager() ?: run {
            recordCardDelivery(DELIVERY_NO_SERVICE)
            return false
        }

        val owner = OverlayViewOwner()
        // 三个宿主必须在 addView 之前全部装上：Compose 在 onAttachedToWindow 就建立组合，
        // 缺任何一个都会直接抛 IllegalStateException
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                BillOfKaiTheme {
                    ReviewCardContent(
                        bill = bill,
                        reason = reason,
                        categoryName = categoryName,
                        categoryTree = categoryTree,
                        onDismiss = { dismiss() },
                        // 就地改分类：不再跳进 App 的编辑页，避免打断用户当前在做的事
                        onSelectCategory = { categoryId -> changeCategory(bill, categoryId) },
                        onRevoke = { revoke(bill.id) }
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // 需要可触摸（用户要点按钮），但不能抢焦点、不能盖住输入法
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        return addWindow(windowManager, owner, view, params, ShownKind.CARD) {
            recordCardDelivery(DELIVERY_SHOWN)
        }.onFailure { error ->
            recordCardDelivery("$DELIVERY_FAILED_PREFIX${error.message.orEmpty()}")
        }.isSuccess
    }

    // ---------------- 提示条 ----------------

    override suspend fun show(hint: CaptureHint): Boolean =
        // 展示与让位判定都在主线程做：`shown` / `shownKind` 只有主线程会写，
        // 在别处读会与 attachHint 抢同一份状态（提示条本身是从 Default 线程发起的）
        withContext(Dispatchers.Main.immediate) {
            // 正显示确认卡片时**不打扰**：卡片优先级更高（用户可能正在改分类），
            // 顶掉它会让人白操作一次；提示条本身只是「告知」，错过也无妨。
            if (isLiveCardShowing()) {
                recordHintDelivery(HINT_SKIPPED_CARD)
                return@withContext false
            }

            val signature = hint.signature()
            if (!shouldShowHint(signature)) {
                recordHintDelivery(HINT_SKIPPED_DUPLICATE)
                return@withContext false
            }

            attachHint(hint)
        }

    /**
     * 当前是否真有**活着的**确认卡片在显示；僵尸卡片顺手清掉。
     *
     * NOTE: 卡片窗口挂在无障碍服务的窗口 token 上，服务被杀/重启时窗口随之消失，
     * 而 [shownKind] 只在 [dismiss] 里清 —— 状态会永久停在 `CARD`，此后每条提示条
     * 都被判成「让位给卡片」而弹不出来（真机表现：有时有、之后再也没有）。
     * 判活用「服务上下文还在」+「视图仍挂在窗口上」：任一不成立，用户就看不到那张卡片。
     */
    private fun isLiveCardShowing(): Boolean {
        if (shownKind != ShownKind.CARD) return false
        val window = shown
        val alive = serviceHolder.serviceContext != null &&
            window?.view?.isAttachedToWindow == true
        if (!alive) dismiss()
        return alive
    }

    /**
     * 挂上提示条，并在若干秒后自动淡出、移除。
     *
     * @return true 表示已成功显示
     */
    private fun attachHint(hint: CaptureHint): Boolean {
        dismiss()

        val windowManager = serviceWindowManager() ?: run {
            recordHintDelivery(DELIVERY_NO_SERVICE)
            return false
        }

        val owner = OverlayViewOwner()
        // 淡出用一个可观察状态驱动，而不是直接 removeView：
        // 从「显示」到「消失」之间需要一段过渡，否则胶囊是硬生生被抹掉的
        val visible: MutableState<Boolean> = mutableStateOf(false)
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                BillOfKaiTheme {
                    // 位置交给 Compose 而不是 `WindowManager.gravity + y`：
                    // 后者在 BOTTOM 重力下 y 的正负方向极易搞反，真机上的表现就是
                    // 提示条直接贴在屏幕下缘。这里「整屏透明 + 底部对齐 + 内边距」
                    // 语义明确，也不会因不同 ROM 的坐标解释而漂移。
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        AnimatedVisibility(
                            visible = visible.value,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            CaptureHintPill(
                                hint = hint,
                                modifier = Modifier.padding(bottom = PillBottomMargin)
                            )
                        }
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // **FLAG_NOT_TOUCHABLE 是这里的命门**：提示条只是告知，绝不能吃掉触摸 ——
            // 用户看到提示的同一时刻往往要正要点底层页面的按钮。
            // 有了它，窗口铺满整屏也不会拦住任何操作，位置就能放心交给 Compose。
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val result = addWindow(windowManager, owner, view, params, ShownKind.HINT) {
            visible.value = true
            recordHintDelivery(DELIVERY_SHOWN)
        }.onFailure { error ->
            recordHintDelivery("$DELIVERY_FAILED_PREFIX${error.message.orEmpty()}")
        }
        if (result.isFailure) return false

        // 自动消失：到点先淡出，再移除窗口。捕获本次窗口实例，
        // 避免把后来顶上的那条提示一起关掉
        val mine = shown
        scope.launch {
            delay(HINT_VISIBLE_MILLIS)
            visible.value = false
            delay(HINT_FADE_MILLIS)
            if (shown === mine) dismiss()
        }
        return true
    }

    // ---------------- 公共部分 ----------------

    /**
     * 移除当前悬浮内容；未显示时是空操作（幂等）。
     *
     * 同时服务于「卡片关闭」与「提示条超时」，因此不区分种类 —— 同一时刻只会有一个。
     */
    override fun dismiss() {
        val window = shown ?: return
        shown = null
        shownKind = null
        runCatching { window.windowManager.removeViewImmediate(window.view) }
        window.owner.destroy()
    }

    /**
     * 取**无障碍服务自己的** WindowManager。
     *
     * Application 上下文的窗口 token 是 null，`addView` 会直接抛
     * `BadTokenException: token null is not valid`（真机验证过一次），
     * 因此这里只能借用服务上下文；服务没在跑就返回 null，由调用方记诊断。
     */
    private fun serviceWindowManager(): WindowManager? {
        val serviceContext = serviceHolder.serviceContext ?: return null
        return serviceContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }

    /**
     * 把窗口挂上去并登记；失败**不抛异常**，交给调用方记诊断。
     *
     * `addView` 抛异常时用户毫无感知（系统也只在 logcat 留一行），
     * 因此这一步的失败原因必须留下来 —— 本项目已为此吃过两次亏。
     */
    private inline fun addWindow(
        windowManager: WindowManager,
        owner: OverlayViewOwner,
        view: ComposeView,
        params: WindowManager.LayoutParams,
        kind: ShownKind,
        onShown: () -> Unit
    ): Result<Unit> = runCatching {
        owner.attach()
        windowManager.addView(view, params)
        shown = ShownWindow(windowManager, view, owner)
        shownKind = kind
        onShown()
    }.onFailure {
        owner.destroy()
    }

    /**
     * 同一条提示短时间内不重复弹。
     *
     * 无障碍服务会**反复**读到同一个页面（页面重绘、窗口切换都会再报一次），
     * 没有这道闸，用户会看到同一条胶囊反复闪。
     */
    private fun shouldShowHint(signature: String): Boolean {
        val now = clock.nowMillis()
        val repeated = signature == lastHintSignature && now - lastHintAtMillis < HINT_DEDUP_MILLIS
        lastHintSignature = signature
        lastHintAtMillis = now
        return !repeated
    }

    /** 把卡片投递结果写进诊断；写入本身绝不能影响卡片逻辑，因此吞掉一切异常 */
    private fun recordCardDelivery(value: String) {
        scope.launch { runCatching { kaiPrefs.recordCardDelivery(value) } }
    }

    /** 把提示条投递结果写进诊断；理由同上 */
    private fun recordHintDelivery(value: String) {
        scope.launch { runCatching { kaiPrefs.recordHintDelivery(value) } }
    }

    private fun revoke(billId: Long) {
        scope.launch {
            runCatching { deleteBillUseCase(billId) }
            dismiss()
        }
    }

    /**
     * 就地改分类：只覆盖 `categoryId`，其余字段沿用传进来的账单快照。
     *
     * 与「撤销」一样只写库、不重新挂窗口 —— 卡片上的分类名由 UI 自己乐观更新
     * （它手里就有分类树），省掉「写库 → 回读 → 重挂悬浮窗」那一圈，也就不会闪。
     */
    private fun changeCategory(bill: Bill, categoryId: Long) {
        if (categoryId == bill.categoryId) return
        scope.launch {
            runCatching {
                updateBillUseCase(
                    UpdateBillUseCase.Params(
                        snapshot = bill,
                        amountCents = bill.amountCents,
                        type = bill.type,
                        categoryId = categoryId,
                        accountId = bill.accountId,
                        note = bill.note,
                        countInStats = bill.countInStats
                    )
                )
            }
        }
    }

    /** 当前悬浮内容的种类 */
    private enum class ShownKind { CARD, HINT }

    /** 已挂上的悬浮内容：窗口、视图与它依赖的生命周期宿主 */
    private data class ShownWindow(
        val windowManager: WindowManager,
        val view: ComposeView,
        val owner: OverlayViewOwner
    )

    private companion object {
        const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val LOOKBACK_MILLIS = 365 * ONE_DAY_MILLIS

        /** 投递结果：已显示在屏幕上 */
        const val DELIVERY_SHOWN = "DELIVERED"

        /** 投递结果：无障碍服务没在跑，无法取得有效的窗口 token */
        const val DELIVERY_NO_SERVICE = "NO_SERVICE"

        /** 投递结果：一笔账单都没有，没东西可展示 */
        const val DELIVERY_NO_BILL = "NO_BILL"

        /** 投递结果前缀：addView 抛异常，后面跟异常信息 */
        const val DELIVERY_FAILED_PREFIX = "FAILED: "

        /** 提示条被跳过：此刻正显示确认卡片 */
        const val HINT_SKIPPED_CARD = "SKIPPED_CARD"

        /** 提示条被跳过：与上一条相同且间隔太近 */
        const val HINT_SKIPPED_DUPLICATE = "SKIPPED_DUPLICATE"

        /** 提示条停留时长；够看清一句话，又不至于挡着用户的下一步操作 */
        const val HINT_VISIBLE_MILLIS = 2_600L

        /** 淡出时长；与 Compose 侧的 fadeOut 默认时长一致 */
        const val HINT_FADE_MILLIS = 240L

        /** 同一条提示的最小间隔 */
        const val HINT_DEDUP_MILLIS = 10_000L
    }
}

/**
 * 提示条与屏幕下缘的距离。
 *
 * 取 112dp：既明显离开边缘，又不会被误认成系统手势条 / 三方的底部导航；
 * 同时高于确认卡片所在的底部区域，两者不会挤在一起。
 */
private val PillBottomMargin = 112.dp

/**
 * 提示的去重指纹：判断「是不是同一件事」。以 [CaptureHint.key] 为主，取不到才退回 `kind + amount`
 * —— 后者不能当主：`ALREADY_RECORDED` 按设计不带金额，那样每笔重复账单的指纹都相同，
 * 10 秒内切换两笔不同账单时第二笔会被误判成重复而不再提示（真机反馈过）。
 */
private fun CaptureHint.signature(): String = "$kind:${key ?: amountCents}"

/**
 * 悬浮层里 Compose 需要的宿主。
 *
 * NOTE: **三个接口一个都不能少**：这个版本的 Compose 在 `AbstractComposeView` 挂载时硬性校验
 * `ViewTreeSavedStateRegistryOwner`，缺了就直接抛 `IllegalStateException`（真机踩过一次）。
 * 卡片与提示条都不取 ViewModel、也没有要保存的状态，但**框架要的是这几个宿主存在，而不是被用到**。
 */
private class OverlayViewOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    /** 必须在 `addView` 之前调用：Compose 在 `onAttachedToWindow` 就建立组合 */
    fun attach() {
        savedStateController.performRestore(null)
        // 直接推到 RESUMED：悬浮内容一挂上就该立刻绘制，没有「可见但未聚焦」的中间态
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
