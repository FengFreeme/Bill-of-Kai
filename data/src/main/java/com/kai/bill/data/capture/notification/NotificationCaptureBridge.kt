package com.kai.bill.data.capture.notification

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知监听服务与采集逻辑之间的桥。
 *
 * 解耦考虑：[KaiNotificationListener] 由系统创建且生命周期不可控，不应直接持有业务对象；
 * 所有抽出的原始事件先 `emit` 进本桥的冷缓冲，再由 [com.kai.bill.data.capture.NotificationCapture]
 * 在 `start()` 时 `collect` 转交 [com.kai.bill.data.ingest.BillIngestor]。
 *
 * 环形缓冲（[extraBufferCapacity] + [BufferOverflow.DROP_OLDEST]）可抵御 Ingestor 瞬时不可用：
 * 消费慢时丢弃最旧的事件，而不是阻塞系统回调线程；账单有短信补扫与手动记账兜底，丢少量不影响正确性。
 */
interface NotificationCaptureBridge {
    /** 被采集侧订阅的事件流 */
    val events: SharedFlow<RawCaptureEvent>

    /** 由监听器在 [android.service.notification.NotificationListenerService.onNotificationPosted] 中调用 */
    fun emit(event: RawCaptureEvent)
}

@Singleton
class DefaultNotificationCaptureBridge @Inject constructor() : NotificationCaptureBridge {

    private val _events = MutableSharedFlow<RawCaptureEvent>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val events: SharedFlow<RawCaptureEvent> = _events.asSharedFlow()

    override fun emit(event: RawCaptureEvent) {
        _events.tryEmit(event)
    }
}
