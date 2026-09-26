package com.kai.bill.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.prefs.CAPTURE_DIAG_LIMIT
import com.kai.bill.core.prefs.CaptureDiagEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「最近采集结果」历史弹窗。
 *
 * 权限自检页原先只能看到**最近一条**结果，而排查漏记时真正要找的往往是几小时前那条，
 * 早就被新通知顶掉了。这里把诊断历史展开成可点击的列表（最多 [CAPTURE_DIAG_LIMIT] 条）。
 *
 * 用整屏 Dialog 而不是底部弹窗：原文可能长达 200 字，底部弹窗的高度不够完整展开一行。
 *
 * **通知采集与页面识别共用本组件**：两条链路的诊断结构完全一致（结果码 + 原文 + 时间），
 * 只是结果码的取值不同。把「编码 → 文案 / 释义 / 颜色」的三张映射表作为参数传进来，
 * 就能让两条链路在引导页上**长得一模一样**，用户不必学两套看法，也不必维护两个弹窗。
 *
 * @param entries 诊断历史，新的在前（通知侧为 `CaptureState.captureRecent`，
 *                识别侧为 `CaptureState.signalRecent`）
 * @param onDismiss 关闭弹窗
 * @param title 弹窗标题
 * @param subtitle 标题下方的一句说明，用来交代「为什么只有这些」
 * @param emptyText 无记录时的文案
 * @param labelOf 结果码 → 一句话文案
 * @param hintOf 结果码 → 排查引导
 * @param colorOf 结果码 → 配色
 */
@Composable
fun CaptureHistoryDialog(
    entries: List<CaptureDiagEntry>,
    onDismiss: () -> Unit,
    title: String = "最近采集结果",
    subtitle: String = "只记抽到金额的通知，最多 $CAPTURE_DIAG_LIMIT 条，点一行看完整原文",
    emptyText: String = "还没有记录。收到带金额的支付/收款通知后会出现在这里。",
    labelOf: (String) -> String = ::resultLabel,
    hintOf: (String) -> String = ::resultHint,
    colorOf: @Composable (String) -> Color = { resultColor(it) }
) {
    // 同时只展开一行：诊断的价值在于逐条看原文，而不是整屏铺满；
    // 把展开态收在弹窗这一层，也免去给每行 remember（LazyColumn 复用会串行）
    var expandedKey by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ListCard(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = title,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.color.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    // 说清「为什么只有这些」：用户看不到无金额的通知 / 无影响的信号时，
                    // 不至于以为采集漏了（那些本来就不参与记账）
                    text = subtitle,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (entries.isEmpty()) {
                    Text(
                        text = emptyText,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.color.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(entries) { index, entry ->
                            val key = "$index|${entry.atMillis}"
                            DiagnosticRow(
                                entry = entry,
                                expanded = expandedKey == key,
                                onClick = { expandedKey = if (expandedKey == key) null else key },
                                labelOf = labelOf,
                                hintOf = hintOf,
                                colorOf = colorOf
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "关闭")
                }
            }
        }
    }
}

/**
 * 单条诊断：结果 + 时间一行，「识别方式 + 展开」右对齐换行，原文在下；点击整行切换展开/收起。
 *
 * 展开必须**看得出变化**：通知原文通常只有一两行，如果只是把 `maxLines` 放开，
 * 短文本点下去和没点一样（真机上就是这么被反馈「没有反应」的）。
 * 所以折叠态只留 1 行，展开态额外补出完整时间与结果释义，且右侧标签始终翻转。
 */
@Composable
private fun DiagnosticRow(
    entry: CaptureDiagEntry,
    expanded: Boolean,
    onClick: () -> Unit,
    labelOf: (String) -> String,
    hintOf: (String) -> String,
    colorOf: @Composable (String) -> Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (expanded) {
                    AppTheme.color.primary.copy(alpha = 0.10f)
                } else {
                    AppTheme.color.surfaceVariant.copy(alpha = 0.45f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val method = extractRecognitionMethod(entry.raw)
        // 结果 + 时间一行；「识别方式」与「展开」作为一组换行，整组右对齐
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = labelOf(entry.result),
                style = AppTheme.typography.bodySmall,
                color = colorOf(entry.result)
            )
            Text(
                text = formatDiagTime(entry.atMillis),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            if (method != null) {
                Text(
                    text = method,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.primary
                )
            }
            Text(
                text = if (expanded) "收起 ▴" else "展开 ▾",
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.primary
            )
        }
        Text(
            text = entry.raw,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.color.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis
        )
        if (expanded) {
            if (method != null) {
                Text(
                    text = "识别方式：$method",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
            }
            Text(
                text = "完整时间：${formatDiagFullTime(entry.atMillis)}",
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant
            )
            val hint = hintOf(entry.result)
            if (hint.isNotBlank()) {
                Text(
                    text = hint,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 结果对应的颜色。
 *
 * 只有「已记账」用主色、待确信用橙色，其余（重复 / 忽略 / 无关）统一灰掉：
 * 用户扫一眼就能分辨「这条成了没有」，而不是逐条读文案。
 */
@Composable
private fun resultColor(code: String): Color = when (code) {
    "SAVED" -> Color(0xFF4CAF50)
    "PENDING" -> Color(0xFFFF9800)
    "NO_RULE" -> Color(0xFFF44336)
    else -> AppTheme.color.onSurfaceVariant
}

private fun formatDiagTime(millis: Long): String =
    if (millis <= 0L) "--" else SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA).format(Date(millis))

/** 展开后的完整时间：在毫秒级精度之上补出年份，便于跨年回看。 */
private fun formatDiagFullTime(millis: Long): String =
    if (millis <= 0L) "--" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).format(Date(millis))

/**
 * 从诊断摘要里抽出「识别方式」（支付宝专属 / 微信专属 / 通用）。
 *
 * 写入格式见 `CategorySignalRecorder.withSummary`：`识别方式 微信专属 ｜ …`。
 * 旧记录没有该字段时返回 null，列表不显示标签。
 */
internal fun extractRecognitionMethod(raw: String): String? =
    RECOGNITION_METHOD.find(raw)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

private val RECOGNITION_METHOD = Regex("""识别方式\s+([^｜]+)""")
