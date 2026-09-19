package com.kai.bill.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.bill.core.common.overlay.ReviewKind
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.CategoryNode
import com.kai.bill.domain.model.SourceType
import com.kai.bill.domain.model.toCategoryTree
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.CategoryRepository
import com.kai.bill.domain.usecase.bill.DeleteBillUseCase
import com.kai.bill.domain.usecase.bill.UpdateBillUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 确认卡片 ViewModel。
 *
 * 用**一次性读取**而非观察流：卡片是短命的（弹出来几秒、用户点一下就走），
 * 展示的就是「刚刚记下的那一笔」，不存在被别的入口改动的窗口期。
 * 少一层 Flow 生命周期管理，行为也更可预测。
 *
 * 分类的选择与落库都在这里完成 —— 卡片**不跳转编辑页**：
 * 从悬浮层 `startActivity` 打开 App 属于后台启动 Activity，系统会静默拦截，
 * 用户看到的就是「点了没反应」（真机复现过）。
 */
@HiltViewModel
class ReviewCardViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val updateBillUseCase: UpdateBillUseCase,
    private val deleteBillUseCase: DeleteBillUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewCardUiState())
    val state = _state.asStateFlow()

    private var loadedBillId = 0L

    /**
     * 载入要展示的账单。
     *
     * 重复传入同一个 id 直接忽略：卡片是 `singleTop`，第二次拉起会走 `onNewIntent`
     * 再触发一次载入，不去重就会白查一遍库。
     */
    fun load(billId: Long, kind: ReviewKind) {
        if (billId <= 0L || billId == loadedBillId) return
        loadedBillId = billId

        viewModelScope.launch {
            val bill = billRepository.getById(billId)
            if (bill == null) {
                // 账单已被删除（比如用户从别处撤了）——没有可展示的内容，直接关掉卡片
                _state.update { it.copy(closed = true) }
                return@launch
            }
            _state.update {
                it.copy(
                    bill = bill,
                    kind = kind,
                    categoryName = categoryNameOf(bill.categoryId),
                    // 只给「同一方向」的分类，并组装成两级树：分类卡直接用应用内的 CategoryPicker 渲染
                    tree = categoryRepository.observeByType(bill.type).first().toCategoryTree(),
                    errorMessage = null,
                    closed = false
                )
            }
        }
    }

    /**
     * 在卡片里改分类：直接落库，全程不离开当前 App。
     *
     * 失败不关闭卡片，而是在卡面上给出原因 —— 默默关掉会让用户以为改成功了。
     */
    fun pickCategory(categoryId: Long) {
        val bill = _state.value.bill ?: return
        if (categoryId <= 0L || categoryId == bill.categoryId) return

        viewModelScope.launch {
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

            _state.update {
                it.copy(
                    bill = if (ok) bill.copy(categoryId = categoryId) else bill,
                    categoryName = if (ok) categoryNameOf(categoryId) else it.categoryName,
                    errorMessage = if (ok) null else "分类没改成，请到 App 里再试一次"
                )
            }
        }
    }

    /** 撤销这一笔：删除账单并关闭卡片。删除失败也关闭——留着卡片只会让用户以为没生效。 */
    fun revoke() {
        val bill = _state.value.bill ?: return
        viewModelScope.launch {
            runCatching { deleteBillUseCase(bill.id) }
            _state.update { it.copy(closed = true) }
        }
    }

    private suspend fun categoryNameOf(categoryId: Long): String =
        categoryRepository.getById(categoryId)?.name ?: "未分类"
}

/**
 * @property bill 待展示的账单；null 表示尚未载入完成
 * @property kind 「新建一笔」还是「补分类」—— 决定标题与说明
 * @property categoryName 已解析好的分类名，UI 不再自己查仓储
 * @property tree 可选的分类（两级结构，已按账单方向过滤）
 * @property errorMessage 上一次操作失败的提示；null 表示正常
 * @property closed true 表示应当关闭卡片（账单不存在或已被撤销）
 */
data class ReviewCardUiState(
    val bill: Bill? = null,
    val kind: ReviewKind = ReviewKind.CREATED,
    val categoryName: String = "",
    val tree: List<CategoryNode> = emptyList(),
    val errorMessage: String? = null,
    val closed: Boolean = false
)

/**
 * 金额与方向的展示文本。
 *
 * 支出带 `-`、收入带 `+`、转账不带符号（它既不是支出也不是收入，
 * 加任何符号都会强化「这是一笔花销 / 收入」的误读）。
 */
internal fun Bill.signedAmountText(): String {
    val amount = "¥%.2f".format(amountCents / 100.0)
    return when (type) {
        BillType.EXPENSE -> "-$amount"
        BillType.INCOME -> "+$amount"
        BillType.TRANSFER -> amount
    }
}

/** 来源展示文案；与首页账单卡保持一致的命名，避免同一个来源在两处叫不同名字 */
internal fun SourceType.reviewSourceLabel(): String = when (this) {
    SourceType.NOTIFICATION -> "通知"
    SourceType.SMS -> "短信"
    SourceType.MANUAL -> "手动"
    SourceType.ACCESSIBILITY -> "分类识别"
    SourceType.SCREENSHOT -> "截图识别"
}
