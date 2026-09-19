package com.kai.bill.data.capture.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 当前存活的 [KaiAccessibilityService] 句柄。
 *
 * **存在的唯一理由**：`TYPE_ACCESSIBILITY_OVERLAY` 类型的窗口需要一个**有效的窗口 token**。
 * 用 Application 上下文取到的 `WindowManager` 其 token 为 null，`addView` 会直接抛
 * `BadTokenException: token null is not valid`（真机已验证过一次），
 * 因此悬浮卡片只能由**服务自己的上下文**来挂 —— 而服务的生命周期由系统掌握，
 * 无法注入，只能由它在 `onServiceConnected` / `onDestroy` 里自行登记。
 *
 * 句柄刻意只暴露 [serviceContext]：调用方拿不到服务本身，
 * 也就无法越权去调用它的无障碍能力（读节点、派发手势等）。
 */
@Singleton
class AccessibilityServiceHolder @Inject constructor() {

    @Volatile
    private var service: AccessibilityService? = null

    /** 服务被系统绑定时登记 */
    fun attach(service: AccessibilityService) {
        this.service = service
    }

    /**
     * 服务销毁时注销。
     *
     * 只在 `onDestroy` 调用：`onUnbind` 不代表进程会结束，系统可能马上重新绑定，
     * 提前清空会让「服务明明在跑、卡片却挂不上」变成间歇性问题。
     */
    fun detach(service: AccessibilityService) {
        if (this.service === service) this.service = null
    }

    /** 服务的上下文；服务未绑定时为 null */
    val serviceContext: Context?
        get() = service
}
