package com.kai.bill.feature.whatsnew

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.component.CtaButton
import com.kai.bill.core.design.component.GlassCard
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode

/**
 * 更新公告卡片。
 *
 * 形式上刻意做成「**底部的卡片**」而不是系统 `AlertDialog`：
 * - 应用里所有需要用户看清的东西都是卡片（确认卡片、预算卡、引导卡片），
 *   弹一个 Material 对话框会让它显得像「系统在说话」，而不是这个 App 在跟你说话；
 * - 公告条目多（本次十几条），卡片能给出可滚动的完整篇幅，系统对话框撑不开。
 *
 * 交互与确认卡片同一套约定：
 * - 遮罩与卡片**共用同一个入场进度**，避免「卡片还在屏幕外、遮罩已经全黑」；
 * - 点卡片外区域等同「知道了」——公告只是告知，关闭不改变任何数据；
 * - 卡片内部吞掉点击，否则点正文也会被外层接走而直接关掉。
 *
 * **不提供「下次再说」**：公告只在「装上的版本 ≠ 上次看过的版本」时弹一次，
 * 给一个「稍后」按钮反而会让用户在下次启动时被再打扰一次。
 *
 * @param note 要展示的公告
 * @param onDismiss 用户已读并关闭；调用方负责记下「这个版本看过了」
 * @param modifier 外部修饰符
 */
@Composable
fun ReleaseNotesDialog(
    note: ReleaseNote,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 入场进度：0 = 卡片完全藏在屏幕下缘之外，1 = 就位
    val enter = remember { Animatable(0f) }
    LaunchedEffect(note.version) {
        enter.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = CardEnterMillis, easing = FastOutSlowInEasing)
        )
    }

    // 系统返回键 = 知道了。不拦的话，返回会**直接结束 Activity**（退出 App），
    // 而公告并不会被标记为已读 —— 用户下次启动又被弹一次，还以为这卡片关不掉
    BackHandler(onBack = onDismiss)

    // 卡片自身的像素高度：入场位移要刚好把它藏到屏幕下缘之外
    var cardHeightPx by remember { mutableIntStateOf(0) }
    val bottomMarginPx = with(LocalDensity.current) { CardBottomMargin.roundToPx() }
    // 条目区限高：本次十几条，不限高会把卡片顶到屏幕外
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * ListMaxScreenRatio).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = ScrimAlpha * enter.value))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassCard(
            strong = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(CardBottomMargin)
                .onSizeChanged { cardHeightPx = it.height }
                // 从屏幕下缘滑入：位移恰好等于「卡片高度 + 底部留白」。
                // 用 graphicsLayer 而不是改布局：只影响绘制，不触发重新测量
                .graphicsLayer {
                    translationY = (1f - enter.value) * (cardHeightPx + bottomMarginPx)
                }
                // 吞掉卡片内部的点击
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "更新到 ${note.version}",
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.color.onSurface
                )
                Text(
                    text = note.lead,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.color.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .heightIn(max = maxListHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    note.sections.forEach { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = section.heading,
                                style = AppTheme.typography.titleSmall,
                                color = AppTheme.color.primary
                            )
                            section.items.forEach { item ->
                                // 一行一条，用「·」而不是符号图标：条目是整句，
                                // 图标会把每行都顶出一个多余的缩进块
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "·",
                                        style = AppTheme.typography.bodyMedium,
                                        color = AppTheme.color.onSurfaceVariant
                                    )
                                    Text(
                                        text = item,
                                        style = AppTheme.typography.bodyMedium,
                                        color = AppTheme.color.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                CtaButton(text = "知道了", onClick = onDismiss)
            }
        }
    }
}

/** 卡片距屏幕左右与下缘的留白；入场位移要用到它，故提为常量 */
private val CardBottomMargin = 16.dp

/** 入场时长（毫秒）；与确认卡片一致，让两类卡片看起来是同一个东西在最上层出现 */
private const val CardEnterMillis = 280

/** 遮罩最终不透明度；比确认卡片略深一点，因为它是「读完再走」而不是「顺手看一眼」 */
private const val ScrimAlpha = 0.5f

/** 条目区高度上限（占屏幕比例）：再多也只在卡片内部滚动 */
private const val ListMaxScreenRatio = 0.5f

@Preview(name = "更新公告-浅色", showBackground = true)
@Composable
private fun ReleaseNotesDialogLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        ReleaseNotes.forVersion("1.2.0")?.let { note ->
            ReleaseNotesDialog(note = note, onDismiss = {})
        }
    }
}

@Preview(name = "更新公告-深色", showBackground = true)
@Composable
private fun ReleaseNotesDialogDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.LILAC, darkMode = DarkMode.DARK) {
        ReleaseNotes.forVersion("1.2.0")?.let { note ->
            ReleaseNotesDialog(note = note, onDismiss = {})
        }
    }
}
