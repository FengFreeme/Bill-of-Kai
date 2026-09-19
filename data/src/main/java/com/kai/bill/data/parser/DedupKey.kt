package com.kai.bill.data.parser

import com.kai.bill.domain.model.BillType
import java.security.MessageDigest

/**
 * 去重键生成。
 *
 * 交易的身份 = **金额 + 交易时间桶 + 类型 + 商户**，四者一起做 md5，作为
 * [com.kai.bill.core.db.entity.BillEntity.dedupHash] / `pending_bill.dedupHash`
 * 唯一索引的值：同一笔交易的第二次写入会因唯一索引冲突被 IGNORE。
 *
 * **为什么不再拿原文当身份**：原文不是交易的属性，而是**渠道**的属性 ——
 * 同一笔钱经支付宝通知、银行短信、更新型通知（App 改写文案后重发）到达时文本各不相同，
 * 拿它当身份会把同一笔判成多笔。改用交易自身的字段后，键与「文本长什么样」彻底解耦。
 *
 * **为什么时间要分桶**：交易时间在两次投递之间会抖动（通知 `postTime` 与真实扣款时刻不同、
 * 补投还会更晚），毫秒直接当身份等于永不重复。桶宽 [TIME_BUCKET_MILLIS] 就是允许的抖动上限：
 * 落在同一个桶内视为同一时刻，跨桶视为不同时刻。
 *
 * ⚠️ **两个方向的代价都写在这里，改桶宽就是在这两者之间挪**：
 * - **少记**：桶内出现两笔金额 / 类型 / 商户都相同的真实消费（如同一秒连下两单同款同价）
 *   会被判成同一笔，后到者被静默丢掉。概率极低但不是零，且没有别的信号能把它与
 *   「同一笔的两次投递」区分开 —— 这两件事在数据上完全同形。
 * - **多记**：同一笔交易若在秒格与 [MATCH_WINDOW_MILLIS] 之外再次投递（例如相隔 1 分钟的重发），
 *   会被记成两笔。
 *
 * 相较旧键（分钟桶 + 原文指纹）：多记的判定从「同一分钟内文本相同」收紧到「秒格 + 时间窗内」，
 * 换来的是「文案变一点就多一笔」这类问题彻底消失。
 *
 * ⚠️ **秒桶不是「1 秒窗口」**：`t / 桶宽` 是**取整分桶**，不是「相差 1 秒内」。两条投递能否合并，
 * 取决于是否落在同一个「秒格」里 —— 首条时间戳若不是整秒（真机上几乎总是），第二条哪怕只晚几毫秒，
 * 也可能正好跨过 `x.999 → (x+1).000` 而落进不同桶。因此落库前还有第二道**时间窗**兜底
 * （[MATCH_WINDOW_MILLIS]，按实际时间差判断，与秒格对齐无关），两道任一命中即判为重复。
 *
 * ⚠️ **跨渠道合并仍是近似**：同一笔消费的通知与银行短信，落在 [MATCH_WINDOW_MILLIS] 之内会被时间窗合并，
 * 超出则各记一笔，而两条通道的投递间隔有时会到几十秒级。
 */
object DedupKey {

    /**
     * 时间抖动窗口（毫秒）—— 整套去重里**唯一需要按真机表现调的旋钮**。
     *
     * - 调宽（如 5_000）：同一笔的延迟重发能合并，但「同额同类型的两笔真实消费」被误合的窗口也变大；
     * - 调窄（如 100）：误合几乎不可能，但系统延迟几秒重发同一通知时会多记一笔。
     */
    const val TIME_BUCKET_MILLIS = 1_000L

    /**
     * 时间窗匹配半径（毫秒）—— 与 [TIME_BUCKET_MILLIS] 配合的第二道去重旋钮。
     *
     * 秒桶只能识别「落在同一个秒格」的重复，是否跨界全看投递时刻的小数部分，等于看运气。
     * 因此落库前再用一次「金额 + 类型相同、交易时间差 ≤ 本值」的窗口查询兜底：
     * 它与秒格对齐无关，把「同一秒内必合并」从概率变成保证。
     *
     * 代价与桶宽同向：调宽能合并更多延迟重发，但「相近时间内的两笔同额同类型真实消费」被误合的
     * 窗口也随之变大 —— 判断区间是 `交易时间 ± 本值`，实际影响宽度为 `2 × 本值`。
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
