package com.kai.bill.core.design.theme

/**
 * 深色模式策略。
 *
 * 注意：深色模式在本项目中是**独立调色**而非对浅色取反 ——
 * 功能色需提亮；页面背景、卡片与底栏均为纯色分层。
 */
enum class DarkMode {

    /** 跟随系统设置（默认） */
    FOLLOW_SYSTEM,

    /** 强制浅色 */
    LIGHT,

    /** 强制深色 */
    DARK;

    /** 选择器与文案展示用的中文名 */
    val label: String
        get() = when (this) {
            FOLLOW_SYSTEM -> "跟随系统"
            LIGHT -> "浅色"
            DARK -> "深色"
        }
}
