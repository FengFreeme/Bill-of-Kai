package com.kai.bill.core.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * ============================================================================
 * 配色定义：3 套主题 × 深浅双模 = 6 组组合
 *
 * 为什么不用 colors.xml：主题必须在运行时动态切换，XML 资源做不到；
 * 且 Material3 的 ColorScheme 缺少业务色（支出/收入/中性灰），
 * 因此这里同时产出 ColorScheme（交给 MaterialTheme）与 ExtendedColors（业务扩展色）。
 *
 * 深色模式为「独立调色」而非对浅色取反：
 *   - 功能色提亮以维持对比度
 *   - 页面背景、卡片与底栏均为纯色，无半透明玻璃
 * ============================================================================
 */

// ---------- 功能色：三套主题共用，仅随深浅模式调整亮度 ----------
// 设计意图：换主题后「红色仍然是支出」不能变，否则会破坏用户对颜色的直觉。
private val ExpenseLight = Color(0xFFF2775F)   // 支出 · 珊瑚红（比正红柔和，降低焦虑感）
private val ExpenseDark = Color(0xFFFF8A75)
private val IncomeLight = Color(0xFF3FC79A)    // 收入 · 薄荷绿
private val IncomeDark = Color(0xFF5FDDB0)
private val NeutralLight = Color(0xFF9AA5B1)   // 转账 / 不计入统计
private val NeutralDark = Color(0xFF7C8894)
private val AlertLight = Color(0xFFFF2800)   // 超支 / 警示 · 鲜红（#FF2800）
private val AlertDark = Color(0xFFFF2800)
private val ProgressSafeLight = Color(0xFF52CC9B)   // 预算进度 · 余量充足（浅绿）
private val ProgressSafeDark = Color(0xFF6FE0B6)
private val ProgressWarnLight = Color(0xFFF0A63C)   // 预算进度 · 开始紧张（琥珀）
private val ProgressWarnDark = Color(0xFFFFB74D)

