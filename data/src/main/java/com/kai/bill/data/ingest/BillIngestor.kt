package com.kai.bill.data.ingest

import com.kai.bill.domain.model.CaptureResult
import com.kai.bill.domain.model.SourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 所有采集来源的统一入口（对外签名保持不变）。
 *
 * 解析职责已整体下沉到 [IngestPipeline]（四级路由），本类只是一层薄适配：
 * - 保留 `ingest(rawText, source)` 这一既有签名，短信补扫（M5）与将来的来源无需改动；
 * - 采集路径不做任何 DB 读：解析全走常量词表，`parse_rule` 表留给 P1 改造成 `match_keyword` 词表。
 */
@Singleton
class BillIngestor @Inject constructor(
    private val pipeline: IngestPipeline
) {

    /**
     * @param rawText 已清洗成单行的原文
     * @param source 采集来源
     * @param packageName 通知来源包名（账户路由用；短信传 null）
     * @param eventTimeMillis 事件发生时间（通知 `postTime`），比处理时刻更接近真实交易时间
     */
    suspend fun ingest(
        rawText: String,
        source: SourceType,
        packageName: String? = null,
        eventTimeMillis: Long? = null
    ): CaptureResult = pipeline.run(
        rawText = rawText,
        source = source,
        packageName = packageName,
        eventTimeMillis = eventTimeMillis
    )

    /**
     * 与 [ingest] 同一条流水线，额外返回落库账单 id。
     *
     * 供 `SignalReconciler` 使用：确认卡片要按主键让用户改 / 撤刚记下的那一笔。
     *
     * @see IngestPipeline.runOutcome
     */
    suspend fun ingestOutcome(
        rawText: String,
        source: SourceType,
        packageName: String? = null,
        eventTimeMillis: Long? = null
    ): IngestOutcome = pipeline.runOutcome(
        rawText = rawText,
        source = source,
        packageName = packageName,
        eventTimeMillis = eventTimeMillis
    )
}
