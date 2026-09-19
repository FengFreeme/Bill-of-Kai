package com.kai.bill.data.parser

import com.kai.bill.data.capture.signal.CategorySignal
import com.kai.bill.data.presets.PackageCategoryPreset
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.SourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分类结论的**来源**，同时表达了可信度顺序。
 *
 * 它有两个实际用途：
 * 1. 回填资格判定 —— 只有 [FALLBACK] 的结果才允许被后续信号改写；
 * 2. 诊断与排查 —— 「为什么这笔分到了餐饮」需要能回答是通知文本、页面文本还是包名猜的。
 */
enum class CategoryMatchOrigin {

    /** 通知正文命中分类词。通知是这笔交易的权威来源，优先级最高 */
    NOTIFICATION_TEXT,

    /** 类别信号的页面 / OCR 文本命中分类词（L2 / L3） */
    SIGNAL_TEXT,

    /** 类别信号的前台包名命中默认分类（仅有映射的商户 App，且仅支出方向） */
    SIGNAL_PACKAGE,

    /** 方向兜底（其他 / 其他收入 / 还款）—— 没有任何线索可用 */
    FALLBACK
}

/**
 * S3 分类路由的结论。
 *
 * @property categoryId 最终落库的分类 id（**永不为 null**：无命中时即方向兜底值）
 * @property origin 结论来源，见 [CategoryMatchOrigin]
 * @property matchedKeyword 命中的词；兜底与包名路径为 null
 */
data class CategoryMatch(
    val categoryId: Long,
    val origin: CategoryMatchOrigin,
    val matchedKeyword: String?
) {

    /** 是否落到了兜底分类 —— 只有兜底结论才允许被后续信号回填 */
    val isFallback: Boolean
        get() = origin == CategoryMatchOrigin.FALLBACK
}

/**
 * S3 分类路由：在**已定方向内**匹配商户 / 场景词，落到具体分类。
 *
 * 为什么必须限定方向：分类词表里有大量方向敏感的词（`会员` 是支出、`会员奖励` 是收入；
 * `京东` 与 `京东买菜` 同向不同类）。限定方向后，同一份词表在两个方向下不会互相污染，
 * 误分概率随方向数下降一个量级 —— 这正是「多级路由」相对「一条大正则」的核心收益。
 *
 * **四级优先级（不可调换）**：
 * ```
 * ① 通知正文命中  >  ② 信号文本命中  >  ③ 信号包名默认分类  >  ④ 方向兜底
 * ```
 * - ① 优先：通知正文是这笔交易自身的描述，比任何旁证都权威；把它放最前也保证
 *   引入信号后**既有行为完全不变** —— 原来命中的，现在仍然命中同一个分类；
 * - ② 其次：L2 读到的页面文本 / L3 的 OCR 文本比通知更具体（账单页有商户名）；
 * - ③ 再次：只知道用了哪个 App 时给出保守猜测（仅支出方向）；
 * - ④ 兜底：什么都不知道，落「其他 / 其他收入 / 还款」，等后续信号回填。
 *
 * 前两级共用 [KeywordMatcher] 与同一份词表，只是换了输入串 ——
 * 这样「多级路由」的排序口径（优先级 → 词长 → 位置）只有一份实现，
 * 不会出现「通知按一套、页面按另一套」的漂移。
 */
@Singleton
class CategoryRouter @Inject constructor() {

    /**
     * 路由一次分类。
     *
     * @param rawText 通知正文（L1）。信号不存在时它是唯一输入
     * @param direction 已由 [DirectionRouter] 定下的方向
     * @param source 账单来源，用于过滤声明了来源限定的词条
     * @param tables 词表容器；只用到 `category` 与 `reserved` 两组
     * @param signals 类别信号，**必须已按可信度排序**（见 `CategorySignalStore.recent`）；
     *                不传即退化为纯通知正文匹配，与引入信号前的行为完全一致
     * @return 结论，永不为 null；无任何线索时是方向兜底
     */
    fun resolve(
        rawText: String,
        direction: BillType,
        source: SourceType,
        tables: MatchKeywordTables,
        signals: List<CategorySignal> = emptyList()
    ): CategoryMatch {

        // ① 通知正文命中
        bestIn(rawText, direction, source, tables)?.let { hit ->
            return CategoryMatch(hit.categoryId, CategoryMatchOrigin.NOTIFICATION_TEXT, hit.keyword)
        }

        // ② 信号文本命中：signals 已排序，取首个命中的即最优
        signals.forEach { signal ->
            bestIn(signal.text, direction, source, tables)?.let { hit ->
                return CategoryMatch(hit.categoryId, CategoryMatchOrigin.SIGNAL_TEXT, hit.keyword)
            }
        }

        // ③ 信号包名默认分类（内部已限定仅支出方向）
        signals.forEach { signal ->
            PackageCategoryPreset.categoryIdOf(signal.packageName, direction)?.let { categoryId ->
                return CategoryMatch(categoryId, CategoryMatchOrigin.SIGNAL_PACKAGE, null)
            }
        }

        // ④ 方向兜底
        return CategoryMatch(fallbackOf(direction), CategoryMatchOrigin.FALLBACK, null)
    }

    /**
     * 方向的兜底分类。
     *
     * 取值来自 [com.kai.bill.data.presets.DefaultCategories]：
     * 12 其他 / 18 其他收入 / 20 还款。这三个 id 是硬约定，改它们会让历史账单掉进「未分类」。
     */
    fun fallbackOf(direction: BillType): Long = when (direction) {
        BillType.EXPENSE -> CATEGORY_OTHER_EXPENSE
        BillType.INCOME -> CATEGORY_OTHER_INCOME
        BillType.TRANSFER -> CATEGORY_REPAYMENT
    }

    /**
     * 该分类是否属于方向兜底值。
     *
     * **回填与建账判定都只认这三个**：命中过任意分类词（或已被回填过）的账单
     * 既不进候选、也不会被再次改写。
     */
    fun isFallback(categoryId: Long): Boolean = categoryId in fallbackIds

    /** 方向兜底分类 id 集合；信号决策用它筛出「允许被回填」的账单（见 `SignalReconciler.matchOnce`） */
    val fallbackIds: Set<Long> =
        setOf(CATEGORY_OTHER_EXPENSE, CATEGORY_OTHER_INCOME, CATEGORY_REPAYMENT)

    /** 方向内取最具体的一条命中；无命中返回 null */
    private fun bestIn(
        rawText: String,
        direction: BillType,
        source: SourceType,
        tables: MatchKeywordTables
    ): CategoryKeyword? = KeywordMatcher.best(
        candidates = tables.category.filter { it.direction == direction },
        rawText = rawText,
        source = source,
        reserved = tables.reserved
    )

    private companion object {
        const val CATEGORY_OTHER_EXPENSE = 12L
        const val CATEGORY_OTHER_INCOME = 18L
        const val CATEGORY_REPAYMENT = 20L
    }
}
