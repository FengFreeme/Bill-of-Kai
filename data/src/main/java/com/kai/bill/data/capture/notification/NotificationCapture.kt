package com.kai.bill.data.capture.notification

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.kai.bill.core.prefs.KaiPrefs
import com.kai.bill.data.capture.BillSource
import com.kai.bill.data.ingest.BillIngestor
import com.kai.bill.domain.model.CaptureResult
import com.kai.bill.domain.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知采集适配：从 [NotificationCaptureBridge] 订阅原始事件，转发给 [BillIngestor] 统一落库。
 *
 * 解耦：本类不直接接触 [KaiNotificationListener]（系统服务），只消费桥接流，便于单元测试与复用。
 * 在 `init` 中即开始消费，保证监听服务一旦被系统绑定、投递事件时，不会因无人订阅而被环形缓冲丢弃。
 *
 * `isAvailable()` 检测系统「通知使用权」是否已授予本组件（通用做法：读 [Settings.Secure.ENABLED_NOTIFICATION_LISTENERS]）。
 */
@Singleton
class NotificationCapture @Inject constructor(
    private val bridge: NotificationCaptureBridge,
    private val ingestor: BillIngestor,
    private val kaiPrefs: KaiPrefs,
    @ApplicationContext private val context: Context
) : BillSource {

    override val sourceType: SourceType = SourceType.NOTIFICATION

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectorJob: Job? = null

    init {
        start()
    }

    override fun start() {
        if (collectorJob?.isActive == true) return
        collectorJob = bridge.events
            .onEach { event ->
                runCatching {
                    val result = ingestor.ingest(event.rawText, SourceType.NOTIFICATION)
                    kaiPrefs.recordCaptureDiagnostic(
                        // 不写 else：CaptureResult 已穷尽，将来新增枚举项时
                        // 编译期就会在这里报错，避免悄悄落到 "UNKNOWN"
                        result = when (result) {
                            CaptureResult.PARSED_AND_SAVED -> "SAVED"
                            CaptureResult.DUPLICATE_SKIPPED -> "DUPLICATE"
                            CaptureResult.NEEDS_REVIEW -> "NO_AMOUNT"
                            CaptureResult.PARSE_FAILED -> "NO_RULE"
                            CaptureResult.FILTERED_OUT -> "FILTERED"
                        },
                        raw = event.rawText,
                        lastSuccessAt = event.postedAtMillis
                    )
                }
            }
            .launchIn(scope)
    }

    override fun stop() {
        collectorJob?.cancel()
        collectorJob = null
    }

    /**
     * 检测系统「通知使用权」是否已授予本组件。
     *
     * 通用且可靠：不依赖任何厂商 API，直接比对 [Settings.Secure.ENABLED_NOTIFICATION_LISTENERS]
     * 是否包含本服务的扁平化组件名。
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val cn = ComponentName(context, KaiNotificationListener::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return@withContext false
        enabled.split(":").any {
            ComponentName.unflattenFromString(it)?.className == cn.className
        }
    }
}