// ============================================================================
// MINT 青草绿（默认）—— 偏草绿、清新，避免过深沉闷与偏青薄荷感
// ============================================================================
private val MintLightScheme = lightColorScheme(
    primary = Color(0xFF5CB34D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5F6DF),
    onPrimaryContainer = Color(0xFF1A3D14),
    secondary = Color(0xFF6BA85F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEFCF),
    onSecondaryContainer = Color(0xFF1B3616),
    tertiary = Color(0xFF6FA8D8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD8E9F8),
    onTertiaryContainer = Color(0xFF0E2A3C),
    background = Color(0xFFF6FBF4),
    onBackground = Color(0xFF152418),
    surface = Color(0xFFFBFDFA),
    onSurface = Color(0xFF152418),
    surfaceVariant = Color(0xFFE8F3E4),
    onSurfaceVariant = Color(0xFF455844),
    outline = Color(0xFF7A9474),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val MintDarkScheme = darkColorScheme(
    primary = Color(0xFF9AD88A),
    onPrimary = Color(0xFF1A3D14),
    primaryContainer = Color(0xFF2F5C28),
    onPrimaryContainer = Color(0xFFD4F0C8),
    secondary = Color(0xFFA5D89A),
    onSecondary = Color(0xFF1B3616),
    secondaryContainer = Color(0xFF2A4A24),
    onSecondaryContainer = Color(0xFFD8EFCB),
    tertiary = Color(0xFFA6C9EE),
    onTertiary = Color(0xFF0E2A3A),
    tertiaryContainer = Color(0xFF1C3A4E),
    onTertiaryContainer = Color(0xFFC6DDF5),
    background = Color(0xFF101810),
    onBackground = Color(0xFFE8F3E8),
    surface = Color(0xFF152015),
    onSurface = Color(0xFFE8F3E8),
    surfaceVariant = Color(0xFF213021),
    onSurfaceVariant = Color(0xFFB8CBB6),
    outline = Color(0xFF849984),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

// NOTE: 必须是 internal 而非 private —— ExtendedColors.kt 中 LocalExtendedColors
//       的默认值要用它，而 private 顶层声明只在同文件内可见
internal val MintLightExtended = ExtendedColors(
    expense = ExpenseLight,
    income = IncomeLight,
    neutral = NeutralLight,
    alert = AlertLight,
    progressSafe = ProgressSafeLight,
    progressWarn = ProgressWarnLight
)

private val MintDarkExtended = ExtendedColors(
    expense = ExpenseDark,
    income = IncomeDark,
    neutral = NeutralDark,
    alert = AlertDark,
    progressSafe = ProgressSafeDark,
    progressWarn = ProgressWarnDark
)

// ============================================================================
// SKY 天空蓝
// ============================================================================
private val SkyLightScheme = lightColorScheme(
    primary = Color(0xFF3E8FD8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBFB),
    onPrimaryContainer = Color(0xFF0A2C42),
    secondary = Color(0xFF3D7FB8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E7F7),
    onSecondaryContainer = Color(0xFF0A2839),
    tertiary = Color(0xFFA97BD8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE0FB),
    onTertiaryContainer = Color(0xFF2E1150),
    background = Color(0xFFF4F9FD),
    onBackground = Color(0xFF101A24),
    surface = Color(0xFFFAFCFE),
    onSurface = Color(0xFF101A24),
    surfaceVariant = Color(0xFFE4EEF8),
    onSurfaceVariant = Color(0xFF3F4E5A),
    outline = Color(0xFF6F818F),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val SkyDarkScheme = darkColorScheme(
    primary = Color(0xFF8CC8F0),
    onPrimary = Color(0xFF06334A),
    primaryContainer = Color(0xFF16334A),
    onPrimaryContainer = Color(0xFFB9DFF5),
    secondary = Color(0xFF9FC9E8),
    onSecondary = Color(0xFF0A2E42),
    secondaryContainer = Color(0xFF1E3D52),
    onSecondaryContainer = Color(0xFFC2DDF2),
    tertiary = Color(0xFFCFAAF0),
    onTertiary = Color(0xFF2E1450),
    tertiaryContainer = Color(0xFF3A2450),
    onTertiaryContainer = Color(0xFFE5D4FA),
    background = Color(0xFF0D141A),
    onBackground = Color(0xFFE6EEF5),
    surface = Color(0xFF121A21),
    onSurface = Color(0xFFE6EEF5),
    surfaceVariant = Color(0xFF1E2A33),
    onSurfaceVariant = Color(0xFFB6C6D2),
    outline = Color(0xFF7F909B),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

private val SkyLightExtended = ExtendedColors(
    expense = ExpenseLight,
    income = IncomeLight,
    neutral = NeutralLight,
    alert = AlertLight,
    progressSafe = ProgressSafeLight,
    progressWarn = ProgressWarnLight
)

private val SkyDarkExtended = ExtendedColors(
    expense = ExpenseDark,
    income = IncomeDark,
    neutral = NeutralDark,
    alert = AlertDark,
    progressSafe = ProgressSafeDark,
    progressWarn = ProgressWarnDark
)

// ============================================================================
// LILAC 淡紫樱花
// ============================================================================
private val LilacLightScheme = lightColorScheme(
    primary = Color(0xFFA97BD8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE0FB),
    onPrimaryContainer = Color(0xFF2E1150),
    secondary = Color(0xFF8F6BBF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6D7F7),
    onSecondaryContainer = Color(0xFF251040),
    tertiary = Color(0xFFF0899F),   // 樱花粉作为点缀色
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBD3E3),
    onTertiaryContainer = Color(0xFF4A1526),
    background = Color(0xFFF9F5FD),
    onBackground = Color(0xFF1A1322),
    surface = Color(0xFFFDFBFE),
    onSurface = Color(0xFF1A1322),
    surfaceVariant = Color(0xFFEDE3F5),
    onSurfaceVariant = Color(0xFF4C4257),
    outline = Color(0xFF7C6F86),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val LilacDarkScheme = darkColorScheme(
    primary = Color(0xFFCFAAF0),
    onPrimary = Color(0xFF33124F),
    primaryContainer = Color(0xFF33224A),
    onPrimaryContainer = Color(0xFFE9D9FB),
    secondary = Color(0xFFC4A6E0),
    onSecondary = Color(0xFF2F1A44),
    secondaryContainer = Color(0xFF3E2A55),
    onSecondaryContainer = Color(0xFFE2CEF5),
    tertiary = Color(0xFFF5A6B8),
    onTertiary = Color(0xFF4A1526),
    tertiaryContainer = Color(0xFF4A2640),
    onTertiaryContainer = Color(0xFFFBD4DE),
    background = Color(0xFF140F1A),
    onBackground = Color(0xFFEFE7F7),
    surface = Color(0xFF1A1421),
    onSurface = Color(0xFFEFE7F7),
    surfaceVariant = Color(0xFF281F33),
    onSurfaceVariant = Color(0xFFC7BAD4),
    outline = Color(0xFF8F8299),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

private val LilacLightExtended = ExtendedColors(
    expense = ExpenseLight,
    income = IncomeLight,
    neutral = NeutralLight,
    alert = AlertLight,
    progressSafe = ProgressSafeLight,
    progressWarn = ProgressWarnLight
)

private val LilacDarkExtended = ExtendedColors(
    expense = ExpenseDark,
    income = IncomeDark,
    neutral = NeutralDark,
    alert = AlertDark,
    progressSafe = ProgressSafeDark,
    progressWarn = ProgressWarnDark
)

// ============================================================================
// 对外出口
// ============================================================================

/** 一套配色的完整定义：浅/深各一份 ColorScheme 与扩展色 */
data class PaletteSet(
    val lightScheme: ColorScheme,
    val darkScheme: ColorScheme,
    val lightExt: ExtendedColors,
    val darkExt: ExtendedColors
)

private val MintSet = PaletteSet(
    lightScheme = MintLightScheme,
    darkScheme = MintDarkScheme,
    lightExt = MintLightExtended,
    darkExt = MintDarkExtended
)

private val SkySet = PaletteSet(
    lightScheme = SkyLightScheme,
    darkScheme = SkyDarkScheme,
    lightExt = SkyLightExtended,
    darkExt = SkyDarkExtended
)

private val LilacSet = PaletteSet(
    lightScheme = LilacLightScheme,
    darkScheme = LilacDarkScheme,
    lightExt = LilacLightExtended,
    darkExt = LilacDarkExtended
)

/**
 * 根据主题枚举取出对应的配色集合。
 *
 * @param palette 主题配色
 * @return 该主题的浅色与深色完整定义
 */
fun paletteSetFor(palette: AppPalette): PaletteSet = when (palette) {
    AppPalette.MINT -> MintSet
    AppPalette.SKY -> SkySet
    AppPalette.LILAC -> LilacSet
}

/**
 * 主题选择器的预览用色：取该主题的主色，用于 6 宫格色块展示。
 *
 * @param palette 主题配色
 * @return 该主题在浅色模式下的主色
 */
fun previewColorOf(palette: AppPalette): Color = when (palette) {
    AppPalette.MINT -> Color(0xFF5CB34D)
    AppPalette.SKY -> Color(0xFF3E8FD8)
    AppPalette.LILAC -> Color(0xFFA97BD8)
}
