package com.kai.bill.feature.settings.appearance

import com.kai.bill.core.prefs.ThemeConfig

/**
 * 外观设置页的 UI 状态。
 *
 * @property config 当前外观配置（主题色 / 深色模式）
 */
data class AppearanceUiState(
    val config: ThemeConfig = ThemeConfig()
)
