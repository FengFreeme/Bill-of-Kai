package com.kai.bill.data.presets

import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.AccountType

/**
 * 预置账户（现金 / 支付宝 / 微信 / 储蓄卡 / 信用卡）。
 *
 * 与 [DefaultCategories] 同样的固定 id 约定；账户表无 `isSystem` 字段，
 * 预置账户靠固定 id 防重复；[com.kai.bill.domain.repository.AccountRepository.insertAll]
 * 同样用 `OnConflictStrategy.IGNORE`，重复播种幂等。
 *
 * 账户**只归档不删除**：这里默认全部 `isArchived = false`。
 */
object DefaultAccounts {

    val all: List<Account> = listOf(
        Account(1, "现金", "cash", AccountType.CASH, 1, false),
        Account(2, "支付宝", "wallet", AccountType.ALIPAY, 2, false),
        Account(3, "微信", "chatbubbles", AccountType.WECHAT, 3, false),
        Account(4, "储蓄卡", "card", AccountType.BANK_CARD, 4, false),
        Account(5, "信用卡", "card-outline", AccountType.CREDIT_CARD, 5, false)
    )
}
