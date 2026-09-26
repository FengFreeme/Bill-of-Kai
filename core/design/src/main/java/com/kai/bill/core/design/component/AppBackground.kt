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
 * 自定义背景图：铺满（Crop）+ 用户取景（缩放 / 位置）+ 可读性遮罩，**不含内容层**。
 *
 * 遮罩色随主题深浅切换（深色压黑、浅色提白），由 [dim] 控制深浅以保证前景文字可读。
 * 取景坐标是**占可平移极限的比例**（-1~1）而非像素：Crop 归一化成「正好铺满」后，放大 [scale]
 * 倍的可平移极限为 `尺寸 × (scale - 1) / 2`，用比例存换分辨率 / 机型也不会跑偏。
 *
 * @param dim 遮罩不透明度，0 表示不遮罩
 * @param scale 缩放倍数，≥1；1 表示只铺满不放大
 * @param offsetX 水平取景位置，-1~1
 * @param offsetY 垂直取景位置，-1~1
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
    // WHY: 用主题背景色亮度判断深浅，而非 isSystemInDarkTheme ——
    //      用户可在设置里强制深/浅色，此时系统值不准
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
                    // NOTE: size 是这一层自己的尺寸（即铺满后的画面），据此换算像素平移量
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
 * 全 App 背景容器：无背景图时铺主题纯色，否则铺 [BackgroundImage] 再叠加 [content]。
 *
 * @param dim 仅在 [imagePath] 非空时生效
 * @param scale 仅在 [imagePath] 非空时生效
 * @param offsetX 背景图水平取景位置，-1~1
 * @param offsetY 背景图垂直取景位置，-1~1
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
