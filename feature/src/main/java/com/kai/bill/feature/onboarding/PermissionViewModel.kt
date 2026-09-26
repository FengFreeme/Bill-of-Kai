package com.kai.bill.feature.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kai.bill.core.common.device.Rom
import com.kai.bill.core.common.device.RomDetector
import com.kai.bill.core.common.overlay.CaptureHint
import com.kai.bill.core.common.overlay.CaptureHintKind
import com.kai.bill.core.common.overlay.CaptureHintOverlay
import com.kai.bill.core.common.overlay.ReviewCardOverlay
import com.kai.bill.core.common.ext.isAccessibilityServiceEnabled
import com.kai.bill.core.common.ext.isIgnoringBatteryOptimizations
import com.kai.bill.core.common.ext.isNotificationListenerEnabled
import com.kai.bill.core.common.ext.openAccessibilitySettings
import com.kai.bill.core.common.ext.openAppDetailSettings
import com.kai.bill.core.common.ext.openNotificationListenerSettings
import com.kai.bill.core.common.ext.openOemAutoStartSettings
import com.kai.bill.core.common.ext.requestIgnoreBatteryOptimizations
import com.kai.bill.core.prefs.CaptureState
import com.kai.bill.domain.time.Clock
import com.kai.bill.core.prefs.KaiPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 权限引导 ViewModel。
 *
 * 检测逻辑全部走 [com.kai.bill.core.common.ext] 里的纯函数（基于系统设置读取），
 * 不持有任何业务规则；跳转动作也收口在扩展函数，保证「检测 + 引导」与 UI 解耦。
 */
@HiltViewModel
class PermissionViewModel @Inject constructor(
    application: Application,
    private val kaiPrefs: KaiPrefs,
    private val reviewCardOverlay: ReviewCardOverlay,
    private val captureHintOverlay: CaptureHintOverlay,
    private val clock: Clock
) : AndroidViewModel(application) {

    /** 采集链路状态：连接态 / 最近成功时间 / 主开关。 */
    val captureState: Flow<CaptureState> = kaiPrefs.captureState

    /** 用户主开关：是否启用自动采集。UI 仅写 prefs，前台服务启停由 data 层监听完成。 */
    val captureEnabled: Flow<Boolean> = kaiPrefs.captureState.map { it.captureEnabled }

    private val rom: Rom = RomDetector.current()

    private val _uiState = MutableStateFlow(
        PermissionUiState(
            rom = rom,
            listenerEnabled = false,
            accessibilityGranted = false,
            batteryOptimized = true,
            steps = PermissionGuideSteps.forRom(rom)
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * 重新读取系统设置，刷新各步骤完成态。页面 onResume / 从设置页返回时调用。
     */
    fun refresh() {
        val ctx = getApplication<Application>()
        _uiState.update {
            it.copy(
                listenerEnabled = ctx.isNotificationListenerEnabled(),
                accessibilityGranted = ctx.isAccessibilityServiceEnabled(),
                batteryOptimized = !ctx.isIgnoringBatteryOptimizations()
            )
        }
    }

    /**
     * 测试确认卡片：用**最近一笔账单**显示悬浮卡片。
     *
     * 悬浮卡片曾静默不生效（不抛异常也无日志），只能靠真付钱尝试；
     * 这里提供一个不花钱的验证入口。
     */
    fun testCard() {
        viewModelScope.launch {
            val shown = runCatching { reviewCardOverlay.showLatest() }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    cardTestHint = if (shown) {
                        "卡片已显示。没看到？请确认无障碍服务处于「正常识别中」。"
                    } else {
                        // 不猜原因：真正的原因（NO_SERVICE / NO_BILL / FAILED: …）
                        // 由悬浮层实现写进「卡片投递结果」，这里只负责指过去。
                        "没能显示，原因见下方「卡片投递结果」。"
                    }
                )
            }
        }
    }

    /**
     * 测试提示条：直接弹一条「此账单已记录，无需重复记录」。
     *
     * 与 [testCard] 同理由：提示条与卡片同走 `WindowManager` 那条会静默失败的路径。
     * 先撤掉可能挂着的确认卡片 —— 提示条按优先级会让位给卡片，而测试入口必须每次可见结果。
     * 每次都换去重身份 —— 宿主会挡下「同一条提示 10 秒内不重复弹」，那是采集侧节流，
     * 不该作用在这个入口上。
     */
    fun testHint() {
        viewModelScope.launch {
            runCatching { captureHintOverlay.dismiss() }
            val shown = runCatching {
                captureHintOverlay.show(
                    CaptureHint(
                        kind = CaptureHintKind.ALREADY_RECORDED,
                        key = "test-${clock.nowMillis()}"
                    )
                )
            }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    hintTestHint = if (shown) {
                        "提示条已显示（屏幕底部）。没看到？请确认无障碍服务处于「正常识别中」。"
                    } else {
                        // 真正的原因由宿主写进「提示条投递结果」，这里只负责指过去
                        "没能显示，原因见下方「提示条投递结果」。"
                    }
                )
            }
        }
    }

    /** 切换自动采集开关，仅写入 prefs；前台服务的启停由 data 层 CaptureController 监听此值完成。 */
    fun setCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch { kaiPrefs.setCaptureEnabled(enabled) }
    }

    /** 触发某一步骤的引导跳转；跳转后延时复查状态，承载「从设置页返回」的反馈。 */
    fun onStepAction(action: GuideAction) {
        val ctx = getApplication<Application>()
        when (action) {
            GuideAction.LISTENER_SETTINGS -> ctx.openNotificationListenerSettings()
            GuideAction.ACCESSIBILITY_SETTINGS -> ctx.openAccessibilitySettings()
            GuideAction.BATTERY_OPTIMIZATION -> ctx.requestIgnoreBatteryOptimizations()
            GuideAction.AUTO_START -> ctx.openOemAutoStartSettings()
            GuideAction.APP_DETAILS -> ctx.openAppDetailSettings()
        }
        viewModelScope.launch {
            delay(800)
            refresh()
        }
    }
}

/**
 * 引导页 UI 状态。
 *
 * @property accessibilityGranted 只回答「系统里勾选了没有」；「服务此刻是否真在跑」由服务自己写入
 *           `CaptureState.accessibilityEnabled`，两者不一致本身就是排查线索
 * @property batteryOptimized true 表示仍被电池优化限制（需引导关闭）
 * @property cardTestHint 测试确认卡片的结果提示；空串表示还没点过
 * @property hintTestHint 测试提示条的结果提示；空串表示还没点过
 */
data class PermissionUiState(
    val rom: Rom,
    val listenerEnabled: Boolean,
    val accessibilityGranted: Boolean,
    val batteryOptimized: Boolean,
    val steps: List<GuideStep>,
    val cardTestHint: String = "",
    val hintTestHint: String = ""
)
