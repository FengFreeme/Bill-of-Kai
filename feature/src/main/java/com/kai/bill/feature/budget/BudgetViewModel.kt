package com.kai.bill.feature.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.bill.domain.model.Budget
import com.kai.bill.domain.model.BudgetPeriod
import com.kai.bill.domain.model.DailyMode
import com.kai.bill.domain.repository.BudgetRepository
import com.kai.bill.domain.usecase.budget.ObserveBudgetProgressUseCase
import com.kai.bill.domain.usecase.budget.SaveBudgetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/**
 * 预算页 ViewModel：同时负责
 * - 设置表单的读写（月预算 / 弹性·固定日预算 / 启用开关），落库走 [SaveBudgetUseCase]
 * - 进度展示（由 [ObserveBudgetProgressUseCase] 组合的月 / 日进度与超支判定）
 *
 * M3 只支持「总额预算」（[Budget.categoryId] 为 null）；分类预算字段已预留但不在本页暴露。
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val observeProgress: ObserveBudgetProgressUseCase,
    private val saveBudget: SaveBudgetUseCase
) : ViewModel() {

    /** 设置表单的本地草稿，用户输入的唯一真相来源 */
    private val form = MutableStateFlow(BudgetForm())

    /** 已加载预算的主键：0 表示尚未落库的新预算 */
    private var loadedId: Long = 0L

    init {
        // 用数据库里已有的总额预算给表单做初值；之后用户改了就以本地草稿为准
        viewModelScope.launch {
            budgetRepository.observeTotalBudget().collect { budget ->
                if (budget != null) {
                    loadedId = budget.id
                    form.value = BudgetForm(
                        monthlyBudgetText = formatCentsToYuanInput(budget.amountCents),
                        dailyMode = budget.dailyMode,
                        dailyBudgetText = formatCentsToYuanInput(budget.dailyAmountCents),
                        enabled = budget.enabled
                    )
                }
            }
        }
    }

    val uiState: StateFlow<BudgetUiState> = combine(form, observeProgress()) { f, progress ->
        BudgetUiState(
            monthlyBudgetText = f.monthlyBudgetText,
            dailyMode = f.dailyMode,
            dailyBudgetText = f.dailyBudgetText,
            enabled = f.enabled,
            hasExisting = loadedId != 0L,
            progress = progress
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetUiState()
    )

    // —— 表单事件 ——
    fun onMonthlyBudgetChange(text: String) {
        form.value = form.value.copy(monthlyBudgetText = text)
    }

    fun onDailyModeChange(mode: DailyMode) {
        form.value = form.value.copy(dailyMode = mode)
    }

    fun onDailyBudgetChange(text: String) {
        form.value = form.value.copy(dailyBudgetText = text)
    }

    fun onEnabledChange(enabled: Boolean) {
        form.value = form.value.copy(enabled = enabled)
    }

    /** 月预算是否可保存（非空且能解析成合法金额） */
    fun canSave(): Boolean = parseYuanToCents(form.value.monthlyBudgetText) != null

    /** 保存预算配置：拼装 [Budget] 后经 [SaveBudgetUseCase] 落库 */
    fun save() {
        val f = form.value
        val monthlyCents = parseYuanToCents(f.monthlyBudgetText) ?: return
        val dailyCents = if (f.dailyMode == DailyMode.FIXED) {
            parseYuanToCents(f.dailyBudgetText) ?: 0L
        } else {
            0L
        }

        viewModelScope.launch {
            val budget = Budget(
                id = loadedId,
                categoryId = null,
                period = BudgetPeriod.MONTHLY,
                amountCents = monthlyCents,
                startDay = 1,
                dailyMode = f.dailyMode,
                dailyAmountCents = dailyCents,
                carryOver = false,
                enabled = f.enabled
            )
            saveBudget(budget)
        }
    }

    /** 把「分」转成无千分位的「元」字符串（如 `3000`、`3000.5`、`3000.05`），供输入框初值 */
    private fun formatCentsToYuanInput(cents: Long): String {
        val abs = kotlin.math.abs(cents)
        val yuan = abs / 100
        val fen = abs % 100
        return if (fen == 0L) yuan.toString() else "$yuan.${fen.toString().padStart(2, '0')}"
    }

    /** 把「元」字符串（允许千分位逗号、小数）解析成「分」；空或非法返回 null */
    private fun parseYuanToCents(text: String): Long? {
        val trimmed = text.replace(",", "").trim()
        if (trimmed.isEmpty()) return null
        val value = trimmed.toBigDecimalOrNull() ?: return null
        if (value < BigDecimal.ZERO) return null
        return (value * BigDecimal(100)).toLong()
    }

    /** 设置表单的内部草稿 */
    private data class BudgetForm(
        val monthlyBudgetText: String = "",
        val dailyMode: DailyMode = DailyMode.ELASTIC,
        val dailyBudgetText: String = "",
        val enabled: Boolean = true
    )
}
