package com.kai.bill.core.design.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 卡片透明度（0.1f ~ 1f），1f 为完全不透明。
 *
 * 由 [BillOfKaiTheme] 注入，[com.kai.bill.core.design.component.GlassCard] 等容器
 * 读取后作用于填充色 alpha，从而实现「自定义背景图 + 半透明卡片」。
 *
 * 用 `staticCompositionLocalOf`：该值只在设置页拖动滑块时变化（低频），
 * 静态持有可避免整棵树订阅变化带来的开销。
 */
val LocalCardAlpha = staticCompositionLocalOf { 1f }

/**
 * 当前是否启用了自定义背景图。
 *
 * 导航层据此决定各页铺「不透明背景色」还是「透明以透出底层背景图」。
 */
val LocalHasBackgroundImage = staticCompositionLocalOf { false }
