package com.kai.bill.data.ingest

import com.kai.bill.data.parser.AmountNormalizer
import com.kai.bill.data.parser.CategoryKeywordMatcher
import com.kai.bill.data.parser.DedupKey
import com.kai.bill.data.parser.FieldExtractor
import com.kai.bill.data.parser.RuleEngine
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.CaptureResult
import com.kai.bill.domain.model.ParseRule
import com.kai.bill.domain.model.SourceType
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.ParseRuleRepository
import com.kai.bill.data.notify.BillSavedNotifier
import com.kai.bill.domain.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 所有来源统一入口：解析 → 去重 → 落库。
 *
 * 规则来自 [ParseRuleRepository]（已入库，可热更新）。为降低每次采集的 DB 读取开销，
 * 在 `init` 中订阅已启用规则并缓存在内存；规则变更（含重新播种）会自动刷新缓存。
 *
 * 解析失败策略：
 * - 无规则命中 → [CaptureResult.PARSE_FAILED]（不落库；后续可由短信补扫 / 手动记账兜底）
 * - 命中规则但金额缺失 → [CaptureResult.NEEDS_REVIEW]
 * - 命中并抽到金额 → 构造 Bill 落库；若 [com.kai.bill.core.db.entity.BillEntity.dedupHash]
 *   唯一索引冲突（insert IGNORE 返回 -1）→ [CaptureResult.DUPLICATE_SKIPPED]
 */
@Singleton
class BillIngestor @Inject constructor(
    private val parseRuleRepository: ParseRuleRepository,
    private val billRepository: BillRepository,
    private val ruleEngine: RuleEngine,
    private val fieldExtractor: FieldExtractor,
    private val clock: Clock,
    private val billSavedNotifier: BillSavedNotifier
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _enabledRules = MutableStateFlow<List<ParseRule>>(emptyList())
    private val enabledRules = _enabledRules.asStateFlow()

    init {
        parseRuleRepository.observeEnabled()
            .onEach { _enabledRules.value = it }
            .launchIn(scope)
    }

    suspend fun ingest(rawText: String, source: SourceType): CaptureResult {
        val rules = enabledRules.value.filter { it.source == source }
        val rule = ruleEngine.match(rawText, source, rules) ?: return CaptureResult.PARSE_FAILED

        val fields = fieldExtractor.extract(rawText, rule)
        val amountCents = AmountNormalizer.toCents(fields.amountText) ?: return CaptureResult.NEEDS_REVIEW

        // 命中商户 / 场景词时，用映射里的「二级分类 + 收支类型」覆盖规则默认（多为「其他支出 / 支出」），
        // 让自动账单按用户整理的收支文案库归类，并修正收入被错记成支出的问题。
        val keywordHit = CategoryKeywordMatcher.match(rawText)
        val categoryId = keywordHit?.first ?: rule.defaultCategoryId ?: 12L
        val billType = keywordHit?.second ?: BillType.EXPENSE

        val now = clock.nowMillis()
        val dedupHash = DedupKey.build(amountCents, now, fields.merchant)

        val bill = Bill(
            amountCents = amountCents,
            type = billType,
            countInStats = true,
            categoryId = categoryId,
            accountId = rule.defaultAccountId,
            merchant = fields.merchant,
            note = null,
            tradeTimeMillis = now,
            source = source,
            rawText = rawText,
            dedupHash = dedupHash,
            createdAt = now,
            updatedAt = now
        )

        val savedId = billRepository.save(bill)
        return if (savedId == -1L) {
            CaptureResult.DUPLICATE_SKIPPED
        } else {
            billSavedNotifier.notifySaved(amountCents, billType, source)
            CaptureResult.PARSED_AND_SAVED
        }
    }
}
