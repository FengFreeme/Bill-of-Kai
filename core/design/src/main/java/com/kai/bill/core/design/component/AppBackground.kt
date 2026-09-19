package com.kai.bill.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.kai.bill.core.design.theme.AppTheme
import java.io.File

/** 遮罩上限：再深就几乎看不到照片了 */
private const val MAX_DIM = 0.85f

/**
 * 自定义背景图：铺满（Crop）+ 用户取景（缩放 / 位置）+ 一层可读性遮罩，**不含内容层**。
 *
 * 遮罩颜色随主题深浅自动切换（深色压黑、浅色提白），深浅由 [dim] 控制：
 * 照片过亮时可以压深一点，过暗时调浅，保证前景文字可读。
 *
 * 取景的坐标约定：Crop 已把图片归一化成「正好铺满」的尺寸，所以放大 [scale] 倍后，
 * 可平移的极限就是 `尺寸 × (scale - 1) / 2`；[offsetX] / [offsetY] 存的是**占这个极限的比例**
 * （-1~1）而不是像素 —— 换分辨率、换机型后取景不会跑偏。
 *
 * @param imagePath 背景图文件路径（App 私有目录）
 * @param dim 遮罩不透明度，0 表示不遮罩
 * @param scale 缩放倍数，≥1；1 表示只铺满不放大
 * @param offsetX 水平取景位置，-1~1
 * @param offsetY 垂直取景位置，-1~1
 * @param modifier 外部修饰符
 */
@Composable
fun BackgroundImage(
    imagePath: String,
    dim: Float,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
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
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val safeScale = scale.coerceAtLeast(1f)
                    scaleX = safeScale
                    scaleY = safeScale
                    // size 是这一层自己的尺寸（即铺满后的画面），据此换算像素平移量
                    translationX = offsetX.coerceIn(-1f, 1f) *
                        (size.width * (safeScale - 1f) / 2f)
                    translationY = offsetY.coerceIn(-1f, 1f) *
                        (size.height * (safeScale - 1f) / 2f)
                }
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
 * @param scale 背景图缩放倍数，仅在 [imagePath] 非空时生效
 * @param offsetX 背景图水平取景位置，-1~1
 * @param offsetY 背景图垂直取景位置，-1~1
 * @param modifier 外部修饰符
 * @param content 背景之上的内容
 */
@Composable
fun AppBackground(
    imagePath: String?,
    dim: Float = 0.4f,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (imagePath != null) {
            BackgroundImage(
                imagePath = imagePath,
                dim = dim,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
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
