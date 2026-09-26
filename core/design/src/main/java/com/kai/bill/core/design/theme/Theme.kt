package com.kai.bill.core.design.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 小凯记账主题入口 —— 全部 6 种组合（3 套主题 × 深浅双模）在此收口。
 *
 * 任何页面/组件都必须包在本主题内，否则 `AppTheme.ext` 取不到值。
 *
 * @param cardAlpha 卡片透明度（0.1~1.0），1.0 完全不透明
 * @param hasBackgroundImage 供导航层决定是否透出背景图
 */
@Composable
fun BillOfKaiTheme(
    palette: AppPalette = AppPalette.MINT,
    darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    cardAlpha: Float = 1f,
    hasBackgroundImage: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (darkMode) {
        DarkMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    // WHY: 主题切换是低频操作，用 remember 缓存避免每次重组都重新取对象
    val set = remember(palette) { paletteSetFor(palette) }
    val colorScheme = remember(set, isDark) { if (isDark) set.darkScheme else set.lightScheme }
    val extendedColors = remember(set, isDark) { if (isDark) set.darkExt else set.lightExt }

    SyncSystemBars(isDark = isDark)

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalCardAlpha provides cardAlpha.coerceIn(0.1f, 1f),
        LocalHasBackgroundImage provides hasBackgroundImage
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

/**
 * 同步系统栏图标明暗。放在代码里而非 XML：深浅模式可在 App 内即时切换，
 * XML 只在 Activity 创建时生效一次，切换后不会更新。
 */
@Composable
private fun SyncSystemBars(isDark: Boolean) {
    val view = LocalView.current
    // NOTE: isInEditMode 为 true 时处于 Preview/布局编辑器，没有真实 Window，必须跳过
    if (view.isInEditMode) return

    SideEffect {
        // NOTE: view.context 在某些场景下是 ContextWrapper 而非 Activity 本身
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        // NOTE: API 35 起 setStatusBarColor/setNavigationBarColor 已废弃，透明改由 MainActivity
        //       的 enableEdgeToEdge() 负责，此处只跟随深浅模式切换图标明暗
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }
}
