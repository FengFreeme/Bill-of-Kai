package com.kai.bill.feature.settings.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.feature.common.SectionTitle

/**
 * 数据备份页：导出 / 导入账单、分类、账户与预算。
 *
 * 文件读写走 SAF（系统文件选择器），**不需要申请存储权限**：
 * 导出用 `CreateDocument` 让用户选保存位置，导入用 `OpenDocument` 让用户选文件。
 *
 * @param uiState 备份状态
 * @param suggestedFileName 导出时预填的文件名（带日期）
 * @param onExport 用户选定保存位置后回调（携带目标 URI）
 * @param onImport 用户选定备份文件后回调（携带源 URI）
 * @param onDismissMessage 点掉结果提示
 * @param onBack 返回
 * @param modifier 外部修饰符
 */
@Composable
fun BackupScreen(
    uiState: BackupUiState,
    suggestedFileName: String,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 导出：系统让用户选「存到哪」，无存储权限要求
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        // 用户取消时 uri 为 null，不能当作导出成功
        if (uri != null) onExport(uri)
    }

    // 导入：部分文件管理器把 .json 标成 octet-stream，用 */* 更稳妥
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImport(uri)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "‹",
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.color.onSurface
                )
            }
            Text(
                text = "数据备份",
                style = AppTheme.typography.titleLarge,
                color = AppTheme.color.onSurface
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "intro") {
                ListCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SectionTitle(text = "用途")
                        Text(
                            text = "把账单、分类、账户与预算打包成一个文件，" +
                                "在新手机上导入即可完成迁移。",
                            style = AppTheme.typography.bodyMedium,
                            color = AppTheme.color.onSurface
                        )
                        Text(
                            text = "导入采用「合并去重」：重复的账单会自动跳过，" +
                                "不影响本机已有数据，同一份文件可反复导入。",
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.color.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "export") {
                BackupActionCard(
                    title = "导出备份",
                    description = "生成一个 JSON 文件，含全部账单、分类、账户与预算。" +
                        "建议存到网盘或传给自己，方便换机时使用。",
                    buttonText = "导出到文件",
                    primary = true,
                    enabled = !uiState.isWorking,
                    onClick = { exportLauncher.launch(suggestedFileName) }
                )
            }

            item(key = "import") {
                BackupActionCard(
                    title = "导入备份",
                    description = "选择之前导出的 .json 文件。" +
                        "本机没有的分类与账户会自动创建，重复账单会被跳过。",
                    buttonText = "从文件导入",
                    primary = false,
                    enabled = !uiState.isWorking,
                    onClick = { importLauncher.launch(arrayOf("*/*")) }
                )
            }

            uiState.message?.let { message ->
                item(key = "message") {
                    ListCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onDismissMessage)
                    ) {
                        Text(
                            text = message,
                            style = AppTheme.typography.bodyMedium,
                            color = if (uiState.isError) {
                                AppTheme.ext.expense
                            } else {
                                AppTheme.color.onSurface
                            },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 单个操作卡：标题 + 说明 + 一个按钮 */
@Composable
private fun BackupActionCard(
    title: String,
    description: String,
    buttonText: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    ListCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.color.onSurface
            )
            Text(
                text = description,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            !enabled -> AppTheme.color.primary.copy(alpha = 0.38f)
                            primary -> AppTheme.color.primary
                            else -> AppTheme.color.outline.copy(alpha = 0.16f)
                        }
                    )
                    .clickable(enabled = enabled, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buttonText,
                    style = AppTheme.typography.titleMedium,
                    color = when {
                        !enabled -> AppTheme.color.onPrimary.copy(alpha = 0.6f)
                        primary -> AppTheme.color.onPrimary
                        else -> AppTheme.color.onSurface
                    }
                )
            }
        }
    }
}

/**
 * 数据备份路由，负责接上 ViewModel。
 *
 * @param onBack 返回
 * @param modifier 外部修饰符
 * @param viewModel 由 Hilt 注入
 */
@Composable
fun BackupRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 文件名只在进入页面时算一次，避免重组时跨天变化
    val fileName = remember { viewModel.suggestedFileName() }

    BackupScreen(
        uiState = uiState,
        suggestedFileName = fileName,
        onExport = viewModel::exportTo,
        onImport = viewModel::importFrom,
        onDismissMessage = viewModel::clearMessage,
        onBack = onBack,
        modifier = modifier
    )
}

@Preview(name = "数据备份-浅色", showBackground = true)
@Composable
private fun BackupScreenLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        BackupScreen(
            uiState = BackupUiState(),
            suggestedFileName = "xiaokai-backup-2026-09-18.json",
            onExport = {},
            onImport = {},
            onDismissMessage = {},
            onBack = {}
        )
    }
}
