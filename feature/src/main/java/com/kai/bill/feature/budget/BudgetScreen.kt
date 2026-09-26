package com.kai.bill.feature.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.design.component.CtaButton
import com.kai.bill.core.design.component.GlassCard
import com.kai.bill.core.design.component.SegmentTabs
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.domain.model.Budget
import com.kai.bill.domain.model.BudgetPeriod
import com.kai.bill.domain.model.DailyMode
import com.kai.bill.domain.model.stats.BudgetProgress
import com.kai.bill.feature.common.SectionTitle
import com.kai.bill.feature.home.components.BudgetCard

/**
 * 预算页：上半部分展示进度（进度卡 + 超支提醒），下半部分是设置表单
 * （月预算 / 弹性·固定日预算 / 启用开关）。
 *
 * 纯展示 + 回调：所有状态来自 [BudgetUiState]，改动通过回调上抛给 [BudgetViewModel]。
 *
 * @param uiState 预算状态（进度 + 表单草稿）
 * @param onMonthlyBudgetChange 月预算文本变更
 * @param onDailyModeChange 日预算模式切换
 * @param onDailyBudgetChange 日预算文本变更
 * @param onEnabledChange 启用开关切换
 * @param onSave 保存预算
 */
@Composable
fun BudgetScreen(
    uiState: BudgetUiState,
    onMonthlyBudgetChange: (String) -> Unit,
    onDailyModeChange: (DailyMode) -> Unit,
    onDailyBudgetChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 超支提醒：任一预算超支即横幅告警
        if (uiState.progress.any { it.isOverBudget }) {
            item(key = "overspend") {
                OverspendBanner()
            }
        }

        item(key = "progress_title") {
            SectionTitle(text = "预算进度")
        }

        // 进度卡：总额预算 + 各分类预算
        items(
            count = uiState.progress.size,
            key = { index -> uiState.progress[index].budget.id }
        ) { index ->
            BudgetCard(progress = uiState.progress[index])
        }

        item(key = "settings_title") {
            SectionTitle(text = "预算设置")
        }

        item(key = "settings_form") {
            BudgetSettingsForm(
                uiState = uiState,
                onMonthlyBudgetChange = onMonthlyBudgetChange,
                onDailyModeChange = onDailyModeChange,
                onDailyBudgetChange = onDailyBudgetChange,
                onEnabledChange = onEnabledChange
            )
        }

        item(key = "save") {
            CtaButton(
                text = if (uiState.hasExisting) "更新预算" else "保存预算",
                onClick = onSave,
                enabled = uiState.monthlyBudgetText.isNotBlank()
            )
        }
    }
}

/**
 * 超支横幅：本月已花超预算时提示。
 */
@Composable
private fun OverspendBanner(modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        strong = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "⚠ 已超支",
                style = AppTheme.typography.titleMedium,
                color = AppTheme.ext.expense
            )
            Text(
                text = "本月预算已花超，注意控制后续支出",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.color.onSurfaceVariant
            )
        }
    }
}

/**
 * 预算设置表单：月预算、日预算模式（弹性 / 固定）、固定模式下的日预算、启用开关。
 */
@Composable
private fun BudgetSettingsForm(
    uiState: BudgetUiState,
    onMonthlyBudgetChange: (String) -> Unit,
    onDailyModeChange: (DailyMode) -> Unit,
    onDailyBudgetChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth(), strong = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.monthlyBudgetText,
                onValueChange = onMonthlyBudgetChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("每月预算（元）") },
                prefix = { Text("¥") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "日预算模式",
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.color.onSurfaceVariant
                )
                SegmentTabs(
                    items = DailyMode.entries.toList(),
                    selected = uiState.dailyMode,
                    onSelect = onDailyModeChange,
                    labelOf = { if (it == DailyMode.ELASTIC) "弹性" else "固定" }
                )
                Text(
                    text = "弹性：今日可用 = 月剩余 ÷ 剩余天数；固定：每天固定额度",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
            }

            // 仅固定模式需要填日预算
            if (uiState.dailyBudgetEditable) {
                OutlinedTextField(
                    value = uiState.dailyBudgetText,
                    onValueChange = onDailyBudgetChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("每日预算（元）") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "启用预算",
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.color.onSurface
                )
                Switch(
                    checked = uiState.enabled,
                    onCheckedChange = onEnabledChange
                )
            }
        }
    }
}

/** 预算页路由。 */
@Composable
fun BudgetRoute(
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BudgetScreen(
        uiState = uiState,
        onMonthlyBudgetChange = viewModel::onMonthlyBudgetChange,
        onDailyModeChange = viewModel::onDailyModeChange,
        onDailyBudgetChange = viewModel::onDailyBudgetChange,
        onEnabledChange = viewModel::onEnabledChange,
        onSave = viewModel::save,
        modifier = modifier
    )
}

@Preview(name = "预算页-浅色", showBackground = true)
@Composable
private fun BudgetScreenLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        BudgetScreen(
            uiState = BudgetUiState(
                monthlyBudgetText = "3000",
                dailyMode = DailyMode.ELASTIC,
                hasExisting = true,
                progress = listOf(
                    BudgetProgress(
                        budget = Budget(
                            categoryId = null,
                            period = BudgetPeriod.MONTHLY,
                            amountCents = 300_000L,
                            startDay = 1,
                            dailyMode = DailyMode.ELASTIC,
                            dailyAmountCents = 0L,
                            carryOver = false,
                            enabled = true
                        ),
                        monthlyBudgetCents = 300_000L,
                        monthSpentCents = 120_000L,
                        monthRemainingCents = 180_000L,
                        daysRemaining = 10,
                        todayAvailableCents = 18_000L,
                        todaySpentCents = 2_000L,
                        todayLeftCents = 16_000L,
                        progress = 0.4f,
                        isOverBudget = false
                    )
                )
            ),
            onMonthlyBudgetChange = {},
            onDailyModeChange = {},
            onDailyBudgetChange = {},
            onEnabledChange = {},
            onSave = {}
        )
    }
}

@Preview(name = "预算页-超支深色", showBackground = true)
@Composable
private fun BudgetScreenOverDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.LILAC, darkMode = DarkMode.DARK) {
        BudgetScreen(
            uiState = BudgetUiState(
                monthlyBudgetText = "1000",
                dailyMode = DailyMode.FIXED,
                dailyBudgetText = "50",
                hasExisting = true,
                progress = listOf(
                    BudgetProgress(
                        budget = Budget(
                            categoryId = null,
                            period = BudgetPeriod.MONTHLY,
                            amountCents = 100_000L,
                            startDay = 1,
                            dailyMode = DailyMode.FIXED,
                            dailyAmountCents = 5_000L,
                            carryOver = false,
                            enabled = true
                        ),
                        monthlyBudgetCents = 100_000L,
                        monthSpentCents = 120_000L,
                        monthRemainingCents = -20_000L,
                        daysRemaining = 5,
                        todayAvailableCents = 5_000L,
                        todaySpentCents = 6_000L,
                        todayLeftCents = -1_000L,
                        progress = 1f,
                        isOverBudget = true
                    )
                )
            ),
            onMonthlyBudgetChange = {},
            onDailyModeChange = {},
            onDailyBudgetChange = {},
            onEnabledChange = {},
            onSave = {}
        )
    }
}
