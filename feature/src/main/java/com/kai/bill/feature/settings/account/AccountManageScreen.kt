package com.kai.bill.feature.settings.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.component.SegmentTabs
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.AccountType
import com.kai.bill.feature.record.components.accountIconRes

/** 预置账户固定占用 id 1..5，只归档不删除（见 [AccountManageViewModel]） */
private val PRESET_ID_RANGE = 1L..5L

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.CASH -> "现金"
    AccountType.ALIPAY -> "支付宝"
    AccountType.WECHAT -> "微信"
    AccountType.BANK_CARD -> "储蓄卡"
    AccountType.CREDIT_CARD -> "信用卡"
}

/**
 * 账户管理页：账户列表 + 新增 / 改名 / 归档恢复 / 删除。
 *
 * 自上而下：顶栏 → 账户列表（图标 + 名称 + 类型 / 归档标记 + 操作）→ 新增入口 → 错误提示。
 * 无状态：只消费 [AccountManageUiState]，所有写操作回调上抛给 [AccountManageViewModel]。
 *
 * 预置账户（id 1..5）不显示删除入口，仅可归档，避免历史账单变成孤儿数据。
 *
 * @param uiState 账户管理状态
 * @param onRequestAdd 请求新增账户
 * @param onRequestRename 请求重命名
 * @param onToggleArchive 归档 / 恢复切换
 * @param onRequestDelete 请求删除（ViewModel 内做预置校验）
 * @param onSubmitEdit 提交对话框里的账户名
 * @param onNewTypeChange 新增对话框切换账户类型
 * @param onDismissEdit 关闭编辑对话框
 * @param onCancelDelete 取消删除确认
 * @param onConfirmDelete 确认删除
 * @param onClearError 清除错误提示
 * @param onBack 返回
 */
@Composable
fun AccountManageScreen(
    uiState: AccountManageUiState,
    onRequestAdd: () -> Unit,
    onRequestRename: (Account) -> Unit,
    onToggleArchive: (Account) -> Unit,
    onRequestDelete: (Account) -> Unit,
    onSubmitEdit: (String) -> Unit,
    onNewTypeChange: (AccountType) -> Unit,
    onDismissEdit: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val types = remember { AccountType.entries.toList() }

    Column(modifier = modifier.fillMaxSize()) {
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
                text = "账户管理",
                style = AppTheme.typography.titleLarge,
                color = AppTheme.color.onSurface
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.accounts.forEach { account ->
                item(key = "acc_${account.id}") {
                    AccountRow(
                        account = account,
                        onRename = { onRequestRename(account) },
                        onToggleArchive = { onToggleArchive(account) },
                        onDelete = { onRequestDelete(account) }
                    )
                }
            }
            item(key = "add") {
                Text(
                    text = "＋ 新增账户",
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.color.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onRequestAdd)
                        .padding(vertical = 14.dp)
                )
            }
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = AppTheme.ext.expense,
                style = AppTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClearError() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }

    uiState.editing?.let { target ->
        AccountEditDialog(
            target = target,
            newType = uiState.newType,
            onNewTypeChange = onNewTypeChange,
            onDismiss = onDismissEdit,
            onConfirm = onSubmitEdit
        )
    }

    uiState.pendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(text = "删除账户") },
            text = {
                Text(
                    text = "确定删除「${account.name}」？该账户下的历史账单将失去账户归属，" +
                        "建议改为「归档」而非删除。"
                )
            },
            confirmButton = {
                Text(
                    text = "删除",
                    color = AppTheme.ext.expense,
                    modifier = Modifier
                        .clickable(onClick = onConfirmDelete)
                        .padding(12.dp)
                )
            },
            dismissButton = {
                Text(
                    text = "取消",
                    color = AppTheme.color.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(onClick = onCancelDelete)
                        .padding(12.dp)
                )
            }
        )
    }
}

@Composable
private fun AccountRow(
    account: Account,
    onRename: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val isPreset = account.id in PRESET_ID_RANGE
    // 用 ListCard 包裹：有背景图时账户行不会直接裸在照片上，避免割裂感
    ListCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AccountDot(account = account, size = 32.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.color.onSurface
                )
                Text(
                    text = buildString {
                        append(accountTypeLabel(account.type))
                        if (account.isArchived) append(" · 已归档")
                    },
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
            }
            // 归档 / 恢复
            Text(
                text = if (account.isArchived) "恢复" else "归档",
                style = AppTheme.typography.titleSmall,
                color = AppTheme.color.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggleArchive)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            RowAction(text = "✎", onClick = onRename)
            if (!isPreset) {
                RowAction(text = "🗑", onClick = onDelete)
            }
        }
    }
}

/** 圆形账户标识：有矢量图标就画图标，没有则退化为文字 / emoji。 */
@Composable
private fun AccountDot(account: Account, size: Dp) {
    val iconRes = accountIconRes(account.icon)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(AppTheme.color.primary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = account.name,
                tint = AppTheme.color.primary,
                modifier = Modifier.size(size * 0.5f)
            )
        } else {
            Text(
                text = account.icon ?: "·",
                style = AppTheme.typography.labelLarge,
                color = AppTheme.color.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RowAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = AppTheme.typography.titleSmall,
        color = AppTheme.color.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun AccountEditDialog(
    target: AccountEditTarget,
    newType: AccountType,
    onNewTypeChange: (AccountType) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(target) {
        mutableStateOf((target as? AccountEditTarget.Rename)?.account?.name.orEmpty())
    }
    val isNew = target is AccountEditTarget.New
    val types = remember { AccountType.entries.toList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (isNew) "新增账户" else "重命名账户") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("账户名") },
                    shape = RoundedCornerShape(12.dp)
                )
                if (isNew) {
                    SegmentTabs(
                        items = types,
                        selected = newType,
                        onSelect = onNewTypeChange,
                        labelOf = { accountTypeLabel(it) }
                    )
                }
            }
        },
        confirmButton = {
            Text(
                text = "保存",
                color = AppTheme.color.primary,
                modifier = Modifier
                    .clickable { onConfirm(text) }
                    .padding(12.dp)
            )
        },
        dismissButton = {
            Text(
                text = "取消",
                color = AppTheme.color.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(12.dp)
            )
        }
    )
}

/** 账户管理的路由。 */
@Composable
fun AccountManageRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountManageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AccountManageScreen(
        uiState = uiState,
        onRequestAdd = viewModel::requestAdd,
        onRequestRename = viewModel::requestRename,
        onToggleArchive = viewModel::toggleArchive,
        onRequestDelete = viewModel::requestDelete,
        onSubmitEdit = viewModel::submitEdit,
        onNewTypeChange = viewModel::onNewTypeChange,
        onDismissEdit = viewModel::dismissEdit,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onClearError = viewModel::clearError,
        onBack = onBack,
        modifier = modifier
    )
}

@Preview(name = "账户管理-浅色", showBackground = true)
@Composable
private fun AccountManageScreenLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        AccountManageScreen(
            uiState = AccountManageUiState(
                accounts = listOf(
                    Account(1, "现金", "cash", AccountType.CASH, 1, false),
                    Account(2, "支付宝", "wallet", AccountType.ALIPAY, 2, false),
                    Account(6, "招行储蓄卡", "card", AccountType.BANK_CARD, 6, true)
                )
            ),
            onRequestAdd = {},
            onRequestRename = {},
            onToggleArchive = {},
            onRequestDelete = {},
            onSubmitEdit = {},
            onNewTypeChange = {},
            onDismissEdit = {},
            onCancelDelete = {},
            onConfirmDelete = {},
            onClearError = {},
            onBack = {}
        )
    }
}
