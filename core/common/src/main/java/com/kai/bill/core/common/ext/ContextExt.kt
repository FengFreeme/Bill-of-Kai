package com.kai.bill.core.common.ext

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ComponentName
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.kai.bill.core.common.device.Rom
import com.kai.bill.core.common.device.RomDetector

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** 读短信权限：银行卡账单解析的前置条件 */
fun Context.hasReadSmsPermission(): Boolean =
    hasPermission(Manifest.permission.READ_SMS)

/** 该权限无运行时弹窗，只能检测 + 引导跳转 */
fun Context.isNotificationListenerEnabled(): Boolean =
    isAnyServiceEnabledIn(NOTIFICATION_LISTENERS)

/**
 * 与通知使用权同理（只能检测 + 引导）。
 *
 * 只回答「系统里勾选了没有」，与「服务此刻是否真在运行」是两件事 —— 后者由服务自己写入
 * `CaptureState.accessibilityEnabled`；两者不一致（已勾选但未连上）是排查「为什么没采到」的关键线索。
 */
fun Context.isAccessibilityServiceEnabled(): Boolean =
    isAnyServiceEnabledIn(ENABLED_ACCESSIBILITY_SERVICES)

/**
 * 无障碍无需厂商私有入口：`ACTION_ACCESSIBILITY_SETTINGS` 在各 ROM 都指向本机服务列表。
 */
fun Context.openAccessibilitySettings() {
    safeStartActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

/**
 * 系统存的是冒号分隔的 ComponentName 扁平名列表；逐个反扁平化精确比对，
 * 比 `contains(packageName)` 更严谨（避免子串误判）。
 */
private fun Context.isAnyServiceEnabledIn(settingsKey: String): Boolean {
    val flat = Settings.Secure.getString(contentResolver, settingsKey)
    if (flat.isNullOrBlank()) return false
    val enabled = flat.split(':').mapNotNull { ComponentName.unflattenFromString(it) }
    val services = runCatching {
        packageManager.getPackageInfo(packageName, PackageManager.GET_SERVICES).services
    }.getOrNull() ?: return false
    return services.any { svc ->
        val cn = ComponentName(packageName, svc.name)
        enabled.any { it.packageName == cn.packageName && it.className == cn.className }
    }
}

private const val NOTIFICATION_LISTENERS = "enabled_notification_listeners"

private const val ENABLED_ACCESSIBILITY_SERVICES = "enabled_accessibility_services"

/**
 * vivo/OriginOS 的入口藏得更深：先试私有页面，失败回落到 AOSP 标准页。
 */
fun Context.openNotificationListenerSettings() {
    val intent = when (RomDetector.current()) {
        Rom.VIVO -> Intent().setClassName(
            "com.android.settings",
            "com.android.settings.Settings\$NotificationAccessSettingsActivity"
        )

        else -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
    if (!safeStartActivity(intent)) {
        safeStartActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

/** 应用详情页：手动授予短信、自启动等权限的兜底入口 */
fun Context.openAppDetailSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.fromParts("package", packageName, null))
    safeStartActivity(intent)
}

/**
 * 国内 ROM 默认把新装 App 加入电池优化，后台服务极易被回收；
 * 引导关闭优化是降低「通知监听被杀」的关键一步。
 */
fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return runCatching { pm.isIgnoringBatteryOptimizations(packageName) }.getOrDefault(false)
}

/**
 * 首选 AOSP 的定向开关页，但部分国内 ROM（小米 / OPPO / vivo）会拦截该 Intent 或抛
 * `SecurityException`（表现为「点了没反应」），因此打不开就回落到总列表让用户手动关闭。
 */
fun Context.requestIgnoreBatteryOptimizations() {
    val target = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(android.net.Uri.fromParts("package", packageName, null))
    if (!safeStartActivity(target)) {
        safeStartActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

/**
 * 国内 ROM 入口全是私有 Activity 且版本间会变动：逐个尝试候选 Intent，全部失败再回落到应用详情页。
 */
fun Context.openOemAutoStartSettings() {
    for (intent in oemAutoStartIntents(RomDetector.current())) {
        if (safeStartActivity(intent)) return
    }
    openAppDetailSettings()
}

/**
 * 同一 ROM 的不同版本 Activity 路径可能不同：给出最可能的候选由 [openOemAutoStartSettings] 顺序尝试；
 * 三星 / 原生等无可靠入口的返回空列表，交由调用方回落到应用详情页。
 */
private fun oemAutoStartIntents(rom: Rom): List<Intent> = when (rom) {
    Rom.XIAOMI -> listOf(
        Intent("miui.intent.action.OP_AUTO_START")
            .setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
    )
    Rom.OPPO, Rom.ONEPLUS -> listOf(
        Intent().setClassName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        Intent().setClassName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.startupapp.StartupAppListActivity"
        )
    )
    Rom.HUAWEI, Rom.HONOR -> listOf(
        Intent().setClassName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        )
    )
    Rom.VIVO -> listOf(
        Intent().setClassName(
            "com.iqoo.secure",
            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
        ),
        Intent().setClassName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        )
    )
    else -> emptyList()
}

/**
 * 目标页面不存在时静默失败、不抛 [ActivityNotFoundException]：ROM 适配大量使用
 * 「先试私有页、再回退标准页」，首次尝试抛异常会中断整个流程，故统一收口。
 *
 * @param intent 待启动的 Intent，会自动补上 [Intent.FLAG_ACTIVITY_NEW_TASK]
 */
fun Context.safeStartActivity(intent: Intent): Boolean = try {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}
