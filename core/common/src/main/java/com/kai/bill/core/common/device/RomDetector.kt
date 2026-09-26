package com.kai.bill.core.common.device

import android.os.Build

/**
 * 手机厂商 ROM 类型。
 *
 * 国内 ROM 对后台、自启动、通知使用权的管控入口各不相同，
 * 权限引导页需要据此跳转到正确的设置页，否则用户根本找不到开关。
 */
enum class Rom {
    /** vivo / OriginOS / Funtouch OS —— 本项目主力机 */
    VIVO,

    /** 华为 EMUI / HarmonyOS（仍兼容 APK 的版本）*/
    HUAWEI,

    /** 荣耀 MagicOS（自华为独立后的分支）*/
    HONOR,

    XIAOMI,
    OPPO,
    ONEPLUS,
    SAMSUNG,
    GOOGLE,

    /** 未能识别，按 AOSP 标准行为处理 */
    UNKNOWN
}

/**
 * ROM 识别器。
 *
 * 只在权限引导页调用（低频），因此不做缓存，
 * 避免静态可变状态带来的线程安全问题。
 */
object RomDetector {

    /**
     * 识别当前设备的 ROM 类型。
     *
     * @return 识别结果；无法匹配时返回 [Rom.UNKNOWN]
     */
    fun current(): Rom {
        // NOTE: 同时比对 MANUFACTURER 与 BRAND —— 部分机型两者不一致（如荣耀可能报 HONOR 或 HUAWEI）
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()

        return when {
            manufacturer.contains("vivo") || brand.contains("vivo") -> Rom.VIVO
            manufacturer.contains("honor") || brand.contains("honor") -> Rom.HONOR
            manufacturer.contains("huawei") || brand.contains("huawei") -> Rom.HUAWEI
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") -> Rom.XIAOMI
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> Rom.ONEPLUS
            // WHY: realme 是 OPPO 子品牌，沿用 ColorOS 的自启动 / 后台管理入口
            manufacturer.contains("oppo") || brand.contains("oppo") || brand.contains("realme") -> Rom.OPPO
            manufacturer.contains("samsung") -> Rom.SAMSUNG
            manufacturer.contains("google") -> Rom.GOOGLE
            else -> Rom.UNKNOWN
        }
    }

    /**
     * 是否为国内深度定制 ROM（后台管控严格，需要额外引导用户加白名单）。
     *
     * @return true 表示需要走「自启动 + 电池优化 + 后台冻结」的完整引导流程
     */
    fun isAggressiveRom(): Boolean =
        current() in setOf(Rom.VIVO, Rom.HUAWEI, Rom.HONOR, Rom.XIAOMI, Rom.OPPO, Rom.ONEPLUS)
}
