package com.kai.bill.feature.budget

import com.kai.bill.domain.model.DailyMode
import com.kai.bill.domain.model.stats.BudgetProgress

/**
 * 预算页 UI 状态：同时承载「设置表单」与「进度展示」。
 *
 * 表单字段用「元」为单位的字符串存储，方便 `TextField` 受控编辑；
 * 落库时由 [BudgetViewModel] 解析回「分」。
 */
data class BudgetUiState(
    // —— 设置表单（月预算 / 日预算）——
    val monthlyBudgetText: String = "",
    val dailyMode: DailyMode = DailyMode.ELASTIC,
    val dailyBudgetText: String = "",
    val enabled: Boolean = true,
    /** 是否已有预算配置（决定「保存」文案为新增 / 更新） */
    val hasExisting: Boolean = false,
    // —— 进度展示（由 ObserveBudgetProgressUseCase 组合）——
    val progress: List<BudgetProgress> = emptyList(),
    // —— 保存副作用 ——
    val isSaving: Boolean = false,
    val saveError: String? = null
) {
    /** 仅在固定日预算模式下才需要填写日预算 */
    val dailyBudgetEditable: Boolean get() = dailyMode == DailyMode.FIXED
}
