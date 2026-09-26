package com.kai.bill.data.capture.accessibility

/**
 * 尚未单独建模的消费 App「账单详情页」判定 —— 纯函数。
 *
 * 支付宝 / 微信已有版式专属规则；美团、京东等原生页仍走本套：
 * 命中至少一个详情字段，且不含聊天 / 主界面 / 账户资产否定词。
 */
object GenericBillPageHeuristics {

    /**
     * 账单详情页专有字段：聊天 / 主界面 / 账单列表 / 账户页都不会出现这些词。
     *
     * 刻意**不收**「支付 / 账单 / 金额」这类泛滥词 —— 它们到处都是，
     * 早期按这类词收录时把聊天内容、账户余额都当成了交易。
     */
    private val BILL_DETAIL_MARKERS = listOf(
        "支付时间", "交易时间", "转账时间",
        "交易单号", "商户单号", "转账单号",
        "商户全称", "收单机构", "商户名称", "收款方备注",
        "账单服务", "在此商户的交易", "对订单有疑惑"
    )

    /**
     * 「不是账单详情页」的特征词：出现即否决。
     *
     * - 聊天 / 主界面：浮窗、通讯录、聊天信息、正在加载、视频号…；
     * - 账户 / 资产页：总金额、累计收益、统计明细…（防把余额当交易）。
     */
    private val NON_BILL_MARKERS = listOf(
        "浮窗", "通讯录", "切换到发消息", "聊天信息", "文件传输助手", "正在加载", "视频号",
        "总金额", "定期金额", "累计收益", "邀请他人加入", "查看权益", "月支出", "月收入", "统计明细"
    )

    /**
     * @param text 已归一化为单行的页面文本（见 [PageTextExtractor]）
     * @return true 表示可以按「某一笔交易的详情」来处理
     */
    fun isBillDetail(text: String): Boolean =
        NON_BILL_MARKERS.none { text.contains(it) } && BILL_DETAIL_MARKERS.any { text.contains(it) }
}
