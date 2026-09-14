package com.kai.bill.feature.onboarding

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.prefs.CaptureState
import com.kai.bill.feature.common.SectionTitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 权限自检与引导。M4 落地：监听权限 / 电池优化 / 厂商自启动 / 采集开关。 */
@Composable
fun PermissionCheckScreen(
    modifier: Modifier = Modifier,
    viewModel: PermissionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val captureState by viewModel.captureState.collectAsStateWithLifecycle(initialValue = CaptureState())
    val captureEnabled by viewModel.captureEnabled.collectAsStateWithLifecycle(initialValue = false)

    LaunchedEffect(Unit) { viewModel.refresh() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") { SectionTitle(text = "通知监听与采集") }

        item(key = "toggle") {
            CaptureToggleCard(
                enabled = captureEnabled,
                onToggle = viewModel::setCaptureEnabled
            )
        }

        item(key = "status") {
            StatusCard(
                listenerEnabled = uiState.listenerEnabled,
                listenerConnected = captureState.notificationListenerEnabled,
                lastSuccessAt = captureState.captureLastSuccessAt,
                lastResult = captureState.captureLastResult,
                lastRaw = captureState.captureLastRaw,
                onReconnect = { viewModel.onStepAction(GuideAction.LISTENER_SETTINGS) }
            )
        }

        items(uiState.steps, key = { it.id }) { step ->
            StepCard(
                step = step,
                done = stepDone(step, uiState),
                onAction = { viewModel.onStepAction(step.action) }
            )
        }
    }
}

/** 自动采集主开关卡片。 */
@Composable
private fun CaptureToggleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    ListCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "自动采集记账",
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.color.onSurface
                )
                Text(
                    text = "开启后保持后台监听，降低漏记",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/** 步骤完成态：null 表示该步骤无法程序化检测（如自启动），需用户手动处理。 */
private fun stepDone(step: GuideStep, uiState: PermissionUiState): Boolean? = when (step.action) {
    GuideAction.LISTENER_SETTINGS -> uiState.listenerEnabled
    GuideAction.BATTERY_OPTIMIZATION -> !uiState.batteryOptimized
    GuideAction.AUTO_START -> null
    GuideAction.APP_DETAILS -> null
}

@Composable
private fun StatusCard(
    listenerEnabled: Boolean,
    listenerConnected: Boolean,
    lastSuccessAt: Long,
    lastResult: String = "",
    lastRaw: String = "",
    onReconnect: () -> Unit = {}
) {
    // 三态：未授权 / 已授权但服务断连 / 正常监听。
    // listenerEnabled = 系统「通知读取」权限是否勾选；listenerConnected = KaiNotificationListener
    // 在 onListenerConnected/Disconnected 写入的真实连接态（见 CaptureState.notificationListenerEnabled）。
    val (dotColor, statusText, showReconnect) = when {
        !listenerEnabled -> Triple(Color(0xFFFF9800), "通知监听未开启（未授权）", false)
        !listenerConnected -> Triple(Color(0xFFF44336), "已授权但服务未连接", true)
        else -> Triple(Color(0xFF4CAF50), "正常监听中", false)
    }
    ListCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "采集状态",
                style = AppTheme.typography.titleMedium,
                color = AppTheme.color.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusDot(color = dotColor)
                Text(
                    text = statusText,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.color.onSurfaceVariant
                )
            }
            if (showReconnect) {
                Text(
                    text = "权限已开启，但监听服务并未真正运行（常见于重装 App 或厂商清理后台）。" +
                        "请关闭再重新打开「通知读取」权限以重新绑定服务。",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
                Button(onClick = onReconnect, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "去重新开启")
                }
            }
            Text(
                text = "最近成功采集：${formatTime(lastSuccessAt)}",
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant
            )
            Text(
                text = "最近采集结果：${resultLabel(lastResult)}",
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant
            )
            val hint = resultHint(lastResult)
            if (hint.isNotBlank()) {
                Text(
                    text = hint,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
            }
            if (lastRaw.isNotBlank()) {
                Text(
                    text = "原始通知：$lastRaw",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun resultLabel(code: String): String = when (code) {
    "SAVED" -> "已记账"
    "DUPLICATE" -> "重复跳过"
    "NO_AMOUNT" -> "识别到通知但没抽到金额"
    "NO_RULE" -> "无匹配规则"
    else -> "暂无"
}

/** 解析失败时的排查引导（M6 异常排查增强）。 */
private fun resultHint(code: String): String = when (code) {
    "NO_RULE" -> "该通知无匹配规则：可在 App 内手动补录。"
    "NO_AMOUNT" -> "识别到通知但未抽到金额：金额常在详情页，建议手动补录。"
    else -> ""
}

@Composable
private fun StepCard(step: GuideStep, done: Boolean?, onAction: () -> Unit) {
    val badgeText = when (done) {
        true -> "已完成"
        false -> "待处理"
        null -> "需手动"
    }
    val badgeColor = when (done) {
        true -> Color(0xFF4CAF50)
        false -> Color(0xFFFF9800)
        null -> AppTheme.color.onSurfaceVariant
    }
    ListCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = step.title,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.color.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = badgeText,
                    style = AppTheme.typography.bodySmall,
                    color = badgeColor
                )
            }
            Text(
                text = step.description,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(text = "去设置")
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

private fun formatTime(millis: Long): String =
    if (millis <= 0L) "暂无成功记录" else {
        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        sdf.format(Date(millis))
    }

@Composable
fun PermissionCheckRoute(modifier: Modifier = Modifier) {
    PermissionCheckScreen(modifier = modifier)
}
