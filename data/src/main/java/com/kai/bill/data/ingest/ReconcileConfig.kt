package com.kai.bill.data.ingest

/**
 * 信号决策的可调参数。
 *
 * 做成可注入的配置对象而不是散落的常量，有两个原因：
 * 1. 这两个窗口在设计文档里被明确标注为「**需真机实测后调**」的旋钮，集中一处才好调；
 * 2. 单测需要把宽限期设为 0 —— 否则每个用例都得真的等 20 秒。
 *
 * @property matchWindowMillis 匹配与回填的搜索半径（毫秒），区间为「有效交易时间 ± 本值」
 * @property graceDelayMillis 建账前的宽限期（毫秒）；**0 表示不等待**。
 *           仅用于兜住「页面信号比通知还早到达」的竞态，默认 0（详见下方常量说明）
 * @property maxRawTextChars 写入账单 `rawText` 的截断长度。
 *           信号文本是整页内容，不截断会让库里的原文体积失控，也放大隐私暴露面
 */
data class ReconcileConfig(
    val matchWindowMillis: Long = DEFAULT_MATCH_WINDOW_MILLIS,
    val graceDelayMillis: Long = DEFAULT_GRACE_DELAY_MILLIS,
    val maxRawTextChars: Int = DEFAULT_MAX_RAW_TEXT_CHARS
) {

    companion object {

        /** ±5 分钟：用户指定的匹配窗口 */
        const val DEFAULT_MATCH_WINDOW_MILLIS = 5 * 60_000L

        /**
         * **0 = 不等待**（默认）。
         *
         * 用户的真实使用形态是「付款之后（或收到通知之后）再点开账单详情页」，此时通知早已落库：
         * - 通知已记过 → 首次匹配即命中 → 回填分类 / 判重，**不会多记一笔**；
         * - 本来就没有通知 → 首次匹配无候选 → **直接落库**。
         *
         * 两种情况都不需要等，所以默认 0。真正需要等待的只有「页面信号比通知还早约 0.5 秒」
         * 这一种竞态，而它还有落库前的「金额 + 类型 + ±3 秒时间窗」判重兜底（见 `DedupKey`）。
         * 真机上若真观察到该竞态导致双记，把本值调大即可 —— [IngestEvents] 会保证通知一到就醒，
         * 不会真的睡满。
         */
        const val DEFAULT_GRACE_DELAY_MILLIS = 0L

        /** 写入账单的原文上限；比诊断用的截断（200 字）宽一些，够复盘分类依据 */
        const val DEFAULT_MAX_RAW_TEXT_CHARS = 500
    }
}
