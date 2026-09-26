package com.kai.bill.feature.onboarding

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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.component.CtaButton
import com.kai.bill.core.design.component.GlassCard
import com.kai.bill.core.design.theme.AppTheme

/**
 * 首次启动的上手引导卡片。
 *
 * 面向**刚装好、还没配权限**的用户，只回答一个问题：「我要做什么，之后它会怎么替我记账」。
 * 因此内容固定三步、不带任何版本信息；点「去开启」把他送到引导页去开权限。
 *
 * 形式上与更新公告卡片同一套（底部卡片 + 遮罩 + 滑入，见 `ReleaseNotesDialog`）：
 * - 遮罩与卡片共用同一个入场进度，不会出现「卡片还没上来、遮罩已经全黑」；
 * - 点卡片外区域 / 返回键 = 稍后再说 —— 引导页随时能从「设置 → 权限与采集」再进，
 *   所以这里**不设「稍后」按钮**（多一个按钮只会让用户犹豫点哪个）；
 * - 卡片内部吞掉点击，否则点正文会被外层接走而直接关掉。
 *
 * @param onStart 去开启：调用方把他送到引导页
 * @param onDismiss 稍后再说：关闭卡片，之后不再自动弹
 */
@Composable
fun FirstRunGuideDialog(
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 入场进度：0 = 卡片完全藏在屏幕下缘之外，1 = 就位
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = CardEnterMillis, easing = FastOutSlowInEasing)
        )
    }

    // 系统返回键 = 稍后再说。不拦的话返回会直接结束 Activity（退出 App），
    // 用户会以为「这卡片关不掉」，而它下次启动还要再弹一遍
    BackHandler(onBack = onDismiss)

    var cardHeightPx by remember { mutableIntStateOf(0) }
    val bottomMarginPx = with(LocalDensity.current) { CardBottomMargin.roundToPx() }

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
                    text = "欢迎使用小凯记账",
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.color.onSurface
                )
                Text(
                    text = "你只需要做一件事：开启通知使用权。之后支付宝 / 微信的付款通知会自动记成一笔账，「分类识别」还会把它归到餐饮 / 交通等分类里。",
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.color.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuideBullet("① 开启「通知使用权」—— 记账的主链路，必做", emphasized = true)
                    GuideBullet("② 建议再开启「分类识别」—— 分类更准，没收到通知时也能补记")
                    GuideBullet("③ 之后平时不用打开 App；记完想改分类，点弹出的卡片就行")
                }

                Text(
                    text = "点「去开启」逐项设置；也可以点空白处或按返回键，稍后在「设置 → 权限与采集」里再弄。",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )

                CtaButton(text = "去开启", onClick = onStart)
            }
        }
    }
}

/** 一条引导条目；`emphasized` 用来点出「不做就记不了账」的那一步。 */
@Composable
private fun GuideBullet(text: String, emphasized: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "·",
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.color.onSurfaceVariant
        )
        Text(
            text = text,
            style = AppTheme.typography.bodyMedium,
            color = if (emphasized) AppTheme.color.primary else AppTheme.color.onSurface
        )
    }
}

/** 卡片距屏幕左右与下缘的留白；入场位移要用到它 */
private val CardBottomMargin = 16.dp

/** 入场时长（毫秒）；与确认卡片、更新公告一致，让它们看起来是同一类东西 */
private const val CardEnterMillis = 280

/** 遮罩最终不透明度；与更新公告同深，都是「读完再走」 */
private const val ScrimAlpha = 0.5f
