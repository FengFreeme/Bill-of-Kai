package com.kai.bill.data.parser

import com.kai.bill.data.presets.AmountPattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1 金额闸门：抽金额，抽不到就直接结束。
 *
 * 这是流水线唯一的硬闸门 —— 没有金额的文本（信用卡账单提醒、活动推送、聊天）
 * 不该进入方向判定，更不该落到待确认列表里打扰用户。
 *
 * **取第一个匹配的金额，不做打分**。理由：
 * - 动账通知实际只带一个金额；多金额基本只出现在营销文案里（「满50元减10元」），
 *   而那类文案已被 [com.kai.bill.data.presets.DefaultMatchKeywords.exclude] 整条拦掉；
 * - 「第一个」与旧实现 `Regex.find` 的语义一致，换引擎不会引入难查的行为差异；
 * - 单一候选时打分毫无收益，却让行为变得难预测。
 *
 * 金额形态见 [AmountPattern]：必须是货币语境中的数字，
 * 因此「您账户2836于…」的卡号尾号不会被读成金额。
 */
@Singleton
class AmountGate @Inject constructor() {

    /**
     * @param rawText 已清洗成单行的通知原文
     * @return 金额（分，恒为正）；没有金额返回 null
     */
    fun extract(rawText: String): Long? =
        AmountPattern.ANY.find(rawText)
            ?.value
            ?.trim()
            ?.let(AmountNormalizer::toCents)
            ?.takeIf { it > 0L }
}
