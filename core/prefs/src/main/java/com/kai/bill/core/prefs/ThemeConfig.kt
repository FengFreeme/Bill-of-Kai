package com.kai.bill.core.prefs

import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.DarkMode

/**
 * 外观配置：主题色 + 深色模式 + 自定义背景 + 卡片透明度。
 *
 * 本类是 `core:prefs` 依赖 `core:design` 的唯一原因 —— 与其让上层拿着
 * `"MINT"` 这样的字符串自己转换，不如在这里就以强类型收口。
 *
 * @property palette 主题配色，默认青草绿
 * @property darkMode 深色模式策略，默认跟随系统
 * @property backgroundUri 自定义背景图路径（App 私有目录内的文件路径）；null 表示默认纯色背景
 * @property cardAlpha 卡片透明度，1.0 完全不透明，范围建议 0.3~1.0
 * @property backgroundDim 背景图遮罩深浅，0 表示不遮罩；深色主题压黑、浅色主题提白。
 *   照片过亮/过暗时用它手动压一压，保证前景文字可读。
 */
data class ThemeConfig(
    val palette: AppPalette = AppPalette.MINT,
    val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    val backgroundUri: String? = null,
    val cardAlpha: Float = 1f,
    val backgroundDim: Float = 0.4f
)
