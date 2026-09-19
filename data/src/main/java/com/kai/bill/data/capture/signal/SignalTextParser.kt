package com.kai.bill.data.capture.signal

import com.kai.bill.data.parser.AmountNormalizer
import com.kai.bill.data.presets.AmountPattern
import com.kai.bill.domain.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 从页面 / OCR 文本中抽取建账所需的**金额**与**交易时间**。
 *
 * 与 L1 的 `AmountGate` 的差别（这是本类存在的全部理由）：
 * - `AmountGate` 面向**单笔通知**（文本短、通常只有一笔金额），取首个货币语境数字即可；
 * - 本解析器面向**整页文本**（可能同时出现订单号、余额、优惠、多笔金额），
 *   「位置靠前」完全不可靠 —— 支付页里第一个数字常常是优惠额或商品额。
 *   因此改用「**金额关键词邻近**」：只有紧挨着标签的数字才被认为是这笔交易的金额。
 *
 * 与金额闸门共用的部分：金额形态仍复用 [AmountPattern]（唯一真相），
 * 数字转「分」仍复用 [AmountNormalizer]，不在本类另写正则与换算 ——
 * 两份实现迟早会漂移，而漂移的结果就是错账。
 *
 * **宁可不抽，也不抽错**：无法确定时返回 null，让信号只走回填、不建账。
 */
object SignalTextParser {

    /**
     * 金额标签，**顺序即优先级**（越靠前越可信）。
     *
     * 「实付」排第一是因为它才是用户真正掏出去的钱：
     * 一张账单页常同时出现「商品金额 ¥25.00」和「实付金额 ¥28.30」，
     * 记账要的是后者，而不是位置更靠前的那个。
     */
    private val AMOUNT_ANCHORS = listOf(
        "实付",      // 0 最高：用户真正支付的钱
        "付款金额",   // 1
        "支付金额",   // 2
        "交易金额",   // 3
        "订单金额",   // 4
        "合计",      // 5
        "总计",      // 6
        "金额",      // 7 泛化兜底
        "支付",      // 8 最弱：可能出现在「原支付」这类描述里
        "收款"       // 9
    )

    /**
     * 标签回看窗口（字符数）。
     *
     * 只回看数字左侧 10 个字符：账单页的形态是「标签 + 金额」，
     * 标签总是紧贴数字。窗口开大反而会把隔壁金额的标签算进来 ——
     * 例如 `商品金额 ¥25.00 实付金额 ¥28.30` 中，两个金额只相隔几个字符，
     * 窗口一放宽，前一个金额就会认到后一个的「实付」。
     */
    private const val PREFIX_LOOKBACK = 10

    /**
     * 账户资产类标签：它们后面的数字是**余额 / 资产**，不是这笔交易的金额。
     *
     * 真机复现（2026-09-19）：支付宝「小荷包」页的 `总金额(元) 564.18` 被当成一笔 ¥564.18
     * 的交易建了账并弹卡。这类数字必须在抽取阶段就排除 —— 否则「全页唯一金额」规则
     * （规则 3）在只有余额的页面上照样会把它采纳。
     */
    private val BALANCE_HINTS = listOf(
        "总金额", "定期金额", "累计收益", "余额", "可用", "剩余", "账户", "总资产"
    )

    /** 完整日期时间：`2026-09-19 15:32` / `2026年9月19日 15:32` / `2026/9/19 15:32` */
    private val FULL_TIME = Regex(
        """(\d{4})\s*[-年/]\s*(\d{1,2})\s*[-月/]\s*(\d{1,2})\s*日?[\sT]+(\d{1,2}):(\d{2})"""
    )

    /** 省略年份：`09-19 15:32` / `9月19日 15:32`；年份由 [Clock] 推断 */
    private val SHORT_TIME = Regex(
        """(\d{1,2})\s*[-月/]\s*(\d{1,2})\s*日?[\sT]+(\d{1,2}):(\d{2})"""
    )

