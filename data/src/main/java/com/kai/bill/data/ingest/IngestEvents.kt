package com.kai.bill.data.ingest

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 采集链路内部的内存事件总线（目前只有「账单已落库」一种事件）。
 *
 * 存在理由：`SignalReconciler` 在「窗口内无候选」时要等一个宽限期再复核，
 * 目的是给 L1 通知留出落库时间（扫码场景信号常比通知早约 0.5 秒到达）。
 * 但**盲等**意味着通知 0.5 秒就到了、我们也要睡满整个宽限期才看得见，
 * 白白把「补记一笔」推迟到宽限期结束。
 *
 * 用一个广播事件把两侧解耦：
 * - 落库侧（[IngestPipeline]）成功落库后 `notifyBillSaved()`，不需要知道谁在等；
 * - 等待侧（[SignalReconciler]）`withTimeoutOrNull(剩余宽限) { billSaved.first() }`，
 *   一收到就立刻复核并转成回填，没收到就等到超时再建账。
 *
 * 为什么用 [SharedFlow] 而不是 `Channel`：同一时刻可能有多个决策协程在等，
 * 需要「一条消息叫醒所有等待者」；Channel 只会把消息投给其中一个。
 * `replay = 0`：等待者只关心**订阅之后**发生的落库；补上一次也已由「等之前先查一遍」覆盖。
 */
@Singleton
class IngestEvents @Inject constructor() {

    private val _billSaved = MutableSharedFlow<Unit>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** 每有一笔账单成功落库就发射一次；只用于「叫醒等待者」，不携带任何业务数据。 */
    val billSaved: SharedFlow<Unit> = _billSaved

    fun notifyBillSaved() {
        _billSaved.tryEmit(Unit)
    }
}
