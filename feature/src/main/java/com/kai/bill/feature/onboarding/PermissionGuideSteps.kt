package com.kai.bill.feature.onboarding

import com.kai.bill.core.common.device.Rom

/**
 * 权限引导步骤对应的动作类型，驱动 [PermissionCheckScreen] 发起具体跳转。
 */
enum class GuideAction {
    /** 跳转到系统「通知使用权」设置页 */
    LISTENER_SETTINGS,

    /** 跳转到系统「无障碍」设置页（分类识别 / 自动补记账） */
    ACCESSIBILITY_SETTINGS,

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
        // 排在「通知使用权」之后：它不影响能不能自动记账，只影响分类有多准，
        // 因此 essential = false —— 不开启时主链路照常工作，只是分类容易落到「其他」。
        add(
            GuideStep(
                id = "accessibility",
                title = "开启分类识别（无障碍）",
                description = "读取支付页面上的商户信息，把落进「其他」的账单自动改成餐饮 / 交通等分类；" +
                    "通知没来、但页面上有这笔消费时，也会自动补记一笔。",
                action = GuideAction.ACCESSIBILITY_SETTINGS,
                essential = false
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
                    description = buildString {
                        append("在厂商设置里把本 App 设为允许自启动、允许后台运行，避免通知监听被杀。")
                        // vivo 是唯一会把「灭屏长待」单独再清一遍的 ROM：光开自启动不够，
                        // 后台高耗电与最近任务锁定这两步不做，过夜之后大概率还是会被清掉
                        if (rom == Rom.VIVO) {
                            append("vivo 还需两步：① 系统设置 → 电池 → 后台高耗电 → 允许；")
                            append("② 从最近任务里下拉本应用卡片点「锁定」。")
                        }
                    },
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
