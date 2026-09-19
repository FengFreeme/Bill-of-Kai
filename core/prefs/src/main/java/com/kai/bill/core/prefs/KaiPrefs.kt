package com.kai.bill.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.DarkMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全 App 配置读写的**唯一入口**。
 *
 * 为什么不用 `SharedPreferences`：它是同步 I/O 且有 `apply()` 异步提交的丢数据窗口；
 * DataStore 基于事务 + Flow，天然支持「配置变更后 UI 自动刷新」，正好匹配
 * 「设置里切主题 → 首页立刻变色」的需求。
 *
 * `DataStore<Preferences>` 实例由 `app/di/AppModule.kt` 以单例提供。
 *
 * @property dataStore 偏好设置数据存储
 */
@Singleton
class KaiPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    /**
     * 观察外观配置。
     *
     * 首次启动（无任何配置）时发射默认配置，因此 UI 不需要处理「空状态」。
     */
    val themeConfig: Flow<ThemeConfig> = dataStore.data
        .catch { e ->
            // DataStore 读文件失败时发射空配置走默认值；抛出去会让首页直接白屏，
            // 而主题配置是可降级的非关键数据
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            ThemeConfig(
                palette = prefs[Keys.THEME_PALETTE]
                    ?.let { safeEnum(it, AppPalette.MINT) }
                    ?: AppPalette.MINT,
                darkMode = prefs[Keys.DARK_MODE]
                    ?.let { safeEnum(it, DarkMode.FOLLOW_SYSTEM) }
                    ?: DarkMode.FOLLOW_SYSTEM,
                backgroundUri = prefs[Keys.BACKGROUND_URI],
                cardAlpha = prefs[Keys.CARD_ALPHA] ?: 1f,
                backgroundDim = prefs[Keys.BACKGROUND_DIM] ?: 0.4f,
                backgroundScale = prefs[Keys.BACKGROUND_SCALE] ?: 1f,
                backgroundOffsetX = prefs[Keys.BACKGROUND_OFFSET_X] ?: 0f,
                backgroundOffsetY = prefs[Keys.BACKGROUND_OFFSET_Y] ?: 0f
            )
        }

    /**
     * 观察采集链路状态。
     */
    val captureState: Flow<CaptureState> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            CaptureState(
                smsLastScanMillis = prefs[Keys.SMS_LAST_SCAN_MILLIS] ?: 0L,
                captureServiceAlive = prefs[Keys.CAPTURE_SERVICE_ALIVE] ?: false,
                notificationListenerEnabled = prefs[Keys.NOTIFICATION_LISTENER_ENABLED] ?: false,
                accessibilityEnabled = prefs[Keys.ACCESSIBILITY_ENABLED] ?: false,
                captureLastSuccessAt = prefs[Keys.CAPTURE_LAST_SUCCESS_AT] ?: 0L,
                captureEnabled = prefs[Keys.CAPTURE_ENABLED] ?: false,
                captureLastResult = prefs[Keys.CAPTURE_LAST_RESULT].orEmpty(),
                captureLastRaw = prefs[Keys.CAPTURE_LAST_RAW].orEmpty(),
                captureRecent = CaptureDiagCodec.decode(prefs[Keys.CAPTURE_DIAG_HISTORY]),
                signalLastResult = prefs[Keys.SIGNAL_LAST_RESULT].orEmpty(),
                signalLastText = prefs[Keys.SIGNAL_LAST_TEXT].orEmpty(),
                signalLastAtMillis = prefs[Keys.SIGNAL_LAST_AT] ?: 0L,
                signalRecent = CaptureDiagCodec.decode(prefs[Keys.SIGNAL_DIAG_HISTORY]),
                cardDelivery = prefs[Keys.CARD_DELIVERY].orEmpty()
            )
        }

    /** 整体保存外观配置 */
    suspend fun saveThemeConfig(config: ThemeConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_PALETTE] = config.palette.name
            prefs[Keys.DARK_MODE] = config.darkMode.name
        }
    }

    /**
     * 只更新主题配色。
     *
     * 单独提供细粒度 setter 而非只留 [saveThemeConfig]：外观页的开关互不相关，
     * 若每次都整包写回，并发切换时后写入的旧值会覆盖掉前一次的新值。
     */
    suspend fun updatePalette(palette: AppPalette) {
        dataStore.edit { prefs -> prefs[Keys.THEME_PALETTE] = palette.name }
    }

    /** 只更新深色模式策略 */
    suspend fun updateDarkMode(darkMode: DarkMode) {
        dataStore.edit { prefs -> prefs[Keys.DARK_MODE] = darkMode.name }
    }

    /**
     * 更新自定义背景图。
     *
     * 无论换图还是清除，都顺手把取景参数（缩放 / 位置）清掉：旧参数是照着上一张图调的，
     * 套到新图上大概率不对，不如回到「铺满居中」让用户重新调。
     *
     * @param path App 私有目录内的图片文件路径；传 null 恢复默认纯色背景
     */
    suspend fun updateBackgroundUri(path: String?) {
        dataStore.edit { prefs ->
            if (path == null) {
                prefs.remove(Keys.BACKGROUND_URI)
            } else {
                prefs[Keys.BACKGROUND_URI] = path
            }
            prefs.remove(Keys.BACKGROUND_SCALE)
            prefs.remove(Keys.BACKGROUND_OFFSET_X)
            prefs.remove(Keys.BACKGROUND_OFFSET_Y)
        }
    }

    /**
     * 更新背景图的取景（缩放 + 位置）。
     *
     * 三个值一次写入：它们描述同一张图的取景，分开写会出现「位置是新值、缩放还是旧值」的中间态。
     *
     * @param scale 缩放倍数，调用方保证 ≥1
     * @param offsetX 水平位置，-1~1
     * @param offsetY 垂直位置，-1~1
     */
    suspend fun updateBackgroundTransform(scale: Float, offsetX: Float, offsetY: Float) {
        dataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_SCALE] = scale
            prefs[Keys.BACKGROUND_OFFSET_X] = offsetX
            prefs[Keys.BACKGROUND_OFFSET_Y] = offsetY
        }
    }

    /**
     * 更新卡片透明度。
     *
     * @param alpha 1.0 为完全不透明；调用方负责限制在合理区间
     */
    suspend fun updateCardAlpha(alpha: Float) {
        dataStore.edit { prefs -> prefs[Keys.CARD_ALPHA] = alpha }
    }

    /**
     * 更新背景图遮罩深浅。
     *
     * @param dim 0 表示不遮罩；调用方负责限制在合理区间
     */
    suspend fun updateBackgroundDim(dim: Float) {
        dataStore.edit { prefs -> prefs[Keys.BACKGROUND_DIM] = dim }
    }

    /**
     * 推进短信扫描水位线。
     *
     * @param millis 本次扫描到的最新短信时间戳（毫秒）；只增不减由调用方保证
     */
    suspend fun updateSmsLastScanMillis(millis: Long) {
        dataStore.edit { prefs -> prefs[Keys.SMS_LAST_SCAN_MILLIS] = millis }
    }

    /** 更新前台保活服务存活标记 */
    suspend fun setCaptureServiceAlive(alive: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.CAPTURE_SERVICE_ALIVE] = alive }
    }

    /** 更新通知使用权授予标记 */
    suspend fun setNotificationListenerEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.NOTIFICATION_LISTENER_ENABLED] = enabled }
    }

    /**
     * 更新无障碍服务的**真实连通态**。
     *
     * 由 `KaiAccessibilityService` 在 `onServiceConnected` / `onUnbind` 写入。
     * 它与「系统设置里勾选了没有」是两个不同的信号，UI 需要同时看到才能判断
     * 「是没授权，还是授权了但服务没跑起来」。
     */
    suspend fun setAccessibilityEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ACCESSIBILITY_ENABLED] = enabled }
    }

    /**
     * 记录一次采集诊断：最近结果编码、原始文本、采集时间三者一次性写入，
     * 供 UI 区分「采集成功」与「真正记账成功」，并展示原文便于排查解析失败。
     *
     * **调用方必须先确认该结果通过了金额闸门**（`CaptureResult.passedAmountGate`），
     * 没抽到金额的通知请走 [recordCaptureSeen]。历史列表只收带金额的记录，
     * 否则「账单已出」这类高频无金额通知会把列表刷满，真正要查的那条反而被顶掉。
     *
     * @param atMillis 通知发布时间；历史列表按写入顺序倒序展示，不需要再排序
     */
    suspend fun recordCaptureDiagnostic(result: String, raw: String, atMillis: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.CAPTURE_LAST_RESULT] = result
            prefs[Keys.CAPTURE_LAST_RAW] = raw.take(CAPTURE_RAW_MAX_CHARS)
            prefs[Keys.CAPTURE_LAST_SUCCESS_AT] = atMillis
            prefs[Keys.CAPTURE_DIAG_HISTORY] = CaptureDiagCodec.encode(
                newEntry = CaptureDiagEntry(result, raw, atMillis),
                existing = prefs[Keys.CAPTURE_DIAG_HISTORY]
            )
        }
    }

    /**
     * 只记录「收到了一条白名单通知」这件事，不写诊断结果。
     *
     * 存在的理由：[CaptureState.captureLastSuccessAt] 回答的是「监听服务还在正常工作吗」，
     * 只要收到通知就该推进 —— 否则用户连续收到几条没金额的提醒后，
     * 「最近一次采集」会停在一个很早的时间点上，看起来像服务已经挂了。
     * 而「这条通知被怎么处理了」由 [recordCaptureDiagnostic] 单独承担。
     */
    suspend fun recordCaptureSeen(atMillis: Long) {
        dataStore.edit { prefs -> prefs[Keys.CAPTURE_LAST_SUCCESS_AT] = atMillis }
    }

    /** 更新用户主开关：是否启用自动记账采集 */
    suspend fun setCaptureEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.CAPTURE_ENABLED] = enabled }
    }

    /**
     * 记录一次**类别信号**的处理结果（回填 / 建账 / 放弃）。
     *
     * 与通知诊断分开存：两者是并行的两条链路，写进同一个键会互相覆盖，
     * 用户就分不清「是通知没到」还是「页面读到了但没匹配上」。
     *
     * @param result 结果编码，取值见 `ReconcileOutcome`
     * @param text 信号原文；**可能含页面隐私，只截断后本地展示，绝不写日志**
     * @param atMillis 处理时刻（毫秒）
     * @param keepHistory 是否进历史列表。**一切照旧写「最近一条」，只有历史是可跳过的** ——
     *           高频噪声（如「没抽到金额」）不该把真正要查的那条顶掉，
     *           但「最近一条」反映的正是此刻的状态，跳过了反而看不出卡在哪
     */
    suspend fun recordSignalResult(
        result: String,
        text: String,
        atMillis: Long,
        keepHistory: Boolean = true
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.SIGNAL_LAST_RESULT] = result
            prefs[Keys.SIGNAL_LAST_TEXT] = text.take(CAPTURE_RAW_MAX_CHARS)
            prefs[Keys.SIGNAL_LAST_AT] = atMillis
            if (keepHistory) {
                prefs[Keys.SIGNAL_DIAG_HISTORY] = CaptureDiagCodec.encode(
                    newEntry = CaptureDiagEntry(result, text, atMillis),
                    existing = prefs[Keys.SIGNAL_DIAG_HISTORY]
                )
            }
        }
    }

    /**
     * 记录一次**确认卡片的投递结果**。
     *
     * 存在的理由见 [CaptureState.cardDelivery]：这是整套链路里唯一会「静默失败」的环节，
     * 失败时用户侧毫无反馈。把结果（尤其是异常信息）留下来，
     * 用户才能在引导页直接看到「为什么卡片没弹」，而不是靠反复付真钱去猜。
     *
     * @param value 结果编码或失败原因，见 [CaptureState.cardDelivery]
     */
    suspend fun recordCardDelivery(value: String) {
        dataStore.edit { prefs -> prefs[Keys.CARD_DELIVERY] = value.take(CAPTURE_RAW_MAX_CHARS) }
    }

    /** 临时诊断：无障碍采集时把每个窗口的抽取片段写进来，便于真机定位金额所在窗口 */
    suspend fun setA11yDebug(value: String) {
        dataStore.edit { prefs -> prefs[Keys.ACCESSIBILITY_DEBUG] = value.take(4000) }
    }

    /**
     * 上次对用户展示过更新公告的版本号；**从未展示过时返回空串**。
     *
     * 只存「版本号字符串」而不是布尔标记：用户可能跨版本升级（1.2.0 → 1.5.0 直接跳，
     * 中间某个版本没装过），存布尔量就只能表达「看过/没看过」，跨版本时会把该看的新公告吞掉。
     *
     * NOTE: 一次性读取即可，不做成 Flow —— 调用点是冷启动那一次判断，之后不再关心它变化。
     */
    suspend fun lastSeenReleaseVersion(): String = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .first()[Keys.LAST_SEEN_RELEASE_VERSION]
        .orEmpty()

    /**
     * 记下已展示过更新公告的版本，同一版本不再重复打扰。
     *
     * 在用户**关掉公告卡片**时才写入，而不是一启动就写：进程若在展示前被杀，
     * 下次启动应当照常再弹一次，而不是把这条公告永久吞掉。
     */
    suspend fun markReleaseVersionSeen(version: String) {
        dataStore.edit { prefs -> prefs[Keys.LAST_SEEN_RELEASE_VERSION] = version }
    }

    private object Keys {
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val BACKGROUND_URI = stringPreferencesKey("background_uri")
        val CARD_ALPHA = floatPreferencesKey("card_alpha")
        val BACKGROUND_DIM = floatPreferencesKey("background_dim")
        val BACKGROUND_SCALE = floatPreferencesKey("background_scale")
        val BACKGROUND_OFFSET_X = floatPreferencesKey("background_offset_x")
        val BACKGROUND_OFFSET_Y = floatPreferencesKey("background_offset_y")
        val SMS_LAST_SCAN_MILLIS = longPreferencesKey("sms_last_scan_millis")
        val CAPTURE_SERVICE_ALIVE = booleanPreferencesKey("capture_service_alive")
        val NOTIFICATION_LISTENER_ENABLED = booleanPreferencesKey("notification_listener_enabled")
        val ACCESSIBILITY_ENABLED = booleanPreferencesKey("accessibility_enabled")
        val CAPTURE_LAST_SUCCESS_AT = longPreferencesKey("capture_last_success_at")
        val CAPTURE_ENABLED = booleanPreferencesKey("capture_enabled")
        val CAPTURE_LAST_RESULT = stringPreferencesKey("capture_last_result")
        val CAPTURE_LAST_RAW = stringPreferencesKey("capture_last_raw")
        val CAPTURE_DIAG_HISTORY = stringPreferencesKey("capture_diag_history")
        val SIGNAL_LAST_RESULT = stringPreferencesKey("signal_last_result")
        val SIGNAL_LAST_TEXT = stringPreferencesKey("signal_last_text")
        val SIGNAL_LAST_AT = longPreferencesKey("signal_last_at")
        val SIGNAL_DIAG_HISTORY = stringPreferencesKey("signal_diag_history")
        val CARD_DELIVERY = stringPreferencesKey("card_delivery")
        val ACCESSIBILITY_DEBUG = stringPreferencesKey("a11y_debug")
        val LAST_SEEN_RELEASE_VERSION = stringPreferencesKey("last_seen_release_version")
    }
}

/**
 * 枚举反序列化，遇到未知值回退到 [fallback]。
 *
 * NOTE: 不抛异常 —— 主题枚举将来若改名，旧配置里的老值不应让 App 直接崩溃，
 *       静默回退到默认值对用户几乎无感。
 */
private inline fun <reified T : Enum<T>> safeEnum(name: String, fallback: T): T =
    runCatching { enumValueOf<T>(name) }.getOrNull() ?: fallback
