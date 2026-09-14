package com.kai.bill.feature.settings.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.AccountType
import com.kai.bill.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 预置账户固定占用 id 1..5（见 [com.kai.bill.data.presets.DefaultAccounts]），只归档不删除 */
private val PRESET_ID_RANGE = 1L..5L

/** 按账户类型分配默认图标 key，与 [com.kai.bill.data.presets.DefaultAccounts] 保持一致 */
private fun iconForType(type: AccountType): String = when (type) {
    AccountType.CASH -> "cash"
    AccountType.ALIPAY -> "wallet"
    AccountType.WECHAT -> "chatbubbles"
    AccountType.BANK_CARD -> "card"
    AccountType.CREDIT_CARD -> "card-outline"
}

/**
 * 账户管理页 ViewModel。
 *
 * 提供四类操作：新增、改名、归档 / 恢复、删除。
 * 两条保护规则（都在本类里拦，UI 不必重复判断）：
 * - **预置账户（id 1..5）不可删** —— 历史账单通过 `accountId` 关联，物理删除会留下孤儿数据；
 *   预置账户只提供「归档」入口，归档后退出「记一笔」选择器但仍可查历史。
 * - 删除仅用于清理用户自建账户，故以确认框二次确认。
 *
 * @property repository 账户仓储
 */
@HiltViewModel
class AccountManageViewModel @Inject constructor(
    private val repository: AccountRepository
) : ViewModel() {

    private val accounts = repository.observeAll()
    private val newType = MutableStateFlow(AccountType.CASH)
    private val editing = MutableStateFlow<AccountEditTarget?>(null)
    private val pendingDelete = MutableStateFlow<Account?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AccountManageUiState> = combine(
        accounts,
        newType,
        editing,
        pendingDelete,
        errorMessage
    ) { accs, type, edit, del, err ->
        AccountManageUiState(
            accounts = accs,
            newType = type,
            editing = edit,
            pendingDelete = del,
            errorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AccountManageUiState()
    )

    // ---- 新增类型切换 ----

    fun onNewTypeChange(type: AccountType) {
        newType.value = type
    }

    // ---- 编辑对话框 ----

    fun requestAdd() {
        editing.value = AccountEditTarget.New
    }

    fun requestRename(account: Account) {
        editing.value = AccountEditTarget.Rename(account)
    }

    fun dismissEdit() {
        editing.value = null
    }

    /** 提交对话框内容；空名直接拒绝 */
    fun submitEdit(name: String) {
        val target = editing.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            errorMessage.value = "账户名不能为空"
            return
        }
        viewModelScope.launch {
            runCatching {
                when (target) {
                    is AccountEditTarget.New -> {
                        val maxSort = accounts.first().maxOfOrNull { it.sortOrder } ?: 0
                        repository.insert(
                            Account(
                                name = trimmed,
                                icon = iconForType(newType.value),
                                type = newType.value,
                                sortOrder = maxSort + 1,
                                isArchived = false
                            )
                        )
                    }

                    is AccountEditTarget.Rename ->
                        repository.update(target.account.copy(name = trimmed))
                }
            }.onSuccess {
                editing.value = null
                errorMessage.value = null
            }.onFailure { e ->
                errorMessage.value = e.message ?: "保存失败"
            }
        }
    }

    // ---- 归档 / 删除 ----

    fun toggleArchive(account: Account) {
        viewModelScope.launch {
            runCatching { repository.update(account.copy(isArchived = !account.isArchived)) }
                .onFailure { e -> errorMessage.value = e.message ?: "操作失败" }
        }
    }

    fun requestDelete(account: Account) {
        if (account.id in PRESET_ID_RANGE) {
            errorMessage.value = "预置账户只能归档，不能删除"
            return
        }
        pendingDelete.value = account
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        val acc = pendingDelete.value ?: return
        viewModelScope.launch {
            runCatching { repository.deleteById(acc.id) }
                .onFailure { e -> errorMessage.value = e.message ?: "删除失败" }
            pendingDelete.value = null
        }
    }

    fun clearError() {
        errorMessage.value = null
    }
}
