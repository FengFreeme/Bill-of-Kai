package com.kai.bill.core.design.theme

/**
 * 可选的主题配色。
 *
 * 三套配色共用同一套「功能色」（支出红 / 收入绿 / 中性灰）——
 * 换主题后「红色仍然是支出」不能变，否则会破坏用户对颜色的直觉。
 * 各套之间变化的是**主色与页面背景色**。
 */
enum class AppPalette {

    /** 青草绿：偏草绿清新，减压自然（默认） */
    MINT,

    /** 天空蓝：冷静理性，偏工具感 */
    SKY,

    /** 淡紫樱花：柔和年轻，偏暖 */
    LILAC;

    /** 选择器与文案展示用的中文名 */
    val label: String
        get() = when (this) {
            MINT -> "青草绿"
            SKY -> "天空蓝"
            LILAC -> "淡紫樱花"
        }
}
