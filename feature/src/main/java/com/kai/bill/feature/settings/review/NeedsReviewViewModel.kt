package com.kai.bill.feature.settings.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.PendingBill
import com.kai.bill.domain.model.RefundCategory
import com.kai.bill.domain.repository.AccountRepository
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.CategoryRepository
import com.kai.bill.domain.repository.PendingBillRepository
import com.kai.bill.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 待确认页 ViewModel。
 *
 * 三个动作，语义完全不同：
 * - **接受建议**：按 `suggested*` 落成正式账单，然后删掉待确认行；
 * - **改一下**：同样先按建议落库拿到 `billId`，再跳到「记一笔」编辑页让用户改
 *   （复用现有编辑路由，不为预填新增路由参数）；
 * - **不要这笔**：只删行，不产生任何记录。
 *
 * 落库时**复用待确认记录的 `dedupHash`**：万一这笔此前已经落过账（例如词表更新后重新解析过），
 * `bill` 表的唯一索引会拦住重复插入并返回 -1 —— 那同样是「已处理」，照常删行，不让它永远卡在列表里。
 *
 * @property billRepository 接受建议 / 改一下都要落库
 * @property categoryRepository 分类仓储，只为把建议里的分类 id 显示成名字
 * @property accountRepository 账户仓储，同上
 * @property clock 时间源（落库时的记录时间）
 */
@HiltViewModel
class NeedsReviewViewModel @Inject constructor(
    private val pendingRepository: PendingBillRepository,
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val clock: Clock
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val editBillId = MutableStateFlow<Long?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    /**
     * 分类名 / 账户名两个映射合流。
     *
     * 单独一层是因为 `combine` 的强类型重载最多 5 个流，外层已有 5 个（见 [uiState]）——
     * 「记一笔」页（`RecordViewModel`）出于同样原因做了同样的分层。
     */
    private val nameMaps = combine(
        categoryRepository.observeAll(),
        accountRepository.observeAll()
    ) { categories, accounts ->
        categories.associate { it.id to it.name } to accounts.associate { it.id to it.name }
    }

    val uiState: StateFlow<NeedsReviewUiState> = combine(
        pendingRepository.observeAll(),
        nameMaps,
        busy,
        editBillId,
        errorMessage
    ) { items, names, isBusy, editId, error ->
        NeedsReviewUiState(
            items = items,
            categoryNames = names.first,
            accountNames = names.second,
            busy = isBusy,
            editBillId = editId,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = NeedsReviewUiState()
    )

    // ---- 三个动作 ----

    /** 接受建议：落库 + 删行 */
    fun accept(item: PendingBill) {
        viewModelScope.launch {
            busy.value = true
            acceptInternal(item)
            busy.value = false
        }
    }

    /** 全部接受：只处理有建议的条目，没有建议的原地留着等用户单独决定 */
    fun acceptAll() {
        viewModelScope.launch {
            if (busy.value) return@launch
            busy.value = true
            runCatching {
                // 先取快照：acceptInternal 会逐条删行，边遍历边被 DB 流刷新会漏掉后面的
                pendingRepository.getAll()
                    .filter { it.hasSuggestion }
                    .forEach { acceptInternal(it) }
            }.onFailure { errorMessage.value = it.message ?: "批量处理失败" }
            busy.value = false
        }
    }

    /** 改一下：先落库拿到 billId，再由 UI 跳到编辑页 */
    fun edit(item: PendingBill) {
        viewModelScope.launch {
            val bill = item.toBill()
            if (bill == null) {
                errorMessage.value = "这条没有可用的建议，只能选「不要这笔」"
                return@launch
            }
            busy.value = true
            runCatching { billRepository.save(bill) }
                .onSuccess { savedId ->
                    pendingRepository.deleteById(item.id)
                    if (savedId > 0L) {
                        editBillId.value = savedId
                    } else {
                        errorMessage.value = "这笔已经在账单里了"
                    }
                }
                .onFailure { errorMessage.value = it.message ?: "保存失败" }
            busy.value = false
        }
    }

    /** 不要这笔：只删行，不产生任何记录 */
    fun discard(item: PendingBill) {
        viewModelScope.launch {
            runCatching { pendingRepository.deleteById(item.id) }
                .onFailure { errorMessage.value = it.message ?: "删除失败" }
        }
    }

    fun onEditNavigationHandled() {
        editBillId.value = null
    }

    fun clearError() {
        errorMessage.value = null
    }

    // ---- 内部 ----

    private suspend fun acceptInternal(item: PendingBill) {
        val bill = item.toBill() ?: run {
            errorMessage.value = "这条没有可用的建议，只能选「不要这笔」"
            return
        }
        runCatching { billRepository.save(bill) }
            .onFailure {
                errorMessage.value = it.message ?: "保存失败"
                return
            }
        pendingRepository.deleteById(item.id)
    }

    /**
     * 待确认记录 → 正式账单。
     *
     * 建议不完整（缺方向或分类）时返回 null：这种条目落不了库，调用方应走「不要这笔」。
     */
    private fun PendingBill.toBill(): Bill? {
        val type = suggestedType ?: return null
        val categoryId = suggestedCategoryId ?: return null
        val now = clock.nowMillis()
        return Bill(
            amountCents = amountCents,
            type = type,
            countInStats = suggestedCountInStats,
            // 与手动记账同一条派生规则：收入 + 退款分类即退款（见 `RecordBillUseCase`）
            isRefund = type == BillType.INCOME && categoryId == RefundCategory.ID,
            categoryId = categoryId,
            accountId = suggestedAccountId,
            merchant = null,
            note = null,
            tradeTimeMillis = tradeTimeMillis,
            source = source,
            rawText = rawText,
            dedupHash = dedupHash,
            createdAt = now,
            updatedAt = now
        )
    }
}
