package com.kai.bill.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 */
@HiltViewModel
class ReviewCardViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val deleteBillUseCase: DeleteBillUseCase,
    private val updateBillUseCase: UpdateBillUseCase
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
    fun load(billId: Long) {
        if (billId <= 0L || billId == loadedBillId) return
        loadedBillId = billId

        viewModelScope.launch {
            val bill = billRepository.getById(billId)
            if (bill == null) {
                // 账单已被删除（比如用户从别处撤了）——没有可展示的内容，直接关掉卡片
                _state.update { it.copy(closed = true) }
                return@launch
            }
            val categoryName = categoryRepository.getById(bill.categoryId)?.name ?: "未分类"
            // 分类树按账单类型取：支出 / 收入 / 转账各一套，卡片内的选择器要展示对应的那套
            val tree = runCatching {
                categoryRepository.observeByType(bill.type).first().toCategoryTree()
            }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    bill = bill,
                    categoryName = categoryName,
                    categoryTree = tree,
                    closed = false
                )
            }
        }
    }

    /**
     * 在卡片里改分类。
     *
     * 只覆盖分类：其余字段全部沿用原账单快照 —— 卡片是「当场修正分类」的入口，
     * 不该顺手改动用户没碰过的金额 / 账户 / 备注（[UpdateBillUseCase] 正是按这个约定写的）。
     *
     * 失败不关卡片、也不提示：卡片是短命的，写失败时保持原样比弹一个转瞬即逝的错
     * 更容易被理解；真正的一致性由仓储保证。
     */
    fun changeCategory(categoryId: Long) {
        val bill = _state.value.bill ?: return
        if (categoryId == bill.categoryId) return

        viewModelScope.launch {
            val saved = runCatching {
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
            }.isSuccess
            if (!saved) return@launch

            val name = categoryRepository.getById(categoryId)?.name
            _state.update { state ->
                state.copy(
                    bill = state.bill?.copy(categoryId = categoryId),
                    categoryName = name ?: state.categoryName
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
}

/**
 * @property bill 待展示的账单；null 表示尚未载入完成
 * @property categoryName 已解析好的分类名，UI 不再自己查仓储
 * @property categoryTree 当前账单类型下的两级分类，供卡片内弹出的选择器使用
 * @property closed true 表示应当关闭卡片（账单不存在或已被撤销）
 */
data class ReviewCardUiState(
    val bill: Bill? = null,
    val categoryName: String = "",
    val categoryTree: List<CategoryNode> = emptyList(),
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
