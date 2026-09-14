package com.kai.bill.core.prefs

/**
 * 账单采集链路的运行状态水位线。
 *
 * 存在的意义：vivo 等国内 ROM 会杀死后台服务，通知监听一旦被杀就会**静默丢数据**。
 * 把状态落到 DataStore 后，App 下次启动可以对比水位线判断「是否被杀过」，
 * 进而在首页给出「有账单可能漏采，去补扫」的提示，而不是让用户毫无察觉。
 *
 * @property smsLastScanMillis 短信兜底扫描水位线（毫秒）。
 *           短信落盘、随时可补，因此只要记录「上次扫到哪」就能做到不重不漏
 * @property captureServiceAlive 前台保活服务的存活标记；被杀后此值为 true 且服务未重启即为异常
 * @property notificationListenerEnabled 通知使用权是否已授予；未授予则通知采集完全不可用
 * @property captureLastSuccessAt 最近一次成功采集（通知/短信）时间；用于首页「漏采」状态提示，0 表示从未成功
 * @property captureEnabled 用户主开关：是否启用自动记账采集。开启后启动前台保活服务，降低监听被杀概率
 * @property captureLastResult 最近一条通知经 [com.kai.bill.data.ingest.BillIngestor] 的处理结果编码：
 *           空串=未知；`SAVED`=已落库；`DUPLICATE`=重复跳过；`NO_AMOUNT`=命中规则但没抽到金额；
 *           `NO_RULE`=无规则命中。用于区分「采集成功」与「真正记账成功」——两者不一致即说明解析环节出问题。
 * @property captureLastRaw 最近一条被采集的原始通知文本（截断 200 字），配合 [captureLastResult] 用于排查
 *           规则未命中 / 金额未抽取等解析失败，便于在真机上对齐规则。
 */
data class CaptureState(
    val smsLastScanMillis: Long = 0L,
    val captureServiceAlive: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val captureLastSuccessAt: Long = 0L,
    val captureEnabled: Boolean = false,
    val captureLastResult: String = "",
    val captureLastRaw: String = ""
)
