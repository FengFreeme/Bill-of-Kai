package com.kai.bill.domain.usecase.stats

import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.model.stats.AccountAmount
import com.kai.bill.domain.model.stats.AccountStat
import com.kai.bill.domain.repository.AccountRepository
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 观察账户维度的统计。
 *
 * 用 [AccountRepository.observeAll] 而非 `observeActive`：归档账户的历史流水
 * 仍然参与统计，只看活跃账户会让这部分金额凭空消失。
 *
 * 「未指定账户」（accountId 为 null）单独成项，不合并进任何账户，
 * 否则这部分金额要么丢失、要么被错误地记到某个账户头上。
 */
class ObserveAccountStatsUseCase @Inject constructor(
    private val statsRepository: StatsRepository,
    private val accountRepository: AccountRepository,
    private val billRepository: BillRepository
) {

    operator fun invoke(
        range: DateRange,
        type: BillType,
        filter: BillFilter? = null
    ): Flow<List<AccountStat>> {
        val amounts: Flow<List<AccountAmount>> = if (filter == null) {
            statsRepository.observeAccountAmounts(range, type)
        } else {
            billRepository.observeBills(range, filter)
                .map { bills -> StatsAggregator.accountAmounts(bills, type) }
        }

        return combine(amounts, accountRepository.observeAll()) { raw, accounts ->
            val accountById = accounts.associateBy { it.id }
            val totalCents = raw.sumOf { it.amountCents }
            raw.map { amount ->
                val account = amount.accountId?.let { accountById[it] }
                AccountStat(
                    accountId = amount.accountId,
                    accountName = when {
                        amount.accountId == null -> UNSPECIFIED_ACCOUNT_NAME
                        account == null -> DELETED_ACCOUNT_NAME
                        else -> account.name
                    },
                    amountCents = amount.amountCents,
                    ratio = ratioOf(amount.amountCents, totalCents)
                )
            }
        }
    }

    private companion object {
        const val UNSPECIFIED_ACCOUNT_NAME = "未指定账户"
        const val DELETED_ACCOUNT_NAME = "已删除账户"
    }
}
