package com.kai.bill.domain.usecase.capture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 采集控制 facade（Observe / Start / Stop）。
 *
 * M4/M5 再拆或接 prefs + ForegroundService；此处仅占位签名，避免 ViewModel 提前依赖。
 */
class CaptureUseCases {

    fun observeCaptureAlive(): Flow<Boolean> = flowOf(false)

    fun startCapture() {
        // TODO: M4 拉起 CaptureForegroundService / 校验通知使用权
    }

    fun stopCapture() {
        // TODO: M4 停止保活服务（不保证系统不杀后台）
    }
}
