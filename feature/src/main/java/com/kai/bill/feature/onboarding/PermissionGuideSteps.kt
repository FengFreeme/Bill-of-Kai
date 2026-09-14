package com.kai.bill.feature.onboarding

import com.kai.bill.core.common.device.Rom

/**
 * 权限引导步骤对应的动作类型，驱动 [PermissionCheckScreen] 发起具体跳转。
 */
enum class GuideAction {
    /** 跳转到系统「通知使用权」设置页 */
    LISTENER_SETTINGS,

    /** 申请加入电池优化白名单（AOSP 标准页，所有机型通用） */
    BATTERY_OPTIMIZATION,

    /** 跳转到厂商「自启动 / 后台耗电」管理页 */
    AUTO_START,

    /** 打开本 App 应用详情页（万能兜底入口） */
    APP_DETAILS
}

/**
 * 单条引导步骤。
 *
 * @param id 稳定标识，作为 Compose 列表 key
 * @param title 步骤标题
 * @param description 引导说明（含「为什么需要」）
 * @param action 点击触发的具体动作
 * @param essential 是否必达项：true 表示不完成则通知采集不可用
 */
data class GuideStep(
    val id: String,
    val title: String,
    val description: String,
    val action: GuideAction,
    val essential: Boolean
)

/**
 * 分厂商引导步骤。
 *
 * 监听权限是所有机型必达项；电池优化 / 自启动引导对国内深度定制 ROM 尤为关键，
 * 原生 / 三星等管控宽松的 ROM 可省略自启动步骤。
 */
object PermissionGuideSteps {

    fun forRom(rom: Rom): List<GuideStep> = buildList {
        add(
            GuideStep(
                id = "listener",
                title = "开启通知使用权",
                description = "允许本 App 读取通知栏内容，才能从支付宝 / 微信扣款通知自动记账。",
                action = GuideAction.LISTENER_SETTINGS,
                essential = true
            )
        )
        add(
            GuideStep(
                id = "battery",
                title = "加入电池优化白名单",
                description = "关闭电池优化可显著降低后台被系统回收、导致漏记的概率。",
                action = GuideAction.BATTERY_OPTIMIZATION,
                essential = false
            )
        )
        if (rom in setOf(Rom.VIVO, Rom.HUAWEI, Rom.HONOR, Rom.XIAOMI, Rom.OPPO, Rom.ONEPLUS)) {
            add(
                GuideStep(
                    id = "autostart",
                    title = "允许自启动 / 后台运行",
                    description = "在厂商设置里把本 App 设为允许自启动、允许后台运行，避免通知监听被杀。",
                    action = GuideAction.AUTO_START,
                    essential = false
                )
            )
        }
        add(
            GuideStep(
                id = "details",
                title = "其他权限（应用详情）",
                description = "如需手动授予短信等权限，可在此统一入口处理（短信解析属后续版本）。",
                action = GuideAction.APP_DETAILS,
                essential = false
            )
        )
    }
}
