package com.kai.bill.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.common.overlay.CaptureHint
import com.kai.bill.core.common.overlay.CaptureHintKind
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.feature.record.components.categoryIconRes

/**
 * 采集结果的悬浮提示条 —— 一枚深色胶囊。
 *
 * 它浮在**别人的 App 之上**（拍照付款页、微信账单页…），因此配色不能跟应用主题走：
 * 底层界面可能是任意颜色，只有「深底白字」在明暗两种背景上都保证可读
 * （商业产品同款做法，见竞品的「此账单已记录，无需重复记录」）。
 *
 * 文案在 UI 层拼：`data` 只上报 [CaptureHintKind]，不该出现给用户看的句子。
 *
 * @param hint 提示内容
 * @param modifier 外部修饰符
 */
@Composable
fun CaptureHintPill(hint: CaptureHint, modifier: Modifier = Modifier) {
    val iconKey = when (hint.kind) {
        CaptureHintKind.ALREADY_RECORDED -> "receipt"
        CaptureHintKind.NEEDS_REVIEW -> "time"
    }
    val text = when (hint.kind) {
        // 竞品同款文案：直接给结论，用户看的就是「不用再记一遍」
        CaptureHintKind.ALREADY_RECORDED -> "此账单已记录，无需重复记录"
        // 待确认要带上金额：用户至少知道「哪一笔」进了待确认
        CaptureHintKind.NEEDS_REVIEW -> hint.amountCents
            ?.let { "${formatYuan(it)} · 看不出是收支，已加入待确认" }
            ?: "看不出是收支，已加入待确认"
    }

    Row(
        modifier = modifier
            .background(color = PillBackground, shape = RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categoryIconRes(iconKey)?.let { res ->
            Icon(
                painter = painterResource(res),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = text,
            style = AppTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}

/** 胶囊底色：接近纯黑但留一点透明，避免完全遮住底下的界面 */
private val PillBackground = Color(0xE6232328)

/** 分 → `¥12.30`；与卡片、诊断保持同一格式 */
private fun formatYuan(cents: Long): String =
    "¥${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

@Preview(name = "提示条-已记录", showBackground = true)
@Composable
private fun CaptureHintPillRecordedPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        CaptureHintPill(hint = CaptureHint(CaptureHintKind.ALREADY_RECORDED))
    }
}

@Preview(name = "提示条-待确认", showBackground = true)
@Composable
private fun CaptureHintPillPendingPreview() {
    BillOfKaiTheme(palette = AppPalette.LILAC, darkMode = DarkMode.DARK) {
        CaptureHintPill(hint = CaptureHint(CaptureHintKind.NEEDS_REVIEW, amountCents = 1230L))
    }
}
