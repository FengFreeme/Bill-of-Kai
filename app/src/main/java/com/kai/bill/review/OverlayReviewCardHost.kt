package com.kai.bill.review

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import com.kai.bill.core.common.overlay.CaptureToast
import com.kai.bill.core.common.overlay.ReviewCardOverlay
import com.kai.bill.core.common.overlay.ReviewKind
import com.kai.bill.core.design.theme.AppTheme
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
import com.kai.bill.feature.review.ReviewCardContent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [ReviewCardOverlay] 与 [CaptureToast] 的实现 —— 都用**无障碍悬浮层**承载。
 *
 * 窗口类型固定为 `TYPE_ACCESSIBILITY_OVERLAY`：只要本应用的无障碍服务在运行，
 * 这个类型**不需要任何权限**就能盖在别的 App 之上。这正是商业产品
 * （小黑记账的「自动记账面板」）的做法 —— 它的悬浮窗权限处于 denied 状态，
 * 面板却照常显示。
 *
 * **两种窗口的能力刻意不同**（同一类里实现，只是复用那套 Compose-overlay 脚手架）：
 *
 * | | 确认卡片 | 轻提示 |
 * |---|---|---|
 * | 尺寸 | 全屏（带遮罩） | 底部一条 |
 * | 触摸 | **可点**（要按按钮） | **完全穿透**（`FLAG_NOT_TOUCHABLE`）—— 用户必须能继续操作原来的 App |
 * | 生命周期 | 用户处理完才消失 | 到点自动消失 |
 *
 * **为什么不再「打开编辑页」**：从悬浮层 `startActivity` 打开 App 属于**后台启动 Activity**，
 * 而 Android 10 起的 8 条官方豁免里**没有「无障碍服务」**，系统会**静默拦截**
 * （不抛异常、不留日志）。真机表现就是「点『改分类』没反应」。
 * 因此分类选择改为**在同一次悬浮里再滑出一张分类卡**，选中即落库，全程不离开用户当前页面。
 *
 * 数据由本类直接注入仓储获取，不引入 ViewModel —— 省掉在 WindowManager 上
 * 搭 `ViewModelStoreOwner` / `SavedStateRegistryOwner` 的一整套脚手架。
 */
