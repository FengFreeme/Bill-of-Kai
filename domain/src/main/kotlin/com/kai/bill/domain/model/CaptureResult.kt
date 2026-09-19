package com.kai.bill.domain.model

/**
 * 采集 / 解析一条原始文本后的结果状态。
 *
 * 由 `IngestPipeline` 返回，采集侧（`NotificationCapture` / 未来的短信扫描）把它编码成
 * 一个短字符串写进诊断（见 `CaptureState.captureLastResult`），UI 据此告诉用户
 * 「这条通知最后怎么了」——**这是用户唯一能自查解析是否正常的入口**，
 * 因此新增取值时必须同步更新展示文案（`PermissionCheckScreen` 的映射表）。
 *
 * @property passedAmountGate 是否通过了流水线第一级「金额闸门」。
 *           采集诊断**只记录通过闸门的结果**：没抽到金额的通知既不记账、也没有排查价值，
 *           全收进诊断只会把真正要看的记录顶掉（见 `CaptureState.captureRecent`）。
 *           判断依据放在这里而不是采集侧，是为了让「哪些结果有诊断价值」与结果定义待在一起，
 *           新增取值时不用回头找采集侧的分支。
 * @property accounted 是否产生了**终态记账结果**（已记账 / 判重 / 入待确认）。
 *           采集侧据此决定要不要把这条通知的 `sbnKey` 记入重放窗口（见 `RecentNotificationKeys`）：
 *           只有终态结果才值得拦住同一条通知更新后的重发；「抽不到金额 / 被排除 / 解析失败」
 *           都不是终态，它们的更新件（如「支付中」→「支付成功 ¥28.30」）必须继续放行，
 *           否则会把真正能记账的那一版丢掉。
 */
enum class CaptureResult(
    val passedAmountGate: Boolean,
    val accounted: Boolean
) {

    /** 解析成功并已写入账单表 */
    PARSED_AND_SAVED(passedAmountGate = true, accounted = true),

    /** 判定为重复，未再次入库（这笔钱已有记录，对这条通知而言同样是终态） */
    DUPLICATE_SKIPPED(passedAmountGate = true, accounted = true),

    /** 金额等字段不确定：**已写入待确认列表**，等用户在待审核页拍板 */
    NEEDS_REVIEW(passedAmountGate = true, accounted = true),

    /**
     * 待确认**未重复入队**：这笔在时间窗内已有相同记录，继续等用户在待审核页拍板。
     *
     * 与 [NEEDS_REVIEW] 的区别只在「有没有新入队」：对采集侧两者都是终态（[accounted] 为 true），
     * 但诊断里必须分开显示 —— 否则用户会以为待审核列表里又多了一条。
     */
    PENDING_DUPLICATE(passedAmountGate = true, accounted = true),

    /**
     * 识别到疑似账单，但抽不到金额。
     *
     * 静默丢弃，不落库也不提示：金额只在 App 详情页的通知（如招行动账）太多，
     * 每条都打扰用户会让人直接关掉自动记账。
     */
    NO_AMOUNT(passedAmountGate = false, accounted = false),

    /** 命中排除词（支付失败 / 待支付 / 营销文案等）：静默丢弃 */
    EXCLUDED(passedAmountGate = true, accounted = false),

    /**
     * 与账单语汇毫无关系，或落库过程出错。
     *
     * 注意它现在**只可能出现在金额闸门之后**：新流水线里「抽不到金额」已经由
     * [NO_AMOUNT] 单独承担，这个值退化为「方向/待确认写入异常」的防御分支。
     */
    PARSE_FAILED(passedAmountGate = true, accounted = false),

    /** 来源被白名单过滤，未进入解析 */
    FILTERED_OUT(passedAmountGate = false, accounted = false)
}
