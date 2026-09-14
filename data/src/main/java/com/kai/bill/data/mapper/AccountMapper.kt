package com.kai.bill.data.mapper

import com.kai.bill.core.db.entity.AccountEntity
import com.kai.bill.core.db.entity.AccountType as EntityAccountType
import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.AccountType

/** Account Entity ⇄ Domain */
object AccountMapper {

    fun toDomain(entity: AccountEntity): Account = Account(
        id = entity.id,
        name = entity.name,
        icon = entity.icon,
        type = entity.type.toDomain(),
        sortOrder = entity.sortOrder,
        isArchived = entity.isArchived
    )

    fun toEntity(account: Account): AccountEntity = AccountEntity(
        id = account.id,
        name = account.name,
        icon = account.icon,
        type = account.type.toEntity(),
        sortOrder = account.sortOrder,
        isArchived = account.isArchived
    )
}

private fun EntityAccountType.toDomain(): AccountType = when (this) {
    EntityAccountType.CASH -> AccountType.CASH
    EntityAccountType.ALIPAY -> AccountType.ALIPAY
    EntityAccountType.WECHAT -> AccountType.WECHAT
    EntityAccountType.BANK_CARD -> AccountType.BANK_CARD
    EntityAccountType.CREDIT_CARD -> AccountType.CREDIT_CARD
}

private fun AccountType.toEntity(): EntityAccountType = when (this) {
    AccountType.CASH -> EntityAccountType.CASH
    AccountType.ALIPAY -> EntityAccountType.ALIPAY
    AccountType.WECHAT -> EntityAccountType.WECHAT
    AccountType.BANK_CARD -> EntityAccountType.BANK_CARD
    AccountType.CREDIT_CARD -> EntityAccountType.CREDIT_CARD
}
