package com.kai.bill.data.repository

import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.AccountType
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Budget
import com.kai.bill.domain.model.BudgetPeriod
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.DailyMode
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.model.ImportSummary
import com.kai.bill.domain.model.SourceType
import com.kai.bill.domain.repository.AccountRepository
import com.kai.bill.domain.repository.BackupRepository
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.BudgetRepository
import com.kai.bill.domain.repository.CategoryRepository
import com.kai.bill.domain.time.Clock
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 备份文件标识：用于识别「这是不是本应用的备份」 */
private const val FORMAT_NAME = "bill-of-kai-backup"

/** 当前备份格式版本；导入时高于此值的文件会被拒绝，避免解析错乱 */
private const val FORMAT_VERSION = 1

/**
 * 备份仓储实现：JSON（`org.json`，Android 内置，不引入额外依赖）。
 *
 * 导出：把本机全部分类 / 账户 / 预算 / 账单拍平成 JSON，
 * **关联字段一律翻译成名称**（分类名 + 父分类名、账户名）。
 *
 * 导入：先按名称把分类与账户「找回或新建」，再用映射结果重建账单的外键；
 * 账单依靠 `dedupHash` 唯一索引天然去重，因此**可以反复导入同一份文件**。
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val clock: Clock
) : BackupRepository {

    override suspend fun export(): String {
        val categories = categoryRepository.observeAll().first()
        val accounts = accountRepository.observeAll().first()
        val budgets = budgetRepository.observeAll().first()
        val bills = billRepository.observeBills(
            range = DateRange(startMillis = 0L, endMillis = Long.MAX_VALUE),
            filter = null
        ).first()

        val categoriesById = categories.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }

        val root = JSONObject()
        root.put(FIELD_FORMAT, FORMAT_NAME)
        root.put(FIELD_VERSION, FORMAT_VERSION)
        root.put(FIELD_EXPORTED_AT, clock.nowMillis())
        root.put(FIELD_CATEGORIES, categoriesToJson(categories, categoriesById))
        root.put(FIELD_ACCOUNTS, accountsToJson(accounts))
        root.put(FIELD_BUDGETS, budgetsToJson(budgets, categoriesById))
        root.put(FIELD_BILLS, billsToJson(bills, categoriesById, accountsById))
        return root.toString(2)
    }

    override suspend fun import(json: String): ImportSummary {
        val root = runCatching { JSONObject(json) }.getOrElse {
            throw IOException("文件不是有效的 JSON，可能已损坏")
        }
        if (root.optString(FIELD_FORMAT) != FORMAT_NAME) {
            throw IOException("这不是「小凯记账」的备份文件")
        }
        val version = root.optInt(FIELD_VERSION, 0)
        if (version > FORMAT_VERSION) {
            throw IOException("备份文件来自更高版本（v$version），请先升级 App")
        }

        val categoriesCreated = restoreCategories(root)
        val categories = categoryRepository.observeAll().first()
        val categoriesById = categories.associateBy { it.id }
        val categoryIdByKey = categories.associate { c ->
            categoryKey(c.type.name, parentNameOf(c, categoriesById), c.name) to c.id
        }

        val accountsCreated = restoreAccounts(root)
        val accountIdByName = accountRepository.observeAll().first().associate { it.name to it.id }

        val budgetsRestored = restoreBudgets(root, categoryIdByKey)

        val (billsImported, billsSkipped) = restoreBills(
            root = root,
            categoryIdByKey = categoryIdByKey,
            accountIdByName = accountIdByName
        )

        return ImportSummary(
            billsImported = billsImported,
            billsSkipped = billsSkipped,
            categoriesCreated = categoriesCreated,
            accountsCreated = accountsCreated,
            budgetsRestored = budgetsRestored
        )
    }

    // ---- 导出 ----

    private fun categoriesToJson(
        categories: List<Category>,
        categoriesById: Map<Long, Category>
    ): JSONArray = JSONArray().apply {
        categories.forEach { category ->
            put(
                JSONObject().apply {
                    put(FIELD_NAME, category.name)
                    put(FIELD_ICON, category.icon)
                    put(FIELD_COLOR_HEX, category.colorHex)
                    put(FIELD_TYPE, category.type.name)
                    put(FIELD_PARENT_NAME, parentNameOf(category, categoriesById))
                    put(FIELD_SORT_ORDER, category.sortOrder)
                    put(FIELD_IS_SYSTEM, category.isSystem)
                }
            )
        }
    }

    private fun accountsToJson(accounts: List<Account>): JSONArray = JSONArray().apply {
        accounts.forEach { account ->
            put(
                JSONObject().apply {
                    put(FIELD_NAME, account.name)
                    put(FIELD_ICON, account.icon)
                    put(FIELD_TYPE, account.type.name)
                    put(FIELD_SORT_ORDER, account.sortOrder)
                    put(FIELD_IS_ARCHIVED, account.isArchived)
                }
            )
        }
    }

    private fun budgetsToJson(
        budgets: List<Budget>,
        categoriesById: Map<Long, Category>
    ): JSONArray = JSONArray().apply {
        budgets.forEach { budget ->
            val category = budget.categoryId?.let { categoriesById[it] }
            put(
                JSONObject().apply {
                    put(FIELD_CATEGORY_NAME, category?.name)
                    put(FIELD_CATEGORY_PARENT_NAME, category?.let { parentNameOf(it, categoriesById) })
                    put(FIELD_PERIOD, budget.period.name)
                    put(FIELD_AMOUNT_CENTS, budget.amountCents)
                    put(FIELD_START_DAY, budget.startDay)
                    put(FIELD_DAILY_MODE, budget.dailyMode.name)
                    put(FIELD_DAILY_AMOUNT_CENTS, budget.dailyAmountCents)
                    put(FIELD_CARRY_OVER, budget.carryOver)
                    put(FIELD_ENABLED, budget.enabled)
                }
            )
        }
    }

    private fun billsToJson(
        bills: List<Bill>,
        categoriesById: Map<Long, Category>,
        accountsById: Map<Long, Account>
    ): JSONArray = JSONArray().apply {
        bills.forEach { bill ->
            val category = categoriesById[bill.categoryId]
            put(
                JSONObject().apply {
                    put(FIELD_AMOUNT_CENTS, bill.amountCents)
                    put(FIELD_TYPE, bill.type.name)
                    put(FIELD_COUNT_IN_STATS, bill.countInStats)
                    put(FIELD_CATEGORY_NAME, category?.name)
                    put(FIELD_CATEGORY_PARENT_NAME, category?.let { parentNameOf(it, categoriesById) })
                    put(FIELD_ACCOUNT_NAME, bill.accountId?.let { accountsById[it]?.name })
                    put(FIELD_MERCHANT, bill.merchant)
                    put(FIELD_NOTE, bill.note)
                    put(FIELD_TRADE_TIME, bill.tradeTimeMillis)
                    put(FIELD_SOURCE, bill.source.name)
                    // 通知/短信原文：规则更新后可据此批量重解析，故一并导出
                    put(FIELD_RAW_TEXT, bill.rawText)
                    put(FIELD_DEDUP_HASH, bill.dedupHash)
                    put(FIELD_CREATED_AT, bill.createdAt)
                    put(FIELD_UPDATED_AT, bill.updatedAt)
                }
            )
        }
    }

    // ---- 导入 ----

    /** 按名称补齐本机缺失的分类；返回新建数量。一级先于二级处理，保证父 id 已就绪。 */
    private suspend fun restoreCategories(root: JSONObject): Int {
        val existing = categoryRepository.observeAll().first()
        val existingById = existing.associateBy { it.id }
        val knownKeys = existing.associate { c ->
            categoryKey(c.type.name, parentNameOf(c, existingById), c.name) to c.id
        }.toMutableMap()

        val nodes = root.arrayOf(FIELD_CATEGORIES)
        var created = 0
        nodes
            .sortedBy { if (it.isNull(FIELD_PARENT_NAME)) 0 else 1 }
            .forEach { obj ->
                val name = obj.optString(FIELD_NAME)
                if (name.isBlank()) return@forEach
                val type = obj.enumOrDefault(FIELD_TYPE, BillType.EXPENSE)
                val parentName = obj.stringOrNull(FIELD_PARENT_NAME)
                val key = categoryKey(type.name, parentName, name)
                if (knownKeys.containsKey(key)) return@forEach

                val parentId = parentName?.let { knownKeys[categoryKey(type.name, null, it)] }
                val newId = categoryRepository.insert(
                    Category(
                        name = name,
                        icon = obj.stringOrNull(FIELD_ICON),
                        colorHex = obj.optString(FIELD_COLOR_HEX, DEFAULT_COLOR),
                        type = type,
                        parentId = parentId,
                        sortOrder = obj.optInt(FIELD_SORT_ORDER, 0),
                        // 备份里的系统分类在本机应已存在并命中；真正新建的一律按可删除处理
                        isSystem = false
                    )
                )
                if (newId > 0L) {
                    knownKeys[key] = newId
                    created++
                }
            }
        return created
    }

    /** 按名称补齐本机缺失的账户；返回新建数量。 */
    private suspend fun restoreAccounts(root: JSONObject): Int {
        val knownNames = accountRepository.observeAll().first().map { it.name }.toMutableSet()
        var created = 0
        root.arrayOf(FIELD_ACCOUNTS).forEach { obj ->
            val name = obj.optString(FIELD_NAME)
            if (name.isBlank() || !knownNames.add(name)) return@forEach
            val newId = accountRepository.insert(
                Account(
                    name = name,
                    icon = obj.stringOrNull(FIELD_ICON),
                    type = obj.enumOrDefault(FIELD_TYPE, AccountType.CASH),
                    sortOrder = obj.optInt(FIELD_SORT_ORDER, 0),
                    isArchived = obj.optBoolean(FIELD_IS_ARCHIVED, false)
                )
            )
            if (newId > 0L) created++
        }
        return created
    }

    /** 恢复预算：同 categoryId 视为同一条，存在则覆盖、不存在则新增。 */
    private suspend fun restoreBudgets(
        root: JSONObject,
        categoryIdByKey: Map<String, Long>
    ): Int {
        val nodes = root.arrayOf(FIELD_BUDGETS)
        if (nodes.isEmpty()) return 0

        val local = budgetRepository.observeAll().first()
        var restored = 0
        nodes.forEach { obj ->
            val categoryName = obj.stringOrNull(FIELD_CATEGORY_NAME)
            val parentName = obj.stringOrNull(FIELD_CATEGORY_PARENT_NAME)
            val categoryId = if (categoryName == null) {
                null
            } else {
                categoryIdByKey[categoryKey(obj.optString(FIELD_TYPE, BillType.EXPENSE.name), parentName, categoryName)]
                    ?: return@forEach
            }
            val existing = local.firstOrNull { it.categoryId == categoryId }
            budgetRepository.upsert(
                Budget(
                    id = existing?.id ?: 0L,
                    categoryId = categoryId,
                    period = obj.enumOrDefault(FIELD_PERIOD, BudgetPeriod.MONTHLY),
                    amountCents = obj.optLong(FIELD_AMOUNT_CENTS, 0L),
                    startDay = obj.optInt(FIELD_START_DAY, 1),
                    dailyMode = obj.enumOrDefault(FIELD_DAILY_MODE, DailyMode.ELASTIC),
                    dailyAmountCents = obj.optLong(FIELD_DAILY_AMOUNT_CENTS, 0L),
                    carryOver = obj.optBoolean(FIELD_CARRY_OVER, false),
                    enabled = obj.optBoolean(FIELD_ENABLED, true)
                )
            )
            restored++
        }
        return restored
    }

    /** 写入账单；重复（dedupHash 命中唯一索引）或分类缺失的会被跳过并计数。 */
    private suspend fun restoreBills(
        root: JSONObject,
        categoryIdByKey: Map<String, Long>,
        accountIdByName: Map<String, Long>
    ): Pair<Int, Int> {
        var imported = 0
        var skipped = 0
        root.arrayOf(FIELD_BILLS).forEach { obj ->
            val type = obj.enumOrDefault(FIELD_TYPE, BillType.EXPENSE)
            val categoryName = obj.optString(FIELD_CATEGORY_NAME)
            val parentName = obj.stringOrNull(FIELD_CATEGORY_PARENT_NAME)
            val categoryId = categoryIdByKey[categoryKey(type.name, parentName, categoryName)]
            if (categoryId == null) {
                // 分类映射不出来：宁可跳过，也不能把账单挂到错误的分类上
                skipped++
                return@forEach
            }

            val saved = billRepository.save(
                Bill(
                    amountCents = obj.optLong(FIELD_AMOUNT_CENTS, 0L),
                    type = type,
                    countInStats = obj.optBoolean(FIELD_COUNT_IN_STATS, true),
                    categoryId = categoryId,
                    accountId = obj.stringOrNull(FIELD_ACCOUNT_NAME)?.let { accountIdByName[it] },
                    merchant = obj.stringOrNull(FIELD_MERCHANT),
                    note = obj.stringOrNull(FIELD_NOTE),
                    tradeTimeMillis = obj.optLong(FIELD_TRADE_TIME, 0L),
                    source = obj.enumOrDefault(FIELD_SOURCE, SourceType.MANUAL),
                    rawText = obj.stringOrNull(FIELD_RAW_TEXT),
                    // 缺失去重键时补一个唯一的：空串会互相撞唯一索引，导致只有第一条能入库
                    dedupHash = obj.optString(FIELD_DEDUP_HASH).takeIf { it.isNotBlank() }
                        ?: "import|${UUID.randomUUID()}",
                    createdAt = obj.optLong(FIELD_CREATED_AT, clock.nowMillis()),
                    updatedAt = obj.optLong(FIELD_UPDATED_AT, clock.nowMillis())
                )
            )
            // save 返回 -1 表示 dedupHash 冲突被跳过 —— 这正是「可反复导入」的基础
            if (saved >= 0L) imported++ else skipped++
        }
        return imported to skipped
    }

    // ---- 工具 ----

    private fun parentNameOf(category: Category, categoriesById: Map<Long, Category>): String? =
        category.parentId?.let { categoriesById[it]?.name }

    private fun categoryKey(type: String, parentName: String?, name: String): String =
        "$type|${parentName.orEmpty()}|$name"

    private companion object {
        const val DEFAULT_COLOR = "#5CB34D"

        const val FIELD_FORMAT = "format"
        const val FIELD_VERSION = "version"
        const val FIELD_EXPORTED_AT = "exportedAt"
        const val FIELD_CATEGORIES = "categories"
        const val FIELD_ACCOUNTS = "accounts"
        const val FIELD_BUDGETS = "budgets"
        const val FIELD_BILLS = "bills"

        const val FIELD_NAME = "name"
        const val FIELD_ICON = "icon"
        const val FIELD_COLOR_HEX = "colorHex"
        const val FIELD_TYPE = "type"
        const val FIELD_PARENT_NAME = "parentName"
        const val FIELD_CATEGORY_NAME = "categoryName"
        const val FIELD_CATEGORY_PARENT_NAME = "categoryParentName"
        const val FIELD_ACCOUNT_NAME = "accountName"
        const val FIELD_SORT_ORDER = "sortOrder"
        const val FIELD_IS_SYSTEM = "isSystem"
        const val FIELD_IS_ARCHIVED = "isArchived"
        const val FIELD_PERIOD = "period"
        const val FIELD_AMOUNT_CENTS = "amountCents"
        const val FIELD_START_DAY = "startDay"
        const val FIELD_DAILY_MODE = "dailyMode"
        const val FIELD_DAILY_AMOUNT_CENTS = "dailyAmountCents"
        const val FIELD_CARRY_OVER = "carryOver"
        const val FIELD_ENABLED = "enabled"
        const val FIELD_COUNT_IN_STATS = "countInStats"
        const val FIELD_MERCHANT = "merchant"
        const val FIELD_NOTE = "note"
        const val FIELD_TRADE_TIME = "tradeTimeMillis"
        const val FIELD_SOURCE = "source"
        const val FIELD_RAW_TEXT = "rawText"
        const val FIELD_DEDUP_HASH = "dedupHash"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}

/** 读取 JSON 数组字段；缺失时返回空列表而非抛异常 */
private fun JSONObject.arrayOf(key: String): List<JSONObject> {
    val array: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

/**
 * 读取字符串字段，空值 / 空白 一律视为 null。
 *
 * `JSONObject.put(key, null)` 会移除该键，所以判空要用 [JSONObject.isNull]。
 */
private fun JSONObject.stringOrNull(key: String): String? {
    if (isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

/** 枚举反序列化：遇到未知值回退默认，避免旧/脏数据让整次导入失败 */
private inline fun <reified T : Enum<T>> JSONObject.enumOrDefault(key: String, fallback: T): T =
    optString(key).takeIf { it.isNotBlank() }
        ?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
        ?: fallback