@Singleton
class OverlayReviewCardHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val updateBillUseCase: UpdateBillUseCase,
    private val deleteBillUseCase: DeleteBillUseCase,
    private val clock: Clock,
    private val serviceHolder: AccessibilityServiceHolder,
    private val kaiPrefs: KaiPrefs
) : ReviewCardOverlay, CaptureToast {

    /** 悬浮层上的操作（改分类、撤销）都从主线程发起；DB 调用由 Room 自己切走 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 当前挂着的卡片；null 表示没有显示 */
    private var shownCard: ShownCard? = null

    /** 当前挂着的轻提示；同一时刻只留一条 */
    private var shownToast: ShownCard? = null

    /** 轻提示的自动消失计时；新提示会顶掉旧的 */
    private var toastJob: Job? = null

    /** 卡片内的错误提示。用 Compose 状态承载，改分类失败时卡面上会直接出现原因 */
    private val cardError = mutableStateOf<String?>(null)

    /**
     * 卡片当前展示的账单与分类名。
     *
     * 必须是 Compose 状态而不是普通字段：在卡片里改完分类后**不关闭卡片**，
     * 而是回到原来那张摘要卡并把新分类就地刷新出来。普通字段改了不会触发重组，
     * 用户看到的会是卡面纹丝不动、分类还是旧的那个。
     */
    private val cardBill = mutableStateOf<Bill?>(null)

    /** 卡片当前展示的分类名；与 [cardBill] 一起构成卡面的可刷新数据 */
    private val cardCategoryName = mutableStateOf("")

    override suspend fun show(billId: Long, kind: ReviewKind): Boolean {
        val bill = billRepository.getById(billId) ?: return false
        return show(bill, kind)
    }

    /** 直接把内存里的账单挂上卡片；[showLatest] 用它免掉一次多余的查库 */
    private suspend fun show(bill: Bill, kind: ReviewKind): Boolean {
        val categoryName = categoryRepository.getById(bill.categoryId)?.name ?: "未分类"
        // 只给「同一方向」的分类，并组装成两级树：分类卡直接用应用内的 CategoryPicker 渲染
        val tree = categoryRepository.observeByType(bill.type).first().toCategoryTree()
        return withContext(Dispatchers.Main.immediate) { attach(bill, categoryName, kind, tree) }
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
            recordDelivery(DELIVERY_NO_BILL)
            return false
        }
        // 测试入口不区分场景，按「补记一笔」的文案展示即可
        return show(latest, ReviewKind.CREATED)
    }

    override fun dismiss() {
        val card = shownCard ?: return
        shownCard = null
        runCatching { card.windowManager.removeViewImmediate(card.view) }
        card.owner.destroy()
    }

    /**
     * 显示一条短暂提示（[CaptureToast]）。
     *
     * 窗口**不抢焦点、不拦截触摸**：提示飘在别人的页面上，用户必须还能继续点自己的 App。
     */
    override suspend fun show(message: String) {
        withContext(Dispatchers.Main.immediate) { attachToast(message) }
    }

    // ---------------------------------------------------------------- 确认卡片

    /**
     * 构建并挂上悬浮卡片。
     *
     * @return true 表示已成功显示
     */
    private fun attach(bill: Bill, categoryName: String, kind: ReviewKind, tree: List<CategoryNode>): Boolean {
        // 同一时刻只允许一张：先撤掉旧的，避免叠出多层遮罩
        dismiss()
        cardError.value = null
        cardBill.value = bill
        cardCategoryName.value = categoryName

        // 必须用**无障碍服务自己的上下文**取 WindowManager：
        // Application 上下文的窗口 token 是 null，addView 会直接抛
        // BadTokenException「token null is not valid」（真机验证过一次）。
        val serviceContext = serviceHolder.serviceContext
        if (serviceContext == null) {
            recordDelivery(DELIVERY_NO_SERVICE)
            return false
        }

        val windowManager = serviceContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            recordDelivery(DELIVERY_NO_WINDOW_MANAGER)
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
                    // 从状态里读、而不是闭包捕获：改完分类只刷新这两个值，卡片就地更新而不必重建
                    val shownBill = cardBill.value ?: return@BillOfKaiTheme
                    ReviewCardContent(
                        bill = shownBill,
                        categoryName = cardCategoryName.value,
                        kind = kind,
                        tree = tree,
                        errorMessage = cardError.value,
                        onDismiss = { dismiss() },
                        onCategoryPicked = ::applyCategory,
                        onRevoke = { revoke(shownBill.id) }
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

        return runCatching {
            owner.attach()
            windowManager.addView(view, params)
            shownCard = ShownCard(windowManager, view, owner)
            recordDelivery(DELIVERY_SHOWN)
            true
        }.getOrElse { error ->
            owner.destroy()
            // **把失败原因留下来**。这一步是整个功能最容易静默失败的地方：
            // addView 抛异常时用户毫无感知，系统也只在 logcat 留一行。
            // 不留原因，就只能靠反复付真钱去猜 —— 本项目已经这么吃过两次亏。
            recordDelivery("$DELIVERY_FAILED_PREFIX${error.message.orEmpty()}")
            false
        }
    }

    /**
     * 在悬浮卡片里直接改分类。
     *
     * 顺序不能乱：
     * 1. 落库（**不启动 Activity**：后台启动会被系统静默拦截，用户全程留在原来的页面上）；
     * 2. **不关闭卡片**，而是回到原来那张摘要卡、把新分类就地刷新出来 ——
     *    用户点「确认」的意思是「就要这个分类」，不是「把卡片关掉」；
     *    直接消失会让他无从确认到底改没改成；
     * 3. 失败则留在卡面上写明原因，用户可以立刻再选一次。
     */
    private fun applyCategory(categoryId: Long) {
        val bill = cardBill.value ?: return
        scope.launch {
            val ok = runCatching {
                updateBillUseCase(
                    UpdateBillUseCase.Params(
                        snapshot = bill,
                        amountCents = bill.amountCents,
                        type = bill.type,
                        categoryId = categoryId,
                        accountId = bill.accountId,
                        note = bill.note,
                        countInStats = bill.countInStats,
                        tradeTimeMillis = bill.tradeTimeMillis
                    )
                )
            }.isSuccess

            if (!ok) {
                // 失败不关卡片：卡面上给出原因，用户可以直接再选一次
                cardError.value = "分类没改成，请再试一次"
                recordDelivery(DELIVERY_ENRICH_FAILED)
                return@launch
            }

            // 就地刷新卡面：分类行变成新分类，**卡片继续留着**等用户看完再自己关
            runCatching { categoryRepository.getById(categoryId)?.name }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { cardCategoryName.value = it }
            // 同步账单本身：再点一次「改分类」时，选择器才会高亮刚选中的那一个
            cardBill.value = bill.copy(categoryId = categoryId)
            // 刻意不弹轻提示：卡片本身已经把新分类显示出来了，再叠一条提示反而是干扰
        }
    }

    private fun revoke(billId: Long) {
        scope.launch {
            val ok = runCatching { deleteBillUseCase(billId) }.isSuccess
            dismiss()
            // 撤销也要有反馈：以前删掉就没了动静，用户无法确认到底撤没撤
            attachToast(if (ok) "已撤销这一笔" else "撤销没成功，请到 App 里试")
        }
    }

    // ---------------------------------------------------------------- 轻提示

    /** 挂上底部轻提示；不抢焦点、完全穿透触摸，到点自己消失 */
    private fun attachToast(message: String) {
        dismissToast()

        val serviceContext = serviceHolder.serviceContext ?: return
        val windowManager = serviceContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        val owner = OverlayViewOwner()
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                BillOfKaiTheme {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = message,
                            style = AppTheme.typography.bodyMedium,
                            color = AppTheme.color.onSurface,
                            modifier = Modifier
                                .background(
                                    color = AppTheme.color.surface,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // ⚠️ NOT_TOUCHABLE 是必须的：提示只是「告知」，绝不能挡住用户在原来 App 上的操作
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            // 抬高一点，避免贴着导航栏或手势条
            y = TOAST_BOTTOM_MARGIN_PX
        }

        val attached = runCatching {
            owner.attach()
            windowManager.addView(view, params)
            shownToast = ShownCard(windowManager, view, owner)
            true
        }.getOrElse {
            owner.destroy()
            false
        }
        if (!attached) return

        toastJob = scope.launch {
            delay(TOAST_DURATION_MS)
            dismissToast()
        }
    }

    private fun dismissToast() {
        toastJob?.cancel()
        toastJob = null
        val toast = shownToast ?: return
        shownToast = null
        runCatching { toast.windowManager.removeViewImmediate(toast.view) }
        toast.owner.destroy()
    }

    /** 把投递结果写进诊断；写入本身绝不能影响卡片逻辑，因此吞掉一切异常 */
    private fun recordDelivery(value: String) {
        scope.launch { runCatching { kaiPrefs.recordCardDelivery(value) } }
    }

    /** 已挂上的悬浮窗口：窗口、视图与它依赖的生命周期宿主 */
    private data class ShownCard(
        val windowManager: WindowManager,
        val view: ComposeView,
        val owner: OverlayViewOwner
    )

    private companion object {
        const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val LOOKBACK_MILLIS = 365 * ONE_DAY_MILLIS

        /** 轻提示停留时长：够看清一句话，又不至于挡路 */
        const val TOAST_DURATION_MS = 2_200L

        /** 轻提示距屏幕底部的距离（px），避开导航栏 / 手势条 */
        const val TOAST_BOTTOM_MARGIN_PX = 160

        /** 投递结果：已显示在屏幕上 */
        const val DELIVERY_SHOWN = "DELIVERED"

        /** 投递结果：无障碍服务没在跑，无法取得有效的窗口 token */
        const val DELIVERY_NO_SERVICE = "NO_SERVICE"

        /** 投递结果：一笔账单都没有，没东西可展示 */
        const val DELIVERY_NO_BILL = "NO_BILL"

        /** 投递结果：取不到 WindowManager */
        const val DELIVERY_NO_WINDOW_MANAGER = "NO_WINDOW_MANAGER"

        /** 投递结果：卡片内改分类失败 */
        const val DELIVERY_ENRICH_FAILED = "ENRICH_FAILED"

        /** 投递结果前缀：addView 抛异常，后面跟异常信息 */
        const val DELIVERY_FAILED_PREFIX = "FAILED: "
    }
}

/**
 * 悬浮层里 Compose 需要的宿主。
 *
 * ⚠️ **三个接口一个都不能少**：这个版本的 Compose 在 `AbstractComposeView` 挂载时会
 * 硬性校验 `ViewTreeSavedStateRegistryOwner`，缺了就直接抛
 * `IllegalStateException: Composed into the View which doesn't propagate ViewTreeSavedStateRegistryOwner`
 * （真机踩过一次）。卡片本身不取 ViewModel、也没有需要保存的状态，
 * 但**框架要的是这几个宿主存在，而不是它们被用到** —— 少一个就会崩。
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
        // 直接推到 RESUMED：悬浮卡片一挂上就该立刻绘制，没有「可见但未聚焦」的中间态
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
