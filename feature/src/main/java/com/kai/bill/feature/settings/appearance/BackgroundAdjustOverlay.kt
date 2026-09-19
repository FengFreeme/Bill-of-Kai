package com.kai.bill.feature.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kai.bill.core.design.component.BackgroundImage
import com.kai.bill.core.design.theme.AppTheme

/** 背景图缩放上限：再大就只剩几个像素被拉成马赛克了 */
internal const val MAX_BACKGROUND_SCALE = 3f

/**
 * 背景图取景面板：**单指拖动改位置、双指捏合改大小**（滑块可精调），点「保存」才写回配置。
 *
 * 默认是 1 倍「铺满居中」，也就是没调过的样子；调坏了随时点「重置」回到它。
 * 改动先落在本地草稿：否则拖一下就写一次 DataStore、还边拖边刷新整个 App 的背景，
 * 既卡又没法后悔。
 *
 * 用全屏 [Dialog] 承载：它在独立窗口里，天然挡住下层页面（不会误触到外观页的控件），
 * 系统返回键也能取消。
 *
 * @param imagePath 背景图文件路径
 * @param initialScale 当前缩放倍数
 * @param initialOffsetX 当前水平位置，-1~1
 * @param initialOffsetY 当前垂直位置，-1~1
 * @param onSave 保存取景参数
 * @param onDismiss 取消 / 按返回键
 */
@Composable
fun BackgroundAdjustOverlay(
    imagePath: String,
    initialScale: Float,
    initialOffsetX: Float,
    initialOffsetY: Float,
    onSave: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(initialScale) }
    var offsetX by remember { mutableFloatStateOf(initialOffsetX) }
    var offsetY by remember { mutableFloatStateOf(initialOffsetY) }

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BackgroundImage(
                imagePath = imagePath,
                dim = 0f,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // panZoomLock = true：单指只平移、双指只缩放，避免搓动时画面被带偏。
                        // 手势事件由 detectTransformGestures 内部消费，不必自己 consume。
                        detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, MAX_BACKGROUND_SCALE)
                            if (nextScale <= 1f) {
                                // 缩回 1 倍就没有可平移的余量，位置一并归零
                                reset()
                            } else {
                                scale = nextScale
                                // 平移极限 = 尺寸 × (scale-1) / 2，与 BackgroundImage 的换算一致；
                                // 要用手势后的新倍率算，否则缩放中途的拖动会被旧极限夹住
                                val maxX = size.width * (nextScale - 1f) / 2f
                                val maxY = size.height * (nextScale - 1f) / 2f
                                if (maxX > 0f) {
                                    offsetX = (offsetX + pan.x / maxX).coerceIn(-1f, 1f)
                                }
                                if (maxY > 0f) {
                                    offsetY = (offsetY + pan.y / maxY).coerceIn(-1f, 1f)
                                }
                            }
                        }
                    }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "单指拖动位置，双指捏合大小",
                        style = AppTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Text(
                        text = "重置",
                        style = AppTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .clickable(onClick = ::reset)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Slider(
                    value = scale,
                    onValueChange = {
                        scale = it
                        if (it <= 1f) {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    },
                    valueRange = 1f..MAX_BACKGROUND_SCALE
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsActionButton(
                        text = "取消",
                        primary = false,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    SettingsActionButton(
                        text = "保存",
                        primary = true,
                        onClick = { onSave(scale, offsetX, offsetY) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
