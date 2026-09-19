package com.kai.bill.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.prefs.CAPTURE_DIAG_LIMIT
import com.kai.bill.core.prefs.CaptureState
import com.kai.bill.feature.common.SectionTitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 权限自检与引导。M4 落地：监听权限 / 电池优化 / 厂商自启动 / 采集开关；M-L2 增补无障碍（分类识别）。 */
@Composable
fun PermissionCheckScreen(
    modifier: Modifier = Modifier,
    viewModel: PermissionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val captureState by viewModel.captureState.collectAsStateWithLifecycle(initialValue = CaptureState())
    val captureEnabled by viewModel.captureEnabled.collectAsStateWithLifecycle(initialValue = false)
    var showHistory by remember { mutableStateOf(false) }
    var showSignalHistory by remember { mutableStateOf(false) }

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
                historyCount = captureState.captureRecent.size,
                onShowHistory = { showHistory = true },
                onReconnect = { viewModel.onStepAction(GuideAction.LISTENER_SETTINGS) }
            )
        }

        item(key = "category_title") {
            SectionTitle(text = "分类识别（自动补分类 / 补记账）")
        }

        item(key = "category_card") {
            AccessibilityCard(
                granted = uiState.accessibilityGranted,
                connected = captureState.accessibilityEnabled,
                testHint = uiState.cardTestHint,
                cardDelivery = captureState.cardDelivery,
                lastResult = captureState.signalLastResult,
                lastText = captureState.signalLastText,
                lastAtMillis = captureState.signalLastAtMillis,
                historyCount = captureState.signalRecent.size,
                onOpen = { viewModel.onStepAction(GuideAction.ACCESSIBILITY_SETTINGS) },
                onTestCard = viewModel::testCard,
                onShowHistory = { showSignalHistory = true }
            )
        }

        // 步骤 key 统一加前缀：它们与上面几个固定 key 是**两个独立的名字空间**，
        // 裸用 step.id 会让「新增一个固定卡片」和「新增一个引导步骤」在命名上撞车
        // （踩过一次：卡片 key="accessibility" 与步骤 id="accessibility" 冲突直接闪退）。
        items(uiState.steps, key = { "step_${it.id}" }) { step ->
            StepCard(
                step = step,
                done = stepDone(step, uiState),
                onAction = { viewModel.onStepAction(step.action) }
            )
        }
    }

    if (showHistory) {
        CaptureHistoryDialog(
            entries = captureState.captureRecent,
            onDismiss = { showHistory = false }
        )
    }

    // 与通知侧的弹窗是**同一个组件**，只是换了三张「结果码 → 文案 / 释义 / 颜色」映射表
    if (showSignalHistory) {
        CaptureHistoryDialog(
            entries = captureState.signalRecent,
            onDismiss = { showSignalHistory = false },
            title = "最近识别记录",
            subtitle = "只记「对账单产生了影响」的信号，最多 $CAPTURE_DIAG_LIMIT 条，点一行看完整内容",
            emptyText = "还没有记录。识别到账单内容并补上分类 / 补记一笔后会出现在这里。",
            labelOf = ::signalResultLabel,
            hintOf = ::signalResultHint,
            colorOf = { signalResultColor(it) }
        )
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
    GuideAction.ACCESSIBILITY_SETTINGS -> uiState.accessibilityGranted
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
    historyCount: Int = 0,
    onShowHistory: () -> Unit = {},
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
            // 只显示最近一条时，几小时前那条早就被顶掉了；这里给个入口回看历史
            Text(
                text = if (historyCount > 0) {
                    "查看最近 $CAPTURE_DIAG_LIMIT 条记录（$historyCount）"
                } else {
                    "查看最近 $CAPTURE_DIAG_LIMIT 条记录"
                },
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onShowHistory)
                    .padding(vertical = 8.dp)
            )
            Text(
                text = "仅记录抽到金额的通知；无金额的提醒不参与记账，也不会记进来。",
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant
            )
        }
    }
}

