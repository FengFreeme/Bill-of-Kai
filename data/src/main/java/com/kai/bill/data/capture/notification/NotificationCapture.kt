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
    private val recentKeys: RecentNotificationKeys,
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
                // 同一条系统通知被更新后重发（如「支付中」→「支付成功」）：key 不变，但投递时间
                // 往往已跨出 DedupKey 的秒桶，光靠 dedupHash 拦不住。这里在解析之前直接跳过，
                // 窗口与取舍见 RecentNotificationKeys。
                if (recentKeys.isDuplicate(event.sbnKey, event.postedAtMillis)) {
                    // 留痕：否则「更新重发被吞掉」在诊断里完全看不见，用户排查漏记时会误以为
                    // 采集根本没收到这条通知。与其它跳过一样包 runCatching —— 写诊断失败不能
                    // 把整条采集链路打断。
                    runCatching {
                        kaiPrefs.recordCaptureDiagnostic(
                            result = "REPLAY",
                            raw = event.rawText,
                            atMillis = event.postedAtMillis
                        )
                    }
                    return@onEach
                }
                runCatching {
                    // 包名用于账户路由，postTime 用作交易时间：通知可能延迟投递，
                    // 记录时刻与真实交易时刻会差出好几小时（补投的历史通知尤其明显）。
                    val result = ingestor.ingest(
                        rawText = event.rawText,
                        source = SourceType.NOTIFICATION,
                        packageName = event.packageName,
                        eventTimeMillis = event.postedAtMillis
                    )
                    // 只有产生终态结果的投递才登记重放窗口：它值得拦住后续更新件；
                    // 「抽不到金额 / 被排除」的版本若也登记，会把真正能记账的那一版一起挡掉。
                    if (result.accounted) {
                        recentKeys.remember(event.sbnKey, event.postedAtMillis)
                    }
                    // 不写 else：CaptureResult 已穷尽，将来新增枚举项时
                    // 编译期就会在这里报错，避免悄悄落到 "UNKNOWN"
                    //
                    // 编码会原样出现在「权限与采集引导」页上，是用户自查解析是否正常的唯一入口，
                    // 因此每个编码都必须与 PermissionCheckScreen 的文案表成对维护。
                    val code = when (result) {
                        CaptureResult.PARSED_AND_SAVED -> "SAVED"
                        CaptureResult.DUPLICATE_SKIPPED -> "DUPLICATE"
                        CaptureResult.NEEDS_REVIEW -> "PENDING"
                        CaptureResult.PENDING_DUPLICATE -> "PENDING_DUP"
                        CaptureResult.NO_AMOUNT -> "NO_AMOUNT"
                        CaptureResult.EXCLUDED -> "EXCLUDED"
                        CaptureResult.PARSE_FAILED -> "NO_RULE"
                        CaptureResult.FILTERED_OUT -> "FILTERED"
                    }
                    if (result.passedAmountGate) {
                        // 只有抽出金额的通知才写诊断：没金额的既不记账也没有排查价值，
                        // 记进去只会把真正要看的记录顶掉
                        kaiPrefs.recordCaptureDiagnostic(
                            result = code,
                            raw = event.rawText,
                            atMillis = event.postedAtMillis
                        )
                    } else {
                        // 未过金额闸门：仍要推进「最近一次收到通知」的水位线，
                        // 否则连收几条无金额通知会让「最近成功采集」看着像服务已死
                        kaiPrefs.recordCaptureSeen(event.postedAtMillis)
                    }
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