    /**
     * 从整页文本中抽取**这笔交易的金额**。
     *
     * 判定规则（按顺序）：
     * 1. 收集所有货币语境金额（形态见 [AmountPattern]，卡号尾号这类裸数字天然不入选）；
     * 2. 优先取「左侧紧邻金额标签」的那个，标签优先级见 [AMOUNT_ANCHORS]；
     * 3. 若无任何金额带标签，且全页**只有一个**货币语境金额，才采纳它；
     * 4. 其余情况返回 null（不猜）。
     *
     * @param text 已清洗为单行的页面 / OCR 文本
     * @return 金额（分，恒为正）；无法确定时返回 null
     */
    fun extractAmountCents(text: String): Long? {
        if (text.isBlank()) return null

        val matches = AmountPattern.ANY.findAll(text)
            .filterNot { isBalanceNumber(text, it.range) }
            .toList()
        if (matches.isEmpty()) return null

        // 规则 2：标签优先级最高者胜；同优先级由正则「从左到右」的顺序自然决定（先出现者胜）
        val anchored = matches
            .mapNotNull { match -> anchorPriority(text, match.range)?.let { match to it } }
            .minByOrNull { it.second }
            ?.first

        // 规则 3：没有标签时，只有「全页唯一金额」才敢采纳
        val chosen = anchored ?: matches.singleOrNull() ?: return null

        return AmountNormalizer.toCents(chosen.value.trim())?.takeIf { it > 0L }
    }

    /**
     * 从文本中抽取**交易时间**。
     *
     * 优先匹配带年份的完整写法；只有省略年份时才用 [clock] 推断年份 ——
     * 推断规则：按当前年份组装，若结果比「现在」晚出一天以上，则认为跨年，退回上一年
     * （预留一天容差是为了容忍设备时钟偏差与未来几分钟内的账单）。
     *
     * @param text 已清洗为单行的页面 / OCR 文本
     * @param clock 时间源，仅在解析「省略年份」的写法时使用
     * @return 交易时间（毫秒）；抽不到或日期非法时返回 null
     */
    fun extractTradeTimeMillis(text: String, clock: Clock): Long? {
        if (text.isBlank()) return null
        val zone = ZoneId.systemDefault()

        FULL_TIME.find(text)?.let { match ->
            val (year, month, day, hour, minute) = match.destructured
            return toMillis(zone, year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt())
        }

        val short = SHORT_TIME.find(text) ?: return null
        val (month, day, hour, minute) = short.destructured
        val now = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone)
        val monthValue = month.toInt()
        val dayValue = day.toInt()
        val hourValue = hour.toInt()
        val minuteValue = minute.toInt()

        // 先按「今年」组装；若明显落在未来（>1 天），说明是去年年底的时间点
        toMillis(zone, now.year, monthValue, dayValue, hourValue, minuteValue)?.let { millis ->
            return if (millis > now.toInstant().toEpochMilli() + ONE_DAY_MILLIS) {
                toMillis(zone, now.year - 1, monthValue, dayValue, hourValue, minuteValue)
            } else {
                millis
            }
        }
        return null
    }

    /**
     * 数字左侧紧邻的金额标签优先级；附近没有标签返回 null。
     *
     * 只看**左侧**：标签总在金额之前（`实付 ¥28.30`），
     * 而金额右侧的文字（`¥28.30 已优惠`）不属于这个数字。
     */
    private fun anchorPriority(text: String, amountRange: IntRange): Int? {
        val from = (amountRange.first - PREFIX_LOOKBACK).coerceAtLeast(0)
        val prefix = text.substring(from, amountRange.first)
        if (prefix.isBlank()) return null
        return AMOUNT_ANCHORS.indexOfFirst { prefix.contains(it) }.takeIf { it >= 0 }
    }

    /**
     * 这个数字是不是「账户资产」而非交易金额（左侧紧邻 [BALANCE_HINTS] 之一）。
     *
     * 「总金额」里的「金额」本身命中泛化标签，仅靠 [AMOUNT_ANCHORS] 拦不住，
     * 因此这里独立判一次，并在收集候选时就把它剔除。
     */
    private fun isBalanceNumber(text: String, amountRange: IntRange): Boolean {
        val from = (amountRange.first - PREFIX_LOOKBACK).coerceAtLeast(0)
        val prefix = text.substring(from, amountRange.first)
        return prefix.isNotBlank() && BALANCE_HINTS.any { prefix.contains(it) }
    }

    /** 组装为毫秒；日期非法（如 2 月 30 日）返回 null 而不是抛异常 */
    private fun toMillis(
        zone: ZoneId,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long? = runCatching {
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
}
