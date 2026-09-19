package com.kai.bill.feature.whatsnew

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode

/** 卡片滑入时长；与「确认卡片」保持一致，整套界面的动效语言是同一套 */
private const val SLIDE_IN_MS = 220
private const val SLIDE_OUT_MS = 180

/** 公告正文最高高度：条目多时卡片不该顶满全屏，但「知道了」按钮必须始终可见 */
private val BODY_MAX_HEIGHT = 380.dp

/**
 * 更新公告卡片。
 *
 * 在**升级后的第一次冷启动**弹出一次，告诉用户这版多了什么、修好了什么。
 *
 * 三个刻意的选择：
 * - **从底部滑入**，与「确认卡片」同一套动效语言 —— 它是同一类东西：一条要你看一眼的提示；
 * - **正文可滚动、按钮固定**：公告条目会越写越多，让「知道了」被顶出屏幕是低级但常见的坑；
 * - **点卡片外也能关**：用户扫一眼不想细读时，不逼他去够按钮；关闭即视为「看过」，
 *   同一版本不会再弹（去重见 `KaiPrefs.markReleaseVersionSeen`）。
 *
 * @param note 要展示的公告内容
 * @param onDismiss 关闭；调用方据此记下「该版本已看过」
 * @param modifier 外部修饰符
 */
@Composable
fun ReleaseNotesDialog(
    note: ReleaseNote,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 挂载后再置 true：直接传 visible = true 不会有进入动画
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { mounted = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = mounted,
            enter = slideInVertically(animationSpec = tween(SLIDE_IN_MS)) { it } +
                fadeIn(animationSpec = tween(SLIDE_IN_MS)),
            exit = slideOutVertically(animationSpec = tween(SLIDE_OUT_MS)) { it } +
                fadeOut(animationSpec = tween(SLIDE_OUT_MS))
        ) {
            ListCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    // 吞掉卡片内部的点击，否则点正文也会被外层的「点外部关闭」接走
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "更新公告",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppTheme.color.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        VersionBadge(version = note.version)
                    }

                    Column(
                        modifier = Modifier
                            .heightIn(max = BODY_MAX_HEIGHT)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        note.sections.forEach { section ->
                            Text(
                                text = section.heading,
                                style = AppTheme.typography.bodyMedium,
                                color = AppTheme.color.primary
                            )
                            section.items.forEach { item ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "·",
                                        style = AppTheme.typography.bodySmall,
                                        color = AppTheme.color.onSurfaceVariant
                                    )
                                    Text(
                                        text = item,
                                        style = AppTheme.typography.bodySmall,
                                        color = AppTheme.color.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(text = "知道了")
                    }
                }
            }
        }
    }
}

/** 版本徽标：`v1.2.0` —— 用户需要知道这条公告属于哪一版 */
@Composable
private fun VersionBadge(version: String) {
    Text(
        text = "v$version",
        style = AppTheme.typography.labelMedium,
        color = AppTheme.color.primary,
        modifier = Modifier
            .background(
                color = AppTheme.color.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Preview(name = "更新公告-浅色", showBackground = true, heightDp = 640)
@Composable
private fun ReleaseNotesDialogLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        ReleaseNotesDialog(
            note = ReleaseNotes.entries.first(),
            onDismiss = {}
        )
    }
}

@Preview(name = "更新公告-深色", showBackground = true, heightDp = 640)
@Composable
private fun ReleaseNotesDialogDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.LILAC, darkMode = DarkMode.DARK) {
        ReleaseNotesDialog(
            note = ReleaseNotes.entries.first(),
            onDismiss = {}
        )
    }
}
