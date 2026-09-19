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
    private val captureHintOverlay: CaptureHintOverlay
) : AndroidViewModel(application) {

    /** 采集链路状态（连接态 / 最近成功时间 / 主开关），供页面展示与开关绑定。 */
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
     * 测试确认卡片：用**最近一笔账单**把悬浮卡片显示出来。
     *
     * 存在的理由：这个功能曾经「静默不生效」过 —— 卡片一次都没弹出来，
     * 而系统既不抛异常也不留日志，只能靠反复付真钱去试。
     * 给一个不花钱的验证入口，是把「可验证性」补回来。
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
     * 与 [testCard] 同一套理由 —— 提示条与卡片同走 `WindowManager` 那条**会静默失败**
     * 的路径，没有不花钱的验证入口就只能靠真去买一笔来试。
     *
     * 先撤掉可能挂着的确认卡片：提示条按优先级会让位给卡片（避免顶掉用户正在操作的内容），
     * 而测试入口必须每次都看得见结果，所以这里先把卡片清掉。
     */
    fun testHint() {
        viewModelScope.launch {
            runCatching { captureHintOverlay.dismiss() }
            val shown = runCatching {
                captureHintOverlay.show(CaptureHint(CaptureHintKind.ALREADY_RECORDED))
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
 * @property rom 当前机型 ROM，仅用于展示
 * @property listenerEnabled 通知使用权是否已授予（实时检测）
 * @property accessibilityGranted 无障碍服务是否已在系统设置中启用（实时检测）。
 *           注意它只回答「系统里勾选了没有」；「服务此刻是否真的在跑」由服务自己写入
 *           `CaptureState.accessibilityEnabled`，两者不一致本身就是排查线索
 * @property batteryOptimized true 表示仍被电池优化限制（需引导关闭）
 * @property steps 当前 ROM 下的引导步骤列表
 * @property cardTestHint 「测试确认卡片」的结果提示；空串表示还没点过
 * @property hintTestHint 「测试提示条」的结果提示；空串表示还没点过
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