/** 结果编码 → 用户看得懂的一句话；诊断历史弹窗复用同一张表，避免两处文案走偏。 */
internal fun resultLabel(code: String): String = when (code) {
    "SAVED" -> "已记账"
    "DUPLICATE" -> "重复跳过"
    "REPLAY" -> "同条通知重发"
    "PENDING" -> "待确认（等你拍板）"
    "PENDING_DUP" -> "待确认（已在列表中）"
    "NO_AMOUNT" -> "识别到通知但没抽到金额"
    "EXCLUDED" -> "已忽略（失败 / 提醒 / 营销类）"
    "NO_RULE" -> "与账单语汇无关"
    else -> "暂无"
}

/**
 * 各结果对应的排查引导（M6 异常排查增强）。
 *
 * 诊断历史弹窗展开一行时复用它，两处文案是同一套解释，不该各写一份。
 */
internal fun resultHint(code: String): String = when (code) {
    "REPLAY" -> "同一条系统通知被更新后重新推送，已按同一笔处理，不会重复记账。"
    "PENDING" -> "可去「设置 → 待审核记录」一键确认或丢弃。"
    "PENDING_DUP" -> "这笔已在待审核记录里，未重复添加。可去「设置 → 待审核记录」处理。"
    "NO_AMOUNT" -> "识别到通知但未抽到金额：金额常在详情页，建议手动补录。"
    "EXCLUDED" -> "支付失败、账单提醒、优惠活动等通知会被自动忽略，属正常行为。"
    "NO_RULE" -> "该通知与账单语汇无关：如确为账单，可在 App 内手动补录。"
    else -> ""
}

/**
 * 分类识别（L2 无障碍）卡片。
 *
 * 三态与通知监听卡一致：未开启 / 已开启但服务未连接 / 正常识别。
 * 「系统里勾选了没有」([granted]) 与「服务是否真的在跑」([connected]) 是两个信号，
 * 两者不一致（勾了但没连上）恰恰是「为什么分类没生效」的第一线索。
 *
 * 它在功能上**不是必达项**：未开启时 L1 通知记账照常工作，只是分类容易落到「其他」。
 */
