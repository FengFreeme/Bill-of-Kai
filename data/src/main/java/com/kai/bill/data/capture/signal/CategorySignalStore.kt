package com.kai.bill.data.capture.signal

import com.kai.bill.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 类别信号的**内存短时窗口**。
 *
 * 存在意义：L2 / L3 产出的信号与 L1 通知的到达时序是不确定的 ——
 * 信号可能早于通知（页面先出现、通知稍后到），也可能晚于通知
 * （通知先落库、用户再点进详情页）。用一个滑动窗口把两侧对齐，
 * 分类路由才能在「通知文本没命中」时找到更具体的页面文本可用。
 *
 * 三条硬约定：
 * - **只存内存、不落盘**：页面文本可能含余额、卡号尾号等隐私，落盘违背框架文档的隐私约定；
 * - **线程安全**：无障碍回调、截图观察者、流水线协程运行在不同线程，临界区极小，
 *   用 `synchronized` 即可，不必引入 `Mutex` 的挂起开销；
 * - **有界**：容量与留存时长双重上限，异常应用刷事件也不会把内存撑大。
 *
 * @property clock 时间源，仅用于清理过期信号
 */
@Singleton
class CategorySignalStore @Inject constructor(
    private val clock: Clock
) {

    private val lock = Any()

    /** 按写入顺序保存；容量与留存时长都靠 [record] 顺手修剪 */
    private val signals = ArrayDeque<CategorySignal>()

    /**
     * 记录一条信号，并顺手清理「超过 [MAX_AGE_MILLIS] 未活动」的旧信号。
     *
     * 清理只在这里做而不是在 [recent] 里：读取路径应当是无副作用的，
     * 否则「查一次窗口」会悄悄改变后续查询的结果，排查时极难理解。
     */
    fun record(signal: CategorySignal) = synchronized(lock) {
        val cutoff = clock.nowMillis() - MAX_AGE_MILLIS
        signals.addLast(signal)
        signals.removeAll { it.capturedAtMillis < cutoff }
        while (signals.size > MAX_SIGNALS) signals.removeFirst()
    }

    /**
     * 取参照时刻附近的信号，**高可信来源在前、同来源新的在前**。
     *
     * 命中判定为「采集时刻**或**有效交易时刻落在 `referenceMillis ± [SIGNAL_TTL_MILLIS]` 内」：
     * - 通知场景：流水线传 `postTime`，与采集时刻基本重合 → 靠采集时刻命中；
     * - 信号建账场景：流水线传的是 `effectiveTimeMillis`（可能是文本里抽到的交易时间，
     *   与截图时刻相差数分钟）→ 靠有效交易时刻命中。
     *
     * 若只判采集时刻，「信号建账时自己的信号认不出自己」就会丢分类。
     *
     * @param referenceMillis 参照时刻（毫秒），通常是待归类账单的交易时间
     * @return 窗口内的信号；无命中返回空列表
     */
    fun recent(referenceMillis: Long): List<CategorySignal> = synchronized(lock) {
        val from = referenceMillis - SIGNAL_TTL_MILLIS
        val to = referenceMillis + SIGNAL_TTL_MILLIS
        signals
            .filter { it.capturedAtMillis in from..to || it.effectiveTimeMillis in from..to }
            .sortedWith(
                // 升序：枚举声明顺序即可信度顺序（ACCESSIBILITY 在 SCREENSHOT 之前）
                compareBy<CategorySignal> { it.origin.ordinal }
                    .thenByDescending { it.capturedAtMillis }
            )
    }

    companion object {

        /**
         * 信号有效期半径（毫秒）。
         *
         * 15 秒：覆盖「页面停留 → 通知到达」的常见间隔，又不至于把上一笔消费的信号带进来。
         */
        const val SIGNAL_TTL_MILLIS = 15_000L

        /** 窗口容量上限，防止异常应用高频刷事件导致内存增长 */
        private const val MAX_SIGNALS = 32

        /** 最长留存时长（毫秒）。覆盖「建账宽限期 + 复核」后即可丢弃 */
        private const val MAX_AGE_MILLIS = 5 * 60_000L
    }
}
