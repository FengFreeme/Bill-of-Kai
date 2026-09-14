package com.kai.bill.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.kai.bill.core.design.theme.AppTheme
import java.io.File

/** 遮罩上限：再深就几乎看不到照片了 */
private const val MAX_DIM = 0.85f

/**
 * 自定义背景图：铺满（Crop）+ 一层可读性遮罩，**不含内容层**。
 *
 * 遮罩颜色随主题深浅自动切换（深色压黑、浅色提白），深浅由 [dim] 控制：
 * 照片过亮时可以压深一点，过暗时调浅，保证前景文字可读。
 *
 * @param imagePath 背景图文件路径（App 私有目录）
 * @param dim 遮罩不透明度，0 表示不遮罩
 * @param modifier 外部修饰符
 */
@Composable
fun BackgroundImage(
    imagePath: String,
    dim: Float,
    modifier: Modifier = Modifier
) {
    // 用主题背景色亮度判断深浅，而非 isSystemInDarkTheme ——
    // 用户可在设置里强制深/浅色，此时系统值不准
    val isDarkTheme = AppTheme.color.background.luminance() < 0.5f
    val maskColor = if (isDarkTheme) Color.Black else Color.White

    Box(modifier = modifier) {
        AsyncImage(
            model = File(imagePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        val alpha = dim.coerceIn(0f, MAX_DIM)
        if (alpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(maskColor.copy(alpha = alpha))
            )
        }
    }
}

/**
 * 全 App 背景容器。
 *
 * - 未设置背景图（[imagePath] 为 null）：铺主题纯色背景，与改造前一致；
 * - 已设置背景图：铺 [BackgroundImage] 后再叠加 [content]。
 *
 * @param imagePath 背景图文件路径（App 私有目录）；null 表示使用纯色背景
 * @param dim 背景图遮罩深浅，仅在 [imagePath] 非空时生效
 * @param modifier 外部修饰符
 * @param content 背景之上的内容
 */
@Composable
fun AppBackground(
    imagePath: String?,
    dim: Float = 0.4f,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (imagePath != null) {
            BackgroundImage(
                imagePath = imagePath,
                dim = dim,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.color.background)
            )
        }
        content()
    }
}