@Composable
private fun AccessibilityCard(
    granted: Boolean,
    connected: Boolean,
    testHint: String,
    cardDelivery: String,
    lastResult: String,
    lastText: String,
    lastAtMillis: Long,
    historyCount: Int,
    onOpen: () -> Unit,
    onTestCard: () -> Unit,
    onShowHistory: () -> Unit
) {
    val (dotColor, statusText) = when {
        !granted -> Color(0xFFFF9800) to "未开启（不影响自动记账，只影响分类准度）"
        !connected -> Color(0xFFF44336) to "已开启但识别服务未连接"
        else -> Color(0xFF4CAF50) to "正常识别中"
    }

    ListCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "分类识别（无障碍）",
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

            if (!granted) {
                Text(
                    text = "开启后可读取支付页面上的商户信息，把落进「其他」的账单自动改成餐饮 / 交通等分类；" +
                        "通知没来、但页面上有这笔消费时，也会自动补记一笔。",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "去开启")
                }
            } else if (!connected) {
                Text(
                    text = "权限已开启，但识别服务并未真正运行（常见于重装 App 或厂商清理后台）。" +
                        "请关闭再重新打开「小凯记账 · 分类识别」以重新绑定服务。",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "去重新开启")
                }
            }

            // —— 以下与「采集状态」卡同构：时间 → 结果 → 释义 → 原文 → 历史入口 → 说明 ——
            // 两条链路在引导页上长得一样，用户不必为「通知」和「页面识别」各学一套看法。
            if (lastAtMillis > 0L) {
                Text(
                    text = "最近处理时间：${formatTime(lastAtMillis)}",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
            }
            Text(
                text = "最近处理：${signalResultLabel(lastResult)}",
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant
            )
            val hint = signalResultHint(lastResult)
            if (hint.isNotBlank()) {
                Text(
                    text = hint,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
            }
            // 这是用户唯一能回答「为什么这笔被分到了餐饮 / 为什么多了一笔账」的入口，
            // 因此保留原文（截断后）而不是只给一个编码
            if (lastText.isNotBlank()) {
                Text(
                    text = "识别到的内容：$lastText",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 卡片靠**无障碍悬浮层**显示，不需要任何权限；唯一的条件是服务真的在跑
            // （上面的绿点已经反映）。这里给一个不花钱的验证入口 ——
            // 这个功能曾经「静默不生效」过（系统不抛异常、不留日志），
            // 没有入口就只能靠反复付真钱去试。
            if (granted) {
                OutlinedButton(onClick = onTestCard, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "测试确认卡片")
                }
                if (testHint.isNotBlank()) {
                    Text(
                        text = testHint,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.color.onSurfaceVariant
                    )
                }
                // 卡片投递是会「静默失败」的环节，把结果（含失败原因）直接摆出来，
                // 省得用户靠反复付真钱去猜
                if (cardDelivery.isNotBlank()) {
                    Text(
                        text = "卡片投递结果：$cardDelivery",
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.color.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 只显示最近一条时，几分钟前那条早就被顶掉了；这里给个入口回看历史
            Text(
                text = if (historyCount > 0) {
                    "查看最近 $CAPTURE_DIAG_LIMIT 条记录（$historyCount）"
                } else {
                    "查看最近 $CAPTURE_DIAG_LIMIT 条记录"
                },
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onShowHistory)
                    .padding(vertical = 8.dp)
            )
            Text(
                text = "页面上读到的文字只在本机内存里短暂使用，用于判断分类；不上传、也不保存。",
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant
            )
        }
    }
}

/**
 * 信号处理结果编码 → 用户看得懂的一句话。
 *
 * 取值与 `ReconcileOutcome` 一一对应；**新增枚举项时必须同步这张表**，
 * 否则用户会看到 `ENRICHED` 这样的原始字符串 —— 与通知诊断的 [resultLabel] 是同一条约定。
 */
internal fun signalResultLabel(code: String): String = when (code) {
    "ENRICHED" -> "已补全分类"
    "CREATED" -> "已自动补记一笔"
    "DUPLICATE" -> "已有一笔，未重复记账"
    "PENDING" -> "待确认（等你拍板）"
    "AMBIGUOUS" -> "附近有多笔，未自动处理"
    "NO_MATCH" -> "识别到内容，但没匹配到分类"
    "NO_AMOUNT" -> "识别到内容，但没抽到金额"
    "EXCLUDED" -> "已忽略（失败 / 提醒 / 营销类）"
    "FAILED" -> "处理失败"
    else -> "暂无"
}

/**
 * 信号处理结果的配色；与采集诊断同一套取向 ——
 * **「确实记上了」用绿色，「需要用户看一眼」用橙色，其余统一灰掉**，
 * 用户扫一眼就能分辨「这条成了没有」，不必逐条读文案。
 */
@Composable
internal fun signalResultColor(code: String): Color = when (code) {
    "ENRICHED", "CREATED" -> Color(0xFF4CAF50)
    "PENDING", "AMBIGUOUS", "NO_MATCH" -> Color(0xFFFF9800)
    else -> AppTheme.color.onSurfaceVariant
}

/** 各信号处理结果对应的排查引导；与 [signalResultLabel] 是同一套解释，不该各写一份。 */
internal fun signalResultHint(code: String): String = when (code) {
    "ENRICHED" -> "原本落「其他」的账单，已按页面内容改成具体分类。"
    "CREATED" -> "通知没到，但页面上有这笔消费，已按页面内容自动记了一笔。"
    "DUPLICATE" -> "同一笔已经记过了（通常是通知先到），没有重复添加。"
    "PENDING" -> "方向判不出来，已加入待审核记录，可去「设置 → 待审核记录」处理。"
    "AMBIGUOUS" -> "同一时间段有多笔待归类账单，无法确定是哪一笔，因此没有自动修改。"
    "NO_MATCH" -> "读到了页面内容，但没命中任何分类词；可在账单里手动改分类。"
    "NO_AMOUNT" -> "读到了页面内容但没抽到可信金额，因此没有自动记一笔。"
    "EXCLUDED" -> "支付失败、活动推送等内容会被自动忽略，属正常行为。"
    "FAILED" -> "处理过程中出现异常，可查看采集诊断记录。"
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
        // 精确到毫秒：同一条通知的更新重发常在同一秒内到达，秒级仍分不出先后
        val sdf = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA)
        sdf.format(Date(millis))
    }

@Composable
fun PermissionCheckRoute(modifier: Modifier = Modifier) {
    PermissionCheckScreen(modifier = modifier)
}
