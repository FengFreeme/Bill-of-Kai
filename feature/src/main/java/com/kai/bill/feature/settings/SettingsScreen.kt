package com.kai.bill.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.feature.common.SectionTitle

/**
 * 设置页：入口聚合。无状态、无 ViewModel。
 *
 * @param releaseNotesVersion 当前安装版本的公告版本号（即 `versionName`）；
 *   null 表示这个版本还没有公告文案，此时**不显示该入口** ——
 *   否则就是一个点下去没有任何反应的按钮
 * @param onReleaseNotesClick 查看更新公告。公告卡片由 `MainActivity` 在应用最上层弹出：
 *   它是**应用级**内容（冷启动也会自动弹），不属于任何一个页面，页面不该自己再画一份
 */
@Composable
fun SettingsScreen(
    onAppearanceClick: () -> Unit,
    onCategoryClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onRulesClick: () -> Unit = {},
    onBudgetClick: () -> Unit = {},
    onReviewClick: () -> Unit = {},
    onOnboardingClick: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    releaseNotesVersion: String? = null,
    onReleaseNotesClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") {
            SectionTitle(text = "设置")
        }
        item(key = "appearance") {
            SettingsRow(
                title = "外观",
                subtitle = "主题色、深色模式",
                leading = { PaletteGlyph(it) },
                onClick = onAppearanceClick
            )
        }
        item(key = "budget") {
            SettingsRow(
                title = "预算",
                subtitle = "月预算与日可用",
                leading = { WalletGlyph(it) },
                onClick = onBudgetClick
            )
        }
        item(key = "category") {
            SettingsRow(
                title = "分类管理",
                subtitle = "支出 / 收入分类",
                leading = { FolderGlyph(it) },
                onClick = onCategoryClick
            )
        }
        item(key = "account") {
            SettingsRow(
                title = "账户管理",
                subtitle = "现金 / 支付宝 / 微信…",
                leading = { CardGlyph(it) },
                onClick = onAccountClick
            )
        }
        item(key = "backup") {
            SettingsRow(
                title = "数据备份",
                subtitle = "导出 / 导入账单，换机迁移",
                leading = { BackupGlyph(it) },
                onClick = onBackupClick
            )
        }
        item(key = "review") {
            SettingsRow(
                title = "待审核记录",
                subtitle = "判不准的通知，确认后才成为账单",
                leading = { ClipboardGlyph(it) },
                onClick = onReviewClick
            )
        }
        // 「解析规则」仍待 P1 落地（词表入库后才能编辑），先隐藏入口
        item(key = "onboarding") {
            SettingsRow(
                title = "权限与采集引导",
                subtitle = "通知监听 / 短信",
                leading = { ShieldGlyph(it) },
                onClick = onOnboardingClick
            )
        }
        // 更新公告：只在「当前版本有公告文案」时出现。
        // 位置放最后：它是「想起来才看」的内容，不该挤在常用设置前面
        releaseNotesVersion?.let { version ->
            item(key = "release_notes") {
                SettingsRow(
                    title = "更新公告",
                    subtitle = "当前版本 $version 改了什么",
                    leading = { DocGlyph(it) },
                    onClick = onReleaseNotesClick
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    leading: @Composable (Color) -> Unit,
    onClick: (() -> Unit)? = null
) {
    val iconTint = AppTheme.color.primary
    ListCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                leading(iconTint)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.color.onSurface
                )
                Text(
                    text = subtitle,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
            }
            ChevronGlyph(color = AppTheme.color.onSurfaceVariant.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun PaletteGlyph(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Dot(size = 7.dp, color = color)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Dot(size = 7.dp, color = color.copy(alpha = 0.75f))
            Dot(size = 7.dp, color = color.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun WalletGlyph(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 18.dp, height = 14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 3.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f))
        )
    }
}

@Composable
private fun FolderGlyph(color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier = Modifier
                .size(width = 10.dp, height = 4.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color.copy(alpha = 0.7f))
        )
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

@Composable
private fun CardGlyph(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 18.dp, height = 12.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 3.dp)
                .size(width = 18.dp, height = 3.dp)
                .background(Color.White.copy(alpha = 0.35f))
        )
    }
}

@Composable
private fun BackupGlyph(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        // 左：向下箭头（导出）
        val leftX = w * 0.32f
        drawLine(color, Offset(leftX, h * 0.16f), Offset(leftX, h * 0.78f), stroke, StrokeCap.Round)
        drawLine(color, Offset(leftX - w * 0.15f, h * 0.6f), Offset(leftX, h * 0.8f), stroke, StrokeCap.Round)
        drawLine(color, Offset(leftX + w * 0.15f, h * 0.6f), Offset(leftX, h * 0.8f), stroke, StrokeCap.Round)
        // 右：向上箭头（导入）
        val rightX = w * 0.68f
        drawLine(color, Offset(rightX, h * 0.84f), Offset(rightX, h * 0.22f), stroke, StrokeCap.Round)
        drawLine(color, Offset(rightX - w * 0.15f, h * 0.4f), Offset(rightX, h * 0.2f), stroke, StrokeCap.Round)
        drawLine(color, Offset(rightX + w * 0.15f, h * 0.4f), Offset(rightX, h * 0.2f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun DocGlyph(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 14.dp, height = 18.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(height = 2.dp, width = 8.dp)
                    .background(Color.White.copy(alpha = 0.75f))
            )
            Box(
                modifier = Modifier
                    .size(height = 2.dp, width = 6.dp)
                    .background(Color.White.copy(alpha = 0.55f))
            )
        }
    }
}

@Composable
private fun ClipboardGlyph(color: Color) {
    Box(contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(width = 14.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .size(width = 8.dp, height = 4.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color.copy(alpha = 0.75f))
        )
    }
}

@Composable
private fun ShieldGlyph(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 14.dp, height = 16.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 7.dp,
                    topEnd = 7.dp,
                    bottomStart = 7.dp,
                    bottomEnd = 7.dp
                )
            )
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 2.dp, height = 6.dp)
                .background(Color.White.copy(alpha = 0.85f))
        )
    }
}

@Composable
private fun ChevronGlyph(color: Color) {
    Text(
        text = "›",
        style = AppTheme.typography.headlineSmall,
        color = color
    )
}

@Composable
private fun Dot(size: Dp, color: Color) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Preview(name = "设置页-浅色", showBackground = true)
@Composable
private fun SettingsScreenLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        SettingsScreen(onAppearanceClick = {}, releaseNotesVersion = "1.2.0")
    }
}
