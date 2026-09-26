package com.kai.bill.data.service

import android.content.Context
import android.content.Intent
import com.kai.bill.core.prefs.KaiPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 采集开关与保活服务的接线器。
 *
 * 不直接接收 UI 调用（feature 模块不依赖 data）：只监听 [KaiPrefs.captureState.captureEnabled]，
 * 用户开关写入 prefs 后，这里据此启停 [CaptureForegroundService]。如此 UI 与采集实现在编译期解耦。
 *
 * 在 [com.kai.bill.KaiApplication] 中注入本单例以触发其 `init` 订阅。
 */
@Singleton
class CaptureController @Inject constructor(
    @ApplicationContext private val context: Context,
    kaiPrefs: KaiPrefs,
    private val keepAlive: CaptureKeepAlive
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        kaiPrefs.captureState
            .map { it.captureEnabled }
            .distinctUntilChanged()
            .onEach { enabled ->
                if (enabled) {
                    // 定期体检：进程被 ROM 清理后，靠它把服务与监听重新武装起来
                    keepAlive.schedulePeriodicCheck()
                    // 应用启动 / 用户开启时顺手体检一次：进程刚起来时通知监听往往还没绑上，
                    // 这一步既完成重绑，也顺便把「链路到底通没通」判出来 ——
                    // 用户刚打开应用，是发现采集已经停了的第一个好时机
                    scope.launch { keepAlive.healthCheck() }
                } else {
                    keepAlive.cancelPeriodicCheck()
                    stopService()
                }
            }
            .launchIn(scope)
    }

    private fun stopService() {
        runCatching {
            context.stopService(Intent(context, CaptureForegroundService::class.java))
        }
    }
}
