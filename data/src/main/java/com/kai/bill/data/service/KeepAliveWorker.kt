package com.kai.bill.data.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

/**
 * 采集链路的定期体检，每 15 分钟一次（见 [CaptureKeepAlive]）。
 *
 * NOTE: 进程被系统清理后，**这个任务仍留在系统侧**；它到点执行时会把进程唤醒，
 * 于是 `KaiApplication` 的启动流程与 [CaptureKeepAlive.rearm] 一起再跑一遍。
 *
 * 依赖走 [CaptureKeepAliveEntryPoint] 而不是 `@HiltWorker`：后者要额外接 `hilt-work`
 * 并配置 `HiltWorkerFactory`，为两个后台入口不值得。
 */
class KeepAliveWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = runCatching {
            EntryPointAccessors.fromApplication(
                applicationContext,
                CaptureKeepAliveEntryPoint::class.java
            )
        }.getOrNull() ?: return Result.success()

        // 体检内部已吞掉异常（它是尽力而为的恢复动作）；这里再兜一层，
        // 避免 Worker 以失败告终触发重试、把周期窗口挤乱
        runCatching { entryPoint.captureKeepAlive().healthCheck() }

        return Result.success()
    }
}
