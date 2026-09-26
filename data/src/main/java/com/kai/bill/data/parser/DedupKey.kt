package com.kai.bill.data.parser

import com.kai.bill.domain.model.BillType
import java.security.MessageDigest

/**
 * 去重键生成：交易身份 = 金额 + 时间桶 + 类型 + 商户（md5），作为 `bill` / `pending_bill`
 * 唯一索引的值 —— 同一笔的第二次写入会因唯一索引冲突被 IGNORE。
 *
 * 不拿原文当身份：原文是**渠道**的属性，同一笔钱经通知 / 银行短信 / 更新型通知到达时
 * 文本各不相同，拿它当身份会把同一笔判成多笔。时间分桶则是为了容纳投递抖动
 * （通知 `postTime` 与真实扣款时刻不同、补投更晚），桶宽 [TIME_BUCKET_MILLIS] 即允许的抖动上限。
 *
 * NOTE: `t / 桶宽` 是**取整分桶**，不是「相差 1 秒内」—— 首条时间戳若不在整秒（真机几乎总是），
 * 第二条哪怕只晚几毫秒也可能跨格。因此落库前还有第二道按实际时间差判断的窗口
 * （[MATCH_WINDOW_MILLIS]）兜底，两道任一命中即判重复。
 *
 * 桶宽与窗口一起决定「少记」（同一秒两笔同额同类型真实消费被误合，且没有别的信号能区分）
 * 与「多记」（超出窗口的延迟重发被记两笔）的天平，改这两个值就是在两者之间挪。
 */
object DedupKey {

    /** 时间抖动窗口（毫秒）：整套去重里唯一按真机表现调的旋钮 */
    const val TIME_BUCKET_MILLIS = 1_000L

    /**
     * 时间窗匹配半径（毫秒）—— 与秒桶配合的第二道防线，按实际时间差判断、与秒格对齐无关。
     *
     * 判断区间是 `交易时间 ± 本值`，因此实际影响宽度为 `2 × 本值`。
     */
    const val MATCH_WINDOW_MILLIS = 3_000L

    /**
     * @param amountCents 金额（分）
     * @param tradeTimeMillis 交易时间（毫秒）；按 [TIME_BUCKET_MILLIS] 取整后参与，取整即为「1 秒内视为同一时刻」
     * @param type 账单类型（支出 / 收入 / 转账）；**待确认记录可能还判不出方向，此时传 null**
     * @param merchant 归一化商户，可为 null（当前解析链路尚未抽取商户，调用方一律传 null）
     */
    fun build(
        amountCents: Long,
        tradeTimeMillis: Long,
        type: BillType?,
        merchant: String?
    ): String {
        val bucket = tradeTimeMillis / TIME_BUCKET_MILLIS
        // 类型为空只出现在「方向都没判出来的待确认」这一种情况；
        // 不影响落库后与正式账单的比对 —— 那种条目本来也落不了库（缺方向）
        val raw = "$amountCents|$bucket|${type?.name.orEmpty()}|${merchant.orEmpty().trim()}"
        return md5(raw)
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
