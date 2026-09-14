package com.kai.bill.feature.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kai.bill.core.common.device.Rom
import com.kai.bill.core.common.device.RomDetector
import com.kai.bill.core.common.ext.isIgnoringBatteryOptimizations
import com.kai.bill.core.common.ext.isNotificationListenerEnabled
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
    private val kaiPrefs: KaiPrefs
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
                batteryOptimized = !ctx.isIgnoringBatteryOptimizations()
            )
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
 * @property batteryOptimized true 表示仍被电池优化限制（需引导关闭）
 * @property steps 当前 ROM 下的引导步骤列表
 */
data class PermissionUiState(
    val rom: Rom,
    val listenerEnabled: Boolean,
    val batteryOptimized: Boolean,
    val steps: List<GuideStep>
)
