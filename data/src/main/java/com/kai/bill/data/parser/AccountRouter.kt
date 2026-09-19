package com.kai.bill.data.parser

import com.kai.bill.domain.model.SourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4 账户路由：按**通知来源 App** 落到对应账户。
 *
 * 为什么用包名而不是正文关键词：正文里出现「支付宝」可能只是银行通知描述对手方
 * （`您尾号1234在【财付通-微信支付-微信转账】发生快捷支付扣款`），按正文判断会把
 * 银行卡的支出记到支付宝账户上。包名是采集链路上唯一的「事实来源」。
 *
 * 返回 null 是合法结果：账户在领域模型里可空（未指定账户的流水），
 * 猜错账户比不指定账户更难排查。
 */
@Singleton
class AccountRouter @Inject constructor() {

    /**
     * @param packageName 通知来源包名；未知或为 null 时返回 null
     * @param source 采集来源，预留：短信来源无法从包名区分账户，M5 起再按正文补充
     */
    fun resolve(packageName: String?, source: SourceType): Long? {
        val pkg = packageName ?: return null
        return when (pkg) {
            in ALIPAY_PACKAGES -> ACCOUNT_ALIPAY
            in WECHAT_PACKAGES -> ACCOUNT_WECHAT
            in BANK_PACKAGES -> ACCOUNT_BANK_CARD
            else -> null
        }
    }

    private companion object {

        /** 账户 id 约定见 DefaultAccounts：2 支付宝 / 3 微信 / 4 储蓄卡 */
        const val ACCOUNT_ALIPAY = 2L
        const val ACCOUNT_WECHAT = 3L
        const val ACCOUNT_BANK_CARD = 4L

        val ALIPAY_PACKAGES = setOf("com.eg.android.AlipayGphone", "com.eg.android.AlipayGphoneRC")

        val WECHAT_PACKAGES = setOf("com.tencent.mm")

        /**
         * 银行 App 包名。
         *
         * 与 `NotificationWhitelist` 的分工：那里决定「要不要看这条通知」（采集闸门），
         * 这里决定「看上了算哪个账户」（归集）。只有白名单内的包名才会走到这里，
         * 因此本集合只需覆盖已放行的银行 App；新增银行时两处都要补。
         */
        val BANK_PACKAGES = setOf(
            "cmb.pb",                       // 招商银行
            "com.icbc",                     // 工商银行
            "com.chinamworld.main",         // 建设银行
            "com.bankofchina",              // 中国银行
            "com.bankcomm",                 // 交通银行
            "com.android.bankabc",          // 农业银行
            "com.yitong.mbank",             // 邮储银行
            "com.psbc",
            "com.citicbank.perbank",        // 中信银行
            "com.spdbccc.app",              // 浦发银行
            "com.cmbc.cc.mbank",            // 民生银行
            "com.cib",                      // 兴业银行
            "com.cebbank.mobile.cemb",      // 光大银行
            "com.pingan.standardcharteredbank", // 平安银行
            "com.cgbchina.mobilebank",      // 广发银行
            "com.hxb.mobile",               // 华夏银行
            "com.bankofbeijing",            // 北京银行
            "com.nbcb"                      // 宁波银行
        )
    }
}
