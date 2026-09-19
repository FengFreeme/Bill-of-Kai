package com.kai.bill.core.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material3 的 [ColorScheme] 中**不存在**的业务扩展色。
 *
 * 为什么需要它：记账 App 有大量「语义色」是 M3 色板没有的 ——
 * 支出红、收入绿、转账灰。
 * 这些颜色必须随主题与深浅模式动态切换，因此不能硬编码在组件里。
 *
 * 访问方式统一走 [AppTheme.ext]，不要直接 new 一个实例。
 *
 * @property expense 支出金额色，珊瑚红
 * @property income 收入金额色，薄荷绿
 * @property neutral 转账 / 不计入统计的中性灰
 * @property alert 超支 / 警示色，鲜红（非珊瑚粉），用于预算超支提示与进度条告警
 */
data class ExtendedColors(
    val expense: Color,
    val income: Color,
    val neutral: Color,
    val alert: Color
)

/**
 * 扩展色的 CompositionLocal。
 *
 * 用 `staticCompositionLocalOf` 而非 `compositionLocalOf`：
 * 扩展色只在主题切换时变化（极低频），静态持有可以避免 Compose
 * 为整棵树订阅变化带来的额外开销。
 *
 * NOTE: 这里**不能**显式声明类型为 `CompositionLocal<ExtendedColors>` ——
 *       `provides` 是 `ProvidableCompositionLocal` 的成员，
 *       一旦向上转型成 CompositionLocal，`CompositionLocalProvider(... provides ...)` 就编译不过。
 */
val LocalExtendedColors = staticCompositionLocalOf { MintLightExtended }

/**
 * 主题访问入口，替代直接使用 `MaterialTheme` 的散落写法。
 *
 * 约定：
 * - 标准 Material3 色 → `AppTheme.color.xxx`
 * - 业务扩展色       → `AppTheme.ext.xxx`
 *
 * 两者都是 `@ReadOnlyComposable`，读取不会引入重组订阅，性能与 `MaterialTheme` 等价。
 */
object AppTheme {

    /** 当前生效的 Material3 配色 */
    val color: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    /** 当前生效的排版 */
    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    /** 当前生效的形状（大圆角） */
    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes

    /** 当前生效的业务扩展色 */
    val ext: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}
