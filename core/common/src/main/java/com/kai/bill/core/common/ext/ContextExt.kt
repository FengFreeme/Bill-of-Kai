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

/**
 * 判断某个权限是否已授予。
 *
 * @param permission 权限名，如 [Manifest.permission.READ_SMS]
 * @return true 表示已授予
 */
fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * 是否已授予读取短信权限（M5 银行卡账单解析的前置条件）。
 *
 * @return true 表示已授予
 */
fun Context.hasReadSmsPermission(): Boolean =
    hasPermission(Manifest.permission.READ_SMS)

/**
 * 判断「通知使用权」是否已对本 App 授予。
 *
 * 该权限不走运行时权限弹窗，只能由用户在系统设置里手动开启，
 * 因此不存在 requestPermission，只能检测 + 引导跳转。
 *
 * @return true 表示已授予，通知监听服务才能收到内容
 */
fun Context.isNotificationListenerEnabled(): Boolean =
    isAnyServiceEnabledIn(NOTIFICATION_LISTENERS)

/**
 * 判断本应用的无障碍服务是否已在系统设置中启用。
 *
 * 与通知使用权同理：只能检测 + 引导跳转，不能主动开启。
 *
 * 注意它回答的是「系统里勾选了没有」，与「服务此刻是否真的在运行」是两件事 ——
 * 后者由服务自己在 `onServiceConnected` / `onUnbind` 写入 `CaptureState.accessibilityEnabled`，
 * 两者不一致（已勾选但服务未连上）恰恰是排查「为什么没采到」的关键线索。
 *
 * @return true 表示已在系统设置中启用
 */
fun Context.isAccessibilityServiceEnabled(): Boolean =
    isAnyServiceEnabledIn(ENABLED_ACCESSIBILITY_SERVICES)

/**
 * 跳转系统「无障碍」设置页。
 *
 * 无障碍不需要厂商私有入口：`ACTION_ACCESSIBILITY_SETTINGS` 在各 ROM 上都指向
 * 本机无障碍服务列表，用户在其下找到本应用开关即可。
 */
fun Context.openAccessibilitySettings() {
    safeStartActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

/**
 * 某类「系统设置里存储的已启用服务列表」中，是否包含本应用声明的任一服务。
 *
 * 系统存的是冒号分隔的 ComponentName 扁平名列表（包部分为本应用 applicationId，
 * 即便服务组件声明在 data 模块），逐个反扁平化后与「本应用已声明的服务」精确比对，
 * 比直接 contains(packageName) 更严谨（避免子串误判），且跨厂商 ROM 通用。
 *
 * @param settingsKey 设置键，如 [NOTIFICATION_LISTENERS]
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

/** 系统设置中「已启用的通知监听器」键 */
private const val NOTIFICATION_LISTENERS = "enabled_notification_listeners"

/** 系统设置中「已启用的无障碍服务」键 */
private const val ENABLED_ACCESSIBILITY_SERVICES = "enabled_accessibility_services"

/**
 * 跳转到「通知使用权」设置页。
 *
 * ROM:vivo —— OriginOS 的入口藏得更深，先尝试私有页面，
 * 失败时自动回落到 AOSP 标准页，保证任何机型都不会卡住用户。
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

/**
 * 跳转到本 App 的应用详情页（用于手动授予短信、自启动等权限）。
 */
fun Context.openAppDetailSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.fromParts("package", packageName, null))
    safeStartActivity(intent)
}

/**
 * 本 App 是否已加入电池优化白名单。
 *
 * 国内 ROM 默认把新装 App 加入电池优化（即「受限制」），后台服务极易被回收；
 * 引导用户关闭优化是降低「通知监听被杀」的关键一步。
 *
 * @return true 表示已被加入白名单（不被优化），监听服务更不容易被杀
 */
fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return runCatching { pm.isIgnoringBatteryOptimizations(packageName) }.getOrDefault(false)
}

/**
 * 跳转系统「电池优化白名单」申请页。
 *
 * 首选 AOSP 标准页 [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS]，
 * 它会直接定位到本 App 的开关，体验最好。但部分国内 OEM ROM（小米 / OPPO / vivo 等）
 * 会拦截该 Intent（resolve 不到目标，[safeStartActivity] 静默失败），或本 App
 * 未声明 [Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] 触发 SecurityException，
 * 表现为「点了没反应」。因此一旦首选页打不开，立即回落到
 * [Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS]（电池优化总列表，所有机型通用），
 * 让用户手动找到本 App 关闭优化，保证任何机型都不会卡住。
 */
fun Context.requestIgnoreBatteryOptimizations() {
    val target = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(android.net.Uri.fromParts("package", packageName, null))
    if (!safeStartActivity(target)) {
        safeStartActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

/**
 * 跳转厂商「自启动 / 后台耗电管理」设置页。
 *
 * 国内 ROM 的入口全是私有 Activity，且版本间会变动，因此先用 [oemAutoStartIntents]
 * 拿到一组候选 Intent 逐个尝试，全部失败再回落到应用详情页，保证任何机型都不会卡住。
 */
fun Context.openOemAutoStartSettings() {
    for (intent in oemAutoStartIntents(RomDetector.current())) {
        if (safeStartActivity(intent)) return
    }
    openAppDetailSettings()
}

/**
 * 各 ROM 的「自启动管理」候选 Intent。
 *
 * 同一 ROM 的不同版本 Activity 路径可能不同，这里给出最可能的候选，由
 * [openOemAutoStartSettings] 顺序尝试；无法给出可靠入口的 ROM（三星 / 原生）返回空列表，
 * 交由调用方回落到应用详情页。
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
 * 安全启动 Activity：目标页面不存在时静默失败，绝不抛出 [ActivityNotFoundException]。
 *
 * ROM 适配里大量使用「先试私有页、再回退标准页」的策略，
 * 若第一次尝试直接抛异常会让整个设置流程中断，因此统一收口到这里。
 *
 * @param intent 待启动的 Intent，会自动补上 [Intent.FLAG_ACTIVITY_NEW_TASK]
 * @return true 表示启动成功
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
