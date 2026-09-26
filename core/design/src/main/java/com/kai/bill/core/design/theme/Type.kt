package com.kai.bill.core.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 全局排版。
 *
 * 关于字体：设计稿指定「思源黑体」，但该字体需打包进 APK（约 10MB+），
 * 对自用项目收益不高，因此 M0 先用系统默认字体（国内 ROM 多为思源系或相近黑体）。
 * TODO: 若对字体一致性有强需求，再引入 fontRes —— 届时只需改 [AppFontFamily] 一处。
 */
private val AppFontFamily = FontFamily.Default

/**
 * 数字等宽（tabular numbers）。
 *
 * 为什么必须开：金额列表里的数字如果按默认比例宽度渲染，
 * 不同行的小数点会对不齐，视觉上非常廉价。开启 tnum 后所有数字等宽。
 */
// NOTE: internal —— component 包下的 AmountText 也要用它，private 只在同文件可见
internal const val TABULAR_NUMBERS = "tnum"

val AppTypography = Typography(
    // 页面大标题 / 金额主数字
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold, // 600
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontFeatureSettings = TABULAR_NUMBERS
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = TABULAR_NUMBERS
    ),
    // 卡片标题 / 分区小标题
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,   // 500
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    // 正文
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,   // 400
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TABULAR_NUMBERS
    ),
    bodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = TABULAR_NUMBERS
    ),
    // 按钮 / 标签
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = TABULAR_NUMBERS
    )
)
