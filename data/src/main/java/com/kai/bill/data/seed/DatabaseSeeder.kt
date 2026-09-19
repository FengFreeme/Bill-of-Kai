package com.kai.bill.data.seed

import com.kai.bill.data.presets.DefaultAccounts
import com.kai.bill.data.presets.DefaultCategories
import com.kai.bill.domain.repository.AccountRepository
import com.kai.bill.domain.repository.CategoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首次启动播种预置分类与账户。
 *
 * 预置数据使用固定 id + `isSystem = true`，且底层写入用 `OnConflictStrategy.IGNORE`，
 * 因此重复调用天然幂等：已存在则跳过，不会长出重复行。这里在每次冷启动时调用一次即可，
 * 无需额外「是否已播种」标志位。
 *
 * **解析规则已不再播种**：M4 的 13 条整包正则被四级路由的词表取代（P0 为代码常量，
 * 见 `DefaultMatchKeywords`）。`parse_rule` 表与 `ParseRuleRepository` 保留给 P1
 * 改造成 `match_keyword` 词表后复用 —— 届时播种策略要一并改掉「按主键覆盖」的行为，
 * 否则用户编辑过的词条会在每次冷启动被打回原形。
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) {

    suspend fun seed() {
        categoryRepository.insertAll(DefaultCategories.all)
        accountRepository.insertAll(DefaultAccounts.all)

        DefaultCategories.all.forEach { categoryRepository.refreshIcon(it.id, it.icon) }
        DefaultAccounts.all.forEach { accountRepository.refreshIcon(it.id, it.icon) }
    }
}
