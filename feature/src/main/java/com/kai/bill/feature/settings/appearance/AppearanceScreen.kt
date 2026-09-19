package com.kai.bill.feature.settings.appearance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.common.percent.PercentFormatter
import com.kai.bill.core.design.component.BackgroundImage
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.component.SegmentTabs
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.feature.common.SectionTitle

/** 卡片透明度下限：再低会看不清卡片内容 */
private const val MIN_CARD_ALPHA = 0.3f

/** 背景遮罩上限：再深就几乎看不到照片了 */
private const val MAX_BACKGROUND_DIM = 0.8f

/**
 * 外观设置页：主题配色（6 宫格）+ 深色模式 + 背景图片 + 卡片透明度。
 *
 * 无状态：只消费 [AppearanceUiState]，所有改动通过回调上抛给 [AppearanceViewModel]。
 * 「跟随系统」的深色模式放在这里的分段器里选 —— 它无法在 6 宫格中预览。
 *
 * @param uiState 外观状态
 * @param onPaletteSelected 主题配色切换
 * @param onDarkModeSelected 深色模式策略切换
 * @param onBackgroundSelected 选择背景图（null 表示清除）
 * @param onCardAlphaChanged 卡片透明度变更（松手时提交）
 * @param onBackgroundDimChanged 背景遮罩深浅变更（松手时提交）
 * @param onBackgroundTransformChanged 背景图缩放 / 位置变更（取景面板点「保存」时提交）
 * @param modifier 外部修饰符
 */
@Composable
fun AppearanceScreen(
    uiState: AppearanceUiState,
    onPaletteSelected: (AppPalette) -> Unit,
    onDarkModeSelected: (DarkMode) -> Unit,
    onBackgroundSelected: (Uri?) -> Unit = {},
    onCardAlphaChanged: (Float) -> Unit = {},
    onBackgroundDimChanged: (Float) -> Unit = {},
    onBackgroundTransformChanged: (Float, Float, Float) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    // 系统图片选择器（Photo Picker）：无需申请存储权限
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        // 用户取消时 uri 为 null，不能误当作「清除背景」
        if (uri != null) onBackgroundSelected(uri)
    }

    // 取景面板显隐；只在有背景图时能打开
    var adjusting by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "palette_title") {
            SectionTitle(text = "主题配色")
        }
        item(key = "palette_grid") {
            ThemePalettePicker(
                selectedPalette = uiState.config.palette,
                selectedDarkMode = uiState.config.darkMode,
                onSelect = { palette, mode ->
                    onPaletteSelected(palette)
                    onDarkModeSelected(mode)
                }
            )
        }
        item(key = "dark_title") {
            SectionTitle(text = "深色模式")
        }
        item(key = "dark_tabs") {
            SegmentTabs(
                items = DarkMode.entries,
                selected = uiState.config.darkMode,
                onSelect = onDarkModeSelected,
                labelOf = { it.label }
            )
        }
        item(key = "background_title") {
            SectionTitle(text = "背景图片")
        }
        item(key = "background_picker") {
            BackgroundControl(
                path = uiState.config.backgroundUri,
                scale = uiState.config.backgroundScale,
                offsetX = uiState.config.backgroundOffsetX,
                offsetY = uiState.config.backgroundOffsetY,
                onPick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onClear = { onBackgroundSelected(null) },
                onAdjust = { adjusting = true }
            )
        }
        // 遮罩深浅只在设置了背景图时才有意义
        if (uiState.config.backgroundUri != null) {
            item(key = "background_dim") {
                SettingSlider(
                    title = "背景明暗",
                    caption = "照片过亮就压深一点，过暗就调浅，保证文字可读",
                    value = uiState.config.backgroundDim,
                    valueRange = 0f..MAX_BACKGROUND_DIM,
                    onChange = onBackgroundDimChanged
                )
            }
        }
        item(key = "card_alpha") {
            SettingSlider(
                title = "卡片透明度",
                caption = "数值越低卡片越透，背景图越明显（底部导航栏一起变透）",
                value = uiState.config.cardAlpha,
                valueRange = MIN_CARD_ALPHA..1f,
                onChange = onCardAlphaChanged
            )
        }
    }

    // 取景面板：全屏 Dialog，拖动 / 缩放都只改本地草稿，点「保存」才写回配置
    val backgroundPath = uiState.config.backgroundUri
    if (adjusting && backgroundPath != null) {
        BackgroundAdjustOverlay(
            imagePath = backgroundPath,
            initialScale = uiState.config.backgroundScale,
            initialOffsetX = uiState.config.backgroundOffsetX,
            initialOffsetY = uiState.config.backgroundOffsetY,
            onSave = { scale, offsetX, offsetY ->
                onBackgroundTransformChanged(scale, offsetX, offsetY)
                adjusting = false
            },
            onDismiss = { adjusting = false }
        )
    }
}

