package com.kai.bill.data.capture.notification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「同一条系统通知」的重放窗口。
 *
 * 系统通知的唯一键 [android.service.notification.StatusBarNotification.key]（见 [RawCaptureEvent.sbnKey]）
 * 在 App **更新同一条通知**（用相同 id 重新 notify）时保持不变。最典型的更新型通知是
 * 「支付中 → 支付成功」：两次投递文案不同、时间也不同，但其实是同一条通知的两次投递。
 *
 * 为什么单靠账单表的 `dedupHash` 拦不住它：`dedupHash` 只看「金额 + 秒级时间桶 + 类型 + 商户」，
 * 两版文案若金额相同、投递时间又跨出了秒桶（更新常发生在数秒之后），就会被记成两笔。
 * 这一层用 key 把这种重放挡在解析之前，与 `dedupHash` 互补：
 * - `dedupHash`：不同通知描述同一笔钱（跨通知、跨渠道）；
 * - 本类：同一条通知被重复投递（同 key）。
 *
 * **只对「已产生终态结果」的通知设防**（登记动作由 [remember] 的调用方在拿到结果后触发）：
 * 只有已经记账 / 判重 / 入过待确认的投递，其后续更新才需要拦。反过来，
 * 「抽不到金额」「被排除」「解析失败」的版本若也登记，会把真正能记账的那一版
 * （如「支付中」→「支付成功 ¥28.30」）一并丢掉 —— 漏记比重复更严重。
 *
 * **内存态、不落盘**：窗口只有 [TTL_MILLIS] 量级，进程重启后失效最多让极少数重放重复一次，
 * 不值得为它引入 DataStore 的写放大。
 *
 * ⚠️ **已知取舍**：[isDuplicate] 只按 key 判断，不比对内容。若某个 App 用**同一个 key**
 * 反复推送**不同交易**（如「最近一笔」这类常驻提醒），落在 [TTL_MILLIS] 窗口内的后一笔会被误挡。
 * 因此 [TTL_MILLIS] 是这里唯一的旋钮，且刻意取小：窗口越短，误挡真实交易的概率越低，
 * 能拦住的更新重发也越少。
 */
@Singleton
class RecentNotificationKeys @Inject constructor() {

    /**
     * key → 最近一次产生终态结果的投递时间。
     *
     * 用 [LinkedHashMap] 保存插入顺序，超量淘汰时有确定的顺序可依（见 [remember]）。
     */
    private val seenAt = LinkedHashMap<String, Long>()

    /**
     * 判断该通知是否为窗口内的重放。
     *
     * **只读，不改动任何状态**：登记由 [remember] 在拿到终态结果后完成。若在这里顺手登记，
     * 等于给一次「还没确定要不要记」的投递上了锁 —— 那次若最终抽不到金额，更新件也会被一起挡掉。
     *
     * @param key [RawCaptureEvent.sbnKey]
     * @param atMillis 本次投递时间（通知 `postTime`）
     * @return 该 key 在窗口内已有终态记录时返回 true
     */
    fun isDuplicate(key: String, atMillis: Long): Boolean {
        if (key.isBlank()) return false
        val last = seenAt[key] ?: return false
        return atMillis - last < TTL_MILLIS
    }

    /**
     * 登记一条「已产生终态结果」的通知，开启它的重放窗口。
     *
     * 同一 key 再次登记会刷新时间戳（写入已有键不改变 [LinkedHashMap] 的插入顺序），
     * 因此窗口是**滑动**的：只要更新件持续在 [TTL_MILLIS] 内到达，就一直被挡住。
     *
     * @param key [RawCaptureEvent.sbnKey]
     * @param atMillis 本条投递时间
     */
    fun remember(key: String, atMillis: Long) {
        if (key.isBlank()) return
        seenAt[key] = atMillis
        prune(atMillis)
        // 正常情况下窗口内的 key 屈指可数；脏数据或高频来源下仍要有个硬上限，
        // 否则这个 Map 会随进程寿命无限增长
        while (seenAt.size > MAX_ENTRIES) {
            val oldest = seenAt.entries.firstOrNull() ?: break
            seenAt.remove(oldest.key)
        }
    }

    /** 丢弃已出窗口的条目，既避免旧 key 干扰判断，也控制内存占用 */
    private fun prune(nowMillis: Long) {
        seenAt.entries.removeAll { nowMillis - it.value >= TTL_MILLIS }
    }

    companion object {

        /**
         * 重放窗口宽度（毫秒）—— 唯一需要按真机表现调的旋钮。
         *
         * 更新型通知的两版投递通常落在同一个「爆发」里（秒级），10 秒足以覆盖；
         * 再放宽只会同时放大「同一 key 推送不同交易被误挡」的窗口。
         */
        const val TTL_MILLIS = 10_000L

        /** 内存中同时保留的最大 key 数，超出时按插入顺序淘汰最旧的一条 */
        private const val MAX_ENTRIES = 64
    }
}
