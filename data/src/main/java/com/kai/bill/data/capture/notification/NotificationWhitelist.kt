package com.kai.bill.data.capture.notification

/**
 * 通知采集白名单。
 *
 * 只处理与账单相关的来源，避免对系统 / 社交 / 工具类通知做无意义的抽取与解析：
 * - 支付宝（含测试版 RC）
 * - 微信（含微信支付通知）
 * - 招商银行等银行 App：快捷支付 / 动账通知
 * - 各厂商短信 App：短信正文会出现在通知里，由本渠道与 M5 短信规则共同覆盖
 *
 * 银行 App 此处先加入招商银行；其他银行按真机样本逐步扩展。
 */
object NotificationWhitelist {

    private val WATCHED = setOf(
        // 支付宝
        "com.eg.android.AlipayGphone",
        "com.eg.android.AlipayGphoneRC",
        // 微信
        "com.tencent.mm",
        // 银行 App（包名经应用商店核验：招行 cmb.pb / 工行 com.icbc / 建行 com.chinamworld.main；
        // 其余为公开常见包名，若个别机型不准请用 `adb shell pm list packages` 校正后告知）
        "cmb.pb",                       // 招商银行
        "com.icbc",                    // 工商银行
        "com.chinamworld.main",        // 建设银行
        "com.bankofchina",             // 中国银行
        "com.bankcomm",                // 交通银行
        "com.android.bankabc",         // 农业银行
        "com.yitong.mbank",            // 邮储银行
        "com.psbc",                    // 邮储银行（候选）
        "com.citicbank.perbank",       // 中信银行
        "com.spdbccc.app",             // 浦发银行
        "com.cmbc.cc.mbank",           // 民生银行
        "com.cib",                     // 兴业银行
        "com.cebbank.mobile.cemb",     // 光大银行
        "com.pingan.standardcharteredbank", // 平安银行
        "com.cgbchina.mobilebank",     // 广发银行
        "com.hxb.mobile",              // 华夏银行
        "com.bankofbeijing",           // 北京银行
        "com.nbcb",                    // 宁波银行
        // 短信 App（跨厂商）
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.miui.mms",
        "com.huawei.android.mms",
        "com.coloros.mms",
        "com.heytap.mms",
        "com.oppo.mms",
        "com.vivo.mms",
        "com.hihonor.mms"
    )

    fun isWatched(packageName: String): Boolean = WATCHED.contains(packageName)
}
