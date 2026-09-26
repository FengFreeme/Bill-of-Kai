package com.kai.bill.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
 * 全 App 配置读写的**唯一入口**；`DataStore<Preferences>` 实例由 `app/di/AppModule.kt` 单例提供。
 *
 * 用 DataStore 而非 `SharedPreferences`：后者同步 I/O、`apply()` 有丢数据窗口，
 * 而 DataStore 基于事务 + Flow，「设置里切主题 → 首页立刻变色」是天然支持的。
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
                cardDelivery = prefs[Keys.CARD_DELIVERY].orEmpty(),
                hintDelivery = prefs[Keys.HINT_DELIVERY].orEmpty()
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
     * 换图与清除都顺手清掉取景参数（缩放 / 位置）：旧参数照着上一张图调，套到新图大概率不对。
     *
     * @param path App 私有目录内的图片路径；传 null 恢复默认纯色背景
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
     * 更新背景图的取景（缩放 + 位置）；三个值一次写入 —— 分开写会出现
     * 「位置是新值、缩放还是旧值」的中间态。
     *
     * @param scale 缩放倍数，调用方保证 ≥1
     * @param offsetX 水平位置，-1~1；offsetY 垂直位置，-1~1
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
     * 更新无障碍服务的真实连通态（由 `KaiAccessibilityCore` 在 `onServiceConnected` / `onUnbind` 写入）。
     *
     * 与「系统设置里勾选了没有」是两个信号，UI 要同时看到才能判断「是没授权，还是没跑起来」。
     */
    suspend fun setAccessibilityEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ACCESSIBILITY_ENABLED] = enabled }
    }

    /**
     * 记一次「采集链路体检不通」，返回连续失败次数（含本次）。
     *
     * 由 `CaptureKeepAlive` 写入；用「连续几次」而非「一次」触发提醒，
     * 避免刚开机 / 刚重启进程时服务还没连上就误报。
     */
    suspend fun bumpCaptureDownStreak(): Int {
        var streak = 0
        dataStore.edit { prefs ->
            streak = (prefs[Keys.CAPTURE_DOWN_STREAK] ?: 0) + 1
            prefs[Keys.CAPTURE_DOWN_STREAK] = streak
        }
        return streak
    }

    /**
     * 体检恢复正常时清零，让下一次故障能重新提醒。
     *
     * 「一段故障只提醒一次」是刻意的：每 15 分钟响一次用户就会关掉通道，提醒彻底失效。
     */
    suspend fun resetCaptureDownStreak() {
        dataStore.edit { prefs -> prefs[Keys.CAPTURE_DOWN_STREAK] = 0 }
    }

    /**
     * 记录一次采集诊断：结果编码、原始文本、采集时间三者一次性写入，供 UI 区分「采集成功」
     * 与「真正记账成功」，并展示原文便于排查解析失败。
     *
     * **调用方必须先确认该结果通过了金额闸门**（`CaptureResult.passedAmountGate`），没抽到金额的
     * 通知请走 [recordCaptureSeen] —— 历史列表只收带金额的记录，否则「账单已出」这类高频无金额
     * 通知会把列表刷满、把真正要查的那条顶掉。
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
     * 记录一次**类别信号**的处理结果（回填 / 建账 / 放弃）。与通知诊断分开存：两者是并行的
     * 两条链路，写进同一个键会互相覆盖，用户就分不清「是通知没到」还是「页面读到了但没匹配上」。
     *
     * @param text 信号原文；**可能含页面隐私，只截断后本地展示，绝不写日志**
     * @param keepHistory 是否进历史列表：「最近一条」照旧写，只有历史可跳过 ——
     *           高频噪声（如「没抽到金额」）不该把真正要查的那条顶掉
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
     * 记录一次**确认卡片的投递结果**（理由见 [CaptureState.cardDelivery]）：这是整套链路里唯一会
     * 「静默失败」的环节，失败时用户侧毫无反馈；把结果（尤其异常信息）留下来，用户才能在引导页
     * 直接看到「为什么卡片没弹」，而不是靠反复付真钱去猜。
     *
     * @param value 结果编码或失败原因，见 [CaptureState.cardDelivery]
     */
    suspend fun recordCardDelivery(value: String) {
        dataStore.edit { prefs -> prefs[Keys.CARD_DELIVERY] = value.take(CAPTURE_RAW_MAX_CHARS) }
    }

    /**
     * 记录最近一次**提示条投递**的结果，与 [recordCardDelivery] 同一套理由
     * （见 [CaptureState.hintDelivery]）：都是 `WindowManager.addView` 那条会静默失败的路径。
     *
     * @param value 结果编码或失败原因，见 [CaptureState.hintDelivery]
     */
    suspend fun recordHintDelivery(value: String) {
        dataStore.edit { prefs -> prefs[Keys.HINT_DELIVERY] = value.take(CAPTURE_RAW_MAX_CHARS) }
    }

    /** 临时诊断：无障碍采集时把每个窗口的抽取片段写进来，便于真机定位金额所在窗口 */
    suspend fun setA11yDebug(value: String) {
        dataStore.edit { prefs -> prefs[Keys.ACCESSIBILITY_DEBUG] = value.take(4000) }
    }

    /**
     * 读「上次看过并关掉的更新公告版本」。
     *
     * 与安装包的 `versionName` 比对：**不一致才弹公告**。空串表示从来没看过，
     * 因此全新安装（或清过数据）第一次启动必定会弹一次当前版本的公告。
     *
     * @return 版本号；没记录过返回空串
     */
    suspend fun lastSeenReleaseVersion(): String = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .first()[Keys.LAST_SEEN_RELEASE_VERSION]
        .orEmpty()

    /**
     * 记下已展示过公告的版本，同一版本不再重复打扰。
     *
     * 在用户**关掉公告卡片**的那一刻才写，而不是一启动就写：
     * 进程若在公告弹出之前就被杀，下次启动应当照常再弹一次，
     * 而不是把这条公告永久吞掉。
     *
     * @param version 当前安装包的 `versionName`
     */
    suspend fun markReleaseVersionSeen(version: String) {
        dataStore.edit { prefs -> prefs[Keys.LAST_SEEN_RELEASE_VERSION] = version }
    }

    /**
     * 是否已经走过「首次启动引导」。
     *
     * 只决定第一次打开 App 时要不要直接把用户送进引导页：新装用户首先要知道的是
     * 「该开哪些权限、开完怎么用」，而不是一份他还没经历过的版本更新说明。
     *
     * @return true 表示已经引导过
     */
    suspend fun firstLaunchDone(): Boolean = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .first()[Keys.FIRST_LAUNCH_DONE] ?: false

    /**
     * 记下「首次启动引导已展示」，此后不再自动跳引导页。
     *
     * 与 [markReleaseVersionSeen] 不同，这里**在跳转前就写**：引导页是用户随时能从
     * 「权限与采集」再进的常驻页面，跳转即使失败（进程被杀）也不会丢东西，
     * 不存在「写早了把引导吞掉」的风险。
     */
    suspend fun markFirstLaunchDone() {
        dataStore.edit { prefs -> prefs[Keys.FIRST_LAUNCH_DONE] = true }
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
        val CAPTURE_DOWN_STREAK = intPreferencesKey("capture_down_streak")
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
        val HINT_DELIVERY = stringPreferencesKey("hint_delivery")
        val ACCESSIBILITY_DEBUG = stringPreferencesKey("a11y_debug")
        val LAST_SEEN_RELEASE_VERSION = stringPreferencesKey("last_seen_release_version")
        val FIRST_LAUNCH_DONE = booleanPreferencesKey("first_launch_done")
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
