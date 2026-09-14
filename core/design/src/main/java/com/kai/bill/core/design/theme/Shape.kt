package com.kai.bill.core.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 全局形状：简约纯色风格以**大圆角**为主。
 *
 * 取值说明：
 * - 小组件（Chip、进度条）8~12dp
 * - 常规卡片 24dp —— 本项目的默认卡片圆角
 * - 弹窗 / 大面板 28dp
 *
 * 圆角越大越显轻盈，纯色块靠圆角与背景色差分层。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
