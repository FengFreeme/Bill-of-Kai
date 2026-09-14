package com.kai.bill.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.bill.domain.calculator.MoneyText
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.toCategoryTree
import com.kai.bill.domain.repository.AccountRepository
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.CategoryRepository
import com.kai.bill.domain.time.Clock
import com.kai.bill.domain.usecase.bill.DeleteBillUseCase
import com.kai.bill.domain.usecase.bill.RecordBillUseCase
import com.kai.bill.domain.usecase.bill.UpdateBillUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * 记一笔 ViewModel。
 *
 * 负责金额键盘输入、分类 / 账户选择、交易日期选择、保存（新增走 [RecordBillUseCase]、
 * 编辑走 [UpdateBillUseCase]）与删除（[DeleteBillUseCase]）。编辑态通过 [load] 传入
 * [billId]，从 [BillRepository.observeById] 拉取原账单回填。
 *
 * 业务组装（dedupHash、source、记录时间、编辑时保留交易时间）下沉到 UseCase；
 * 本类只收集 UI 输入并转发。一次性关闭走 [events]，不放进 [uiState]。
 *
 * 账户允许为空：用户未指定账户（或尚无账户）时也能保存。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val recordBill: RecordBillUseCase,
    private val updateBill: UpdateBillUseCase,
    private val deleteBill: DeleteBillUseCase,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val billRepository: BillRepository,
    private val clock: Clock
) : ViewModel() {

    private var editingId: Long = 0L
    private val isEditing: Boolean get() = editingId != 0L

    /** 编辑态保留原账单中不可由本页改写的字段（createdAt / dedupHash 等）。 */
    private var editingSnapshot: Bill? = null

    private val type = MutableStateFlow(BillType.EXPENSE)
    private val amountText = MutableStateFlow("")
    private val categoryId = MutableStateFlow<Long?>(null)
    /** 当前展开的一级分类；由选中项自动推导，不对外暴露 setter */
    private val expandedParentId = MutableStateFlow<Long?>(null)
    private val accountId = MutableStateFlow<Long?>(null)
    private val note = MutableStateFlow("")
    private val countInStats = MutableStateFlow(true)
    /** 交易时间：新增默认「现在」，可由日期键改写；编辑态 [load] 回填原值 */
    private val tradeTimeMillis = MutableStateFlow(clock.nowMillis())
    private val errorMessage = MutableStateFlow<String?>(null)

    private val _events = MutableSharedFlow<RecordEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<RecordEvent> = _events.asSharedFlow()

    private val categoryTree = type.flatMapLatest { t ->
        categoryRepository.observeByType(t).map { categories -> categories.toCategoryTree() }
    }
    private val accounts = accountRepository.observeActive()

    // 拆成两层 combine，避开 6+ Flow 的 typed overload 限制：
    // 第一层合出不可由日期影响的文本快照，第二层再并入 countInStats 与 tradeTimeMillis。
    private val formState = combine(
        combine(type, amountText, categoryId, accountId, note) { t, amt, catId, accId, nt ->
            FormSnapshot(t, amt, catId, accId, nt)
        },
        countInStats,
        tradeTimeMillis
    ) { form, cis, tt ->
        form.copy(countInStats = cis, tradeTimeMillis = tt)
    }

    val uiState: StateFlow<RecordUiState> = combine(
        categoryTree,
        accounts,
        formState,
        errorMessage,
        expandedParentId
    ) { tree, accs, form, error, parentId ->
        val cents = MoneyText.textToCents(form.amountText)
        RecordUiState(
            billId = editingId,
            isEditing = isEditing,
            type = form.type,
            amountText = form.amountText,
            amountCents = cents,
            categoryId = form.categoryId,
            accountId = form.accountId,
            note = form.note,
            tradeTimeMillis = form.tradeTimeMillis,
            categoryTree = tree,
            expandedParentId = parentId,
            accounts = accs,
            // 金额 > 0 且已选分类即可保存；账户允许为空
            canSave = cents > 0 && form.categoryId != null,
            countInStats = form.countInStats,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordUiState()
    )

    init {
        // 默认展开到选中项所属一级；无选中时展开第一个一级。用户手动收起/展开
        // 后 expandedParentId 非 null，不再自动覆盖，保证「再次点击收回」生效。
        viewModelScope.launch {
            combine(categoryTree, categoryId, expandedParentId) { tree, catId, exp ->
                Triple(tree, catId, exp)
            }.collect { (tree, catId, exp) ->
                if (tree.isEmpty()) return@collect
                if (exp == null) {
                    val target = catId?.let { id ->
                        tree.firstOrNull { node ->
                            node.parent.id == id || node.children.any { it.id == id }
                        }?.parent?.id
                    } ?: tree.first().parent.id
                    expandedParentId.value = target
                    if (catId == null) categoryId.value = target
                }
            }
        }
        viewModelScope.launch {
            accounts.collect { list ->
                if (accountId.value == null && list.isNotEmpty()) {
                    accountId.value = list.first().id
                }
            }
        }
    }

    /** 进入编辑态：一次性回填指定账单（由路由在编辑场景调用）。 */
    fun load(billId: Long) {
        if (billId == 0L) return
        editingId = billId
        viewModelScope.launch {
            val bill = billRepository.observeById(billId).first() ?: return@launch
            editingSnapshot = bill
            type.value = bill.type
            amountText.value = MoneyText.centsToText(bill.amountCents)
            categoryId.value = bill.categoryId
            accountId.value = bill.accountId
            note.value = bill.note ?: ""
            countInStats.value = bill.countInStats
            tradeTimeMillis.value = bill.tradeTimeMillis
            errorMessage.value = null
        }
    }

    // ---- 输入处理 ----

    fun onTypeChange(newType: BillType) {
        type.value = newType
        // 切换类型后分类树重建，清空旧选择，由 collect 默认选中新类型的首个一级分类
        categoryId.value = null
        expandedParentId.value = null
    }

    fun onDigit(digit: String) {
        val cur = amountText.value
        if (cur.contains(".")) {
            val decimals = cur.substringAfter(".")
            if (decimals.length >= 2) return
        }
        amountText.value = when {
            cur == "0" && digit != "." -> digit
            cur.isEmpty() && digit == "." -> "0."
            else -> cur + digit
        }
    }

    fun onDelete() {
        val cur = amountText.value
        amountText.value = if (cur.length <= 1) "" else cur.dropLast(1)
    }

    fun onCategorySelect(id: Long) {
        categoryId.value = id
    }

    /**
     * 点击一级分类：选中它，并在有子分类时切换展开/收起。
     * 再次点击已展开的一级会收回其二级网格。
     */
    fun onParentClick(parentId: Long) {
        categoryId.value = parentId
        expandedParentId.value = if (expandedParentId.value == parentId) null else parentId
    }

    fun onAccountSelect(id: Long) {
        accountId.value = id
    }

    fun onNoteChange(value: String) {
        note.value = value
    }

    fun onCountInStatsChange(value: Boolean) {
        countInStats.value = value
    }

    /**
     * 选择交易日期时间（精确到分）。
     *
     * 直接采用所选年月日与时分，不做归零；新增态默认即为「现在」的当前时分。
     */
    fun onDateTimeSelected(dateTime: LocalDateTime) {
        val zone = ZoneId.systemDefault()
        tradeTimeMillis.value = dateTime.atZone(zone).toInstant().toEpochMilli()
    }

    fun clearError() {
        errorMessage.value = null
    }

    // ---- 提交 ----

    fun save() {
        val state = uiState.value
        if (!state.canSave) return
        val catId = state.categoryId ?: return
        viewModelScope.launch {
            errorMessage.value = null
            val result = runCatching {
                if (isEditing) {
                    val snapshot = editingSnapshot
                        ?: error("编辑态缺少原账单快照")
                    updateBill(
                        UpdateBillUseCase.Params(
                            snapshot = snapshot,
                            amountCents = state.amountCents,
                            type = state.type,
                            categoryId = catId,
                            accountId = state.accountId,
                            note = state.note,
                            countInStats = state.countInStats,
                            tradeTimeMillis = state.tradeTimeMillis
                        )
                    )
                } else {
                    recordBill(
                        RecordBillUseCase.Params(
                            amountCents = state.amountCents,
                            type = state.type,
                            categoryId = catId,
                            accountId = state.accountId,
                            note = state.note,
                            countInStats = state.countInStats,
                            tradeTimeMillis = state.tradeTimeMillis
                        )
                    )
                }
            }
            result.fold(
                onSuccess = { savedId ->
                    // insert IGNORE 冲突时返回 -1，不可当作成功关闭
                    if (savedId >= 0L) {
                        _events.emit(RecordEvent.Close)
                    } else {
                        errorMessage.value = "保存失败：可能与已有账单冲突"
                    }
                },
                onFailure = { e ->
                    errorMessage.value = e.message ?: "保存失败"
                }
            )
        }
    }

    fun delete() {
        if (!isEditing) return
        viewModelScope.launch {
            errorMessage.value = null
            runCatching { deleteBill(editingId) }
                .onSuccess { _events.emit(RecordEvent.Close) }
                .onFailure { e -> errorMessage.value = e.message ?: "删除失败" }
        }
    }

    private data class FormSnapshot(
        val type: BillType,
        val amountText: String,
        val categoryId: Long?,
        val accountId: Long?,
        val note: String,
        val countInStats: Boolean = true,
        val tradeTimeMillis: Long = 0L
    )
}
