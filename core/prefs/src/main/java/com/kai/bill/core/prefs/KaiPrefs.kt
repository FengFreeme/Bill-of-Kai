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
                backgroundDim = prefs[Keys.BACKGROUND_DIM] ?: 0.4f
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
                captureLastSuccessAt = prefs[Keys.CAPTURE_LAST_SUCCESS_AT] ?: 0L,
                captureEnabled = prefs[Keys.CAPTURE_ENABLED] ?: false,
                captureLastResult = prefs[Keys.CAPTURE_LAST_RESULT].orEmpty(),
                captureLastRaw = prefs[Keys.CAPTURE_LAST_RAW].orEmpty()
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
     * @param path App 私有目录内的图片文件路径；传 null 恢复默认纯色背景
     */
    suspend fun updateBackgroundUri(path: String?) {
        dataStore.edit { prefs ->
            if (path == null) {
                prefs.remove(Keys.BACKGROUND_URI)
            } else {
                prefs[Keys.BACKGROUND_URI] = path
            }
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
     * 记录一次采集诊断：最近结果编码、原始文本、成功时间三者一次性写入，
     * 供 UI 区分「采集成功」与「真正记账成功」，并展示原文便于排查解析失败。
     */
    suspend fun recordCaptureDiagnostic(result: String, raw: String, lastSuccessAt: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.CAPTURE_LAST_RESULT] = result
            prefs[Keys.CAPTURE_LAST_RAW] = raw.take(200)
            prefs[Keys.CAPTURE_LAST_SUCCESS_AT] = lastSuccessAt
        }
    }

    /** 更新用户主开关：是否启用自动记账采集 */
    suspend fun setCaptureEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.CAPTURE_ENABLED] = enabled }
    }

    private object Keys {
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val BACKGROUND_URI = stringPreferencesKey("background_uri")
        val CARD_ALPHA = floatPreferencesKey("card_alpha")
        val BACKGROUND_DIM = floatPreferencesKey("background_dim")
        val SMS_LAST_SCAN_MILLIS = longPreferencesKey("sms_last_scan_millis")
        val CAPTURE_SERVICE_ALIVE = booleanPreferencesKey("capture_service_alive")
        val NOTIFICATION_LISTENER_ENABLED = booleanPreferencesKey("notification_listener_enabled")
        val CAPTURE_LAST_SUCCESS_AT = longPreferencesKey("capture_last_success_at")
        val CAPTURE_ENABLED = booleanPreferencesKey("capture_enabled")
        val CAPTURE_LAST_RESULT = stringPreferencesKey("capture_last_result")
        val CAPTURE_LAST_RAW = stringPreferencesKey("capture_last_raw")
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
