package com.kai.bill.feature.settings.account

import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.AccountType

/**
 * 账户管理页状态。
 *
 * @property accounts 全部账户（含已归档），按排序号升序
 * @property newType 新增账户对话框里当前选中的账户类型
 * @property editing 正在编辑的目标；null 表示无对话框
 * @property pendingDelete 等待二次确认的待删除账户；null 表示无确认框
 * @property errorMessage 错误提示（点按可清除）
 */
data class AccountManageUiState(
    val accounts: List<Account> = emptyList(),
    val newType: AccountType = AccountType.CASH,
    val editing: AccountEditTarget? = null,
    val pendingDelete: Account? = null,
    val errorMessage: String? = null
)

/** 编辑目标：新增 / 重命名。 */
sealed interface AccountEditTarget {
    data object New : AccountEditTarget
    data class Rename(val account: Account) : AccountEditTarget
}
