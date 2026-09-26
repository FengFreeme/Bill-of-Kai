package com.kai.bill.data.capture.notification

/**
 * 通知侧诊断文本的组装。
 *
 * 「最近采集结果」与「最近识别记录」共用同一个弹窗组件，识别记录侧的原文带了
 * `识别方式 微信专属 ｜ 来源 无障碍 ｜ …` 前缀，列表右侧因此能显示渠道标签。
 * 本类为通知侧补上**逐字对齐**识别记录的同格式前缀，
 * 于是展示侧（弹窗里的 `extractRecognitionMethod`）无需任何改动。
 *
 * 前缀还会保护最关键的信息：列表只截断展示前 200 字，而通知原文（尤其广告短信）可能很长。
 */
internal object NotificationDiagnosticText {

    /** 与识别记录侧（`CategorySignalRecorder.SUMMARY_SEP`）保持一致 */
    private const val SUMMARY_SEP = " ｜ "

    private const val METHOD_KEY = "识别方式"

    private const val SOURCE_KEY = "来源"

    private const val SOURCE_NOTIFICATION = "通知"

    /**
     * 组装诊断原文：`识别方式 微信专属 ｜ 来源 通知 ｜ <原文>`。
     *
     * @param packageName 通知来源包名，用于判定渠道
     * @param rawText 抽取并归一化后的通知文本
     */
    fun withChannelSummary(packageName: String, rawText: String): String =
        "$METHOD_KEY ${channelLabel(packageName)}$SUMMARY_SEP$SOURCE_KEY $SOURCE_NOTIFICATION" +
            "$SUMMARY_SEP$rawText"

    /**
     * 渠道标签。取值与识别记录侧同一套词汇，方便用户在两份列表里用同一种看法：
     * 支付宝 / 微信 / 银行 / 短信各有自己的采集闸门（见 [NotificationContentGate]），
     * 因此对通知侧而言「专属」也是成立的。
     */
    fun channelLabel(packageName: String): String = when (packageName) {
        in NotificationWhitelist.ALIPAY -> "支付宝专属"
        in NotificationWhitelist.WECHAT -> "微信专属"
        in NotificationWhitelist.BANKS -> "银行专属"
        in NotificationWhitelist.SMS -> "短信专属"
        else -> "通用"
    }
}