/**
 * 背景图片控制：当前图预览（含已保存的取景）+ 选择 / 调整 / 移除。
 *
 * 缩略图用 [BackgroundImage] 而不是裸 `AsyncImage`：取景参数是**比例**语义，
 * 这里也能如实渲染出「放大了多少、偏向哪边」，用户点「调整」之前就看得到当前效果。
 *
 * @param path 背景图路径；null 表示当前是默认纯色背景
 * @param scale 已保存的缩放倍数
 * @param offsetX 已保存的水平位置，-1~1
 * @param offsetY 已保存的垂直位置，-1~1
 * @param onPick 选择 / 更换照片
 * @param onClear 移除背景
 * @param onAdjust 打开取景面板
 */
@Composable
private fun BackgroundControl(
    path: String?,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onAdjust: () -> Unit
) {
    ListCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (path != null) {
                BackgroundImage(
                    imagePath = path,
                    dim = 0f,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppTheme.color.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "当前为默认纯色背景",
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.color.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsActionButton(
                    text = if (path == null) "选择照片" else "更换照片",
                    primary = true,
                    onClick = onPick,
                    modifier = Modifier.weight(1f)
                )
                if (path != null) {
                    SettingsActionButton(
                        text = "移除背景",
                        primary = false,
                        onClick = onClear,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (path != null) {
                // 取景单独占一行：和「更换 / 移除」挤一行会被压成三条窄按钮
                SettingsActionButton(
                    text = "调整大小与位置",
                    primary = false,
                    onClick = onAdjust,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 通用「标题 + 百分比 + 滑块」设置项。
 *
 * 拖动只更新本地草稿，松手才提交，避免高频写 DataStore。
 */
@Composable
private fun SettingSlider(
    title: String,
    caption: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    var draft by remember(value) { mutableFloatStateOf(value) }

    ListCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.color.onSurface
                )
                Text(
                    text = PercentFormatter.of(draft),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.color.primary
                )
            }
            Text(
                text = caption,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant
            )
            Slider(
                value = draft,
                onValueChange = { draft = it },
                onValueChangeFinished = { onChange(draft) },
                valueRange = valueRange
            )
        }
    }
}

/**
 * 页面通用操作按钮。
 *
 * 对包内可见（而非 `private`）：取景面板 [BackgroundAdjustOverlay] 在独立文件里，
 * 也用它保持两处按钮样式一致。
 */
@Composable
internal fun SettingsActionButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (primary) AppTheme.color.primary
                else AppTheme.color.outline.copy(alpha = 0.16f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AppTheme.typography.titleMedium,
            color = if (primary) AppTheme.color.onPrimary else AppTheme.color.onSurface
        )
    }
}

/**
 * 外观页路由，负责接上 ViewModel。
 *
 * @param modifier 外部修饰符
 * @param viewModel 由 Hilt 注入
 */
@Composable
fun AppearanceRoute(
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppearanceScreen(
        uiState = uiState,
        onPaletteSelected = viewModel::onPaletteSelected,
        onDarkModeSelected = viewModel::onDarkModeSelected,
        onBackgroundSelected = viewModel::onBackgroundSelected,
        onCardAlphaChanged = viewModel::onCardAlphaChanged,
        onBackgroundDimChanged = viewModel::onBackgroundDimChanged,
        onBackgroundTransformChanged = viewModel::onBackgroundTransformChanged,
        modifier = modifier
    )
}

@Preview(name = "外观页-浅色", showBackground = true)
@Composable
private fun AppearanceScreenLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        AppearanceScreen(
            uiState = AppearanceUiState(),
            onPaletteSelected = {},
            onDarkModeSelected = {}
        )
    }
}

@Preview(name = "外观页-深色", showBackground = true)
@Composable
private fun AppearanceScreenDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.LILAC, darkMode = DarkMode.DARK) {
        AppearanceScreen(
            uiState = AppearanceUiState(),
            onPaletteSelected = {},
            onDarkModeSelected = {}
        )
    }
}
