package com.kai.bill.domain.model

/**
 * 采集 / 解析一条原始文本后的结果状态。
 *
 * M4+ 由 `BillIngestor` 返回；UI 用它决定是静默入库、提示待审核，还是记入失败列表。
 */
enum class CaptureResult {

    /** 解析成功并已写入账单表 */
    PARSED_AND_SAVED,

    /** 判定为重复，未再次入库 */
    DUPLICATE_SKIPPED,

    /** 金额/商户等字段不确定，需人工确认后入库 */
    NEEDS_REVIEW,

    /** 规则未命中或字段抽取失败 */
    PARSE_FAILED,

    /** 来源被白名单过滤，未进入解析 */
    FILTERED_OUT
}
