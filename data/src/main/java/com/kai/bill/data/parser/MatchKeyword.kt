package com.kai.bill.data.parser

import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.PendingReason
import com.kai.bill.domain.model.SourceType

/**
 * 方向级词条命中后的走向。
 *
 * 把「命中这个词之后这笔钱算什么」收敛成一个枚举，是为了让 [DirectionRouter] 与
 * [com.kai.bill.data.ingest.IngestPipeline] 的判定保持**穷尽**：将来新增一类走向时，
 * 所有 `when` 都会在编译期报错，而不是让某个分支用默认值兜底 ——
 * `?: BillType.EXPENSE` 这类兜底正是「余额宝收益被记成支出」的根源。
 *
 * @property type 直接落库时的账单类型；需要人工确认或排除时为 null
 * @property pendingReason 需要人工确认时的原因；仅待确认类为非 null
 * @property suggestedType 待确认时的**建议方向**：用户点「接受建议」后落库用的类型。
 *           与 [type] 分开是因为「建议」和「已定」是两件事 —— 前者只是一种倾向，
 *           后者才是解析结论。判不出方向时没有建议（null），因为那时连倾向都给不出来
 */
enum class MatchRoute(
    val type: BillType?,
    val pendingReason: PendingReason?,
    val suggestedType: BillType? = null
) {

    /** 支出：直接落库并计入统计 */
    EXPENSE(BillType.EXPENSE, null),

    /** 收入：直接落库并计入统计 */
    INCOME(BillType.INCOME, null),

    /** 转账（目前只有还款类）：语义明确，直接落库但**不计入统计** */
    TRANSFER(BillType.TRANSFER, null),

    /**
     * 自己的钱搬家（转出 / 提现 / 取现 / 代付）：可能是账户互转，交人工确认。
     *
     * 建议方向给 [BillType.TRANSFER]：钱确实在账户之间移动了，落成转账最贴近事实；
     * 是否计入统计仍由待确认规则统一压成 false。
     */
    SELF_TRANSFER(null, PendingReason.SELF_TRANSFER, BillType.TRANSFER),

    /** 红包：收 / 发与是否人情往来因人而异，交人工确认。建议按收入落账，但不计入统计 */
    GIFT(null, PendingReason.GIFT, BillType.INCOME),

    /**
     * 泛化入账（入账 / 转入 / 存入 / 到账）：可能是收入，也可能只是账户互转，交人工确认。
     *
     * 建议按收入落账、但不计入统计 —— 这样即使用户直接接受，也不会虚增本月收入。
     */
    GENERIC_INBOUND(null, PendingReason.GENERIC_INBOUND, BillType.INCOME),

    /**
     * 排除词（失败 / 预告 / 营销）：静默丢弃。
     *
     * 这类文本常带金额（「满 50 元减 10 元」「将于 20 日自动扣款 ¥88」），
     * 若不单独拦截会被记成真实收支，因此它必须**优先于方向词**判定。
     */
    EXCLUDE(null, null)
}

/**
 * 词条共同契约：路由器只关心「怎么比大小」与「是否适用于该来源」。
 *
 * 抽出来的意义：方向词与分类词的排序规则完全一致（优先级降序 → 词长降序 → 位置靠前），
 * 统一成泛型实现后只有一份排序代码，不会出现「方向词按一套规则、分类词按另一套」的漂移。
 */
interface Matchable {

    val keyword: String

    /** 降序比较；相同优先级时取**更长**的词（更具体者胜） */
    val priority: Int

    /** 是否适用于该来源；声明了来源限定的词只在其来源下参与匹配 */
    fun appliesTo(source: SourceType): Boolean
}

/**
 * 方向级词条：交易动作词 → 走向。
 *
 * 只放「动作」（支付 / 收款 / 扣款 / 转出…），不放商户与场景词 ——
 * 商户词属于分类级（见 [CategoryKeyword]），两者职责分开才能让
 * 「先定方向、再在方向内选分类」的路由成立。
 *
 * @property source 限定来源；null = 不限（如「充值」在微信是账户互转、在运营商侧是支出）
 */
data class DirectionKeyword(
    override val keyword: String,
    val route: MatchRoute,
    override val priority: Int = 0,
    val source: SourceType? = null
) : Matchable {

    override fun appliesTo(source: SourceType): Boolean = this.source == null || this.source == source
}

/**
 * 分类级词条：商户 / 场景词 / 口语化表达 → 分类 id。
 *
 * @property categoryId 目标分类；**一级与二级 id 都可以**（沿用旧 `CategoryKeywordMatcher` 的约定），
 *           统计与饼图按分类聚合，两种粒度混用不会算错，UI 的分类选择器需要允许选任意层级
 * @property direction 该词所属方向；由 [CategoryRouter] 用来把搜索范围收窄到当前方向，
 *           避免「同一个词在两个方向下都命中」的歧义。P1 词表入库后可由 `category.type` 反推
 * @property source 同 [DirectionKeyword.source]
 */
data class CategoryKeyword(
    override val keyword: String,
    val categoryId: Long,
    val direction: BillType,
    override val priority: Int = 0,
    val source: SourceType? = null
) : Matchable {

    override fun appliesTo(source: SourceType): Boolean = this.source == null || this.source == source
}

/**
 * 三组词表 + 一张保留词表的容器 —— 流水线只认这个结构，不关心它是常量还是数据库。
 *
 * P0 由 [com.kai.bill.data.presets.DefaultMatchKeywords] 提供常量；
 * P1 词表入库后改成「仓储观察 + 内存缓存」提供同一结构，四级阶段类与流水线零改动。
 *
 * @property direction 方向动作词（含待确认类与还款类）
 * @property exclude 排除词（失败 / 预告 / 营销），必须优先于 [direction] 判定
 * @property category 分类词（商户 / 场景）
 * @property reserved 保留词（品牌 / 应用名，如「支付宝」「微信支付」）。**不参与任何走向判定**，
 *           只在匹配前把出现区间标记为「已占用」，让落在其中的短词失效 ——
 *           否则「支付」这种短词会命中「支付宝」里的子串，把每一条支付宝通知都判成支出。
 *           这是「先认长词、短词不许进已占区间」的通用修法，比逐个删词可靠：
 *           将来往词表里加任何短词，都不会再被更长的品牌名误触发。
 */
data class MatchKeywordTables(
    val direction: List<DirectionKeyword>,
    val exclude: List<DirectionKeyword>,
    val category: List<CategoryKeyword>,
    val reserved: List<String>
)
