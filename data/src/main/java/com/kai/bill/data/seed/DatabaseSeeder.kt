package com.kai.bill.data.seed

import com.kai.bill.data.presets.DefaultAccounts
import com.kai.bill.data.presets.DefaultCategories
import com.kai.bill.data.presets.DefaultParseRules
import com.kai.bill.domain.repository.AccountRepository
import com.kai.bill.domain.repository.CategoryRepository
import com.kai.bill.domain.repository.ParseRuleRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首次启动播种预置分类、账户与解析规则。
 *
 * 预置数据使用固定 id + `isSystem = true`，且底层写入用 `OnConflictStrategy.IGNORE` / `@Upsert`，
 * 因此重复调用天然幂等：已存在则跳过或覆盖，不会长出重复行。
 * 这里在每次冷启动时调用一次即可，无需额外「是否已播种」标志位。
 *
 * 预置规则带固定 id，重新播种会按主键覆盖，实现「支付宝 / 微信改版后无需发版即可更新规则」。
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val parseRuleRepository: ParseRuleRepository
) {

    suspend fun seed() {
        categoryRepository.insertAll(DefaultCategories.all)
        accountRepository.insertAll(DefaultAccounts.all)
        parseRuleRepository.upsertAll(DefaultParseRules.all)

        DefaultCategories.all.forEach { categoryRepository.refreshIcon(it.id, it.icon) }
        DefaultAccounts.all.forEach { accountRepository.refreshIcon(it.id, it.icon) }
    }
}
