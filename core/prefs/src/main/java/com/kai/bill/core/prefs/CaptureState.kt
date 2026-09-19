package com.kai.bill.core.prefs

/**
 * 采集诊断历史保留条数。
 *
 * 之所以要留够 50 条：通知采集是后台行为，用户往往是在「发现漏记」之后才回头查，
 * 那时现场早就过去若干条通知了；只留最近三两条会查不到当时那条。
 */
const val CAPTURE_DIAG_LIMIT = 50

/** 单条原文写入诊断前截断的字符数。通知正文很短，200 字足够还原现场，又不会把配置读写成负担。 */
const val CAPTURE_RAW_MAX_CHARS = 200

/**
 * 一条采集诊断记录 —— 「最近采集结果」列表里的一行。
 *
 * @property result 处理结果编码，取值见 [CaptureState.captureLastResult]
 * @property raw 原始通知文本（已截断 [CAPTURE_RAW_MAX_CHARS] 字）
 * @property atMillis 通知发布时间（`postTime`），不是写入诊断的时刻
 */
data class CaptureDiagEntry(
    val result: String,
    val raw: String,
    val atMillis: Long
)

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
 * @property accessibilityEnabled 无障碍服务是否**真的连上了**（由服务在 `onServiceConnected` / `onUnbind` 写入）。
 *           注意它与「系统设置里勾选了没有」是两件事：已勾选但此值为 false，
 *           说明服务被 ROM 杀掉或未成功绑定 —— 这是排查「为什么补分类不生效」时第一个要看的地方
 * @property captureLastSuccessAt 最近一次**收到**白名单通知的时间（无论有没有抽出金额）；
 *           它回答的是「监听服务还在正常工作吗」，与「这条通知是否被记账」是两件事，0 表示从未收到
 * @property captureEnabled 用户主开关：是否启用自动记账采集。开启后启动前台保活服务，降低监听被杀概率
 * @property captureLastResult 最近一条被处理的通知的结果编码：
 *           空串=未知；`SAVED`=已落库；`DUPLICATE`=重复跳过（键或时间窗判重）；
 *           `REPLAY`=同一条通知更新后重发，已在解析之前拦下；`PENDING`=已进待确认列表；
 *           `PENDING_DUP`=这笔已在待确认列表里，未重复入队；
 *           `NO_AMOUNT`=识别到通知但没抽到金额（旧版本写入的历史值，新版本不再写入）；
 *           `EXCLUDED`=失败/提醒/营销类，已忽略；`NO_RULE`=与账单语汇无关；`FILTERED`=来源不在白名单。
 *           除 `REPLAY` 外，其余都是**通过金额闸门之后**的结果；
 *           用于区分「采集成功」与「真正记账成功」——两者不一致即说明解析环节出问题。
 *           取值增删时必须同步 `PermissionCheckScreen` 的文案表，否则用户会看到对不上的提示。
 * @property captureLastRaw 最近一条被采集的原始通知文本（截断 [CAPTURE_RAW_MAX_CHARS] 字），
 *           配合 [captureLastResult] 用于排查规则未命中 / 金额未抽取等解析失败，便于在真机上对齐规则
 * @property captureRecent 最近采集诊断历史（新的在前，最多 [CAPTURE_DIAG_LIMIT] 条）。
 *           **只记通过金额闸门的通知**：抽不到金额的通知（账单提醒 / 活动推送 / 聊天）既不记账、
 *           也没有排查价值，全收进来只会把真正要看的记录顶掉。用户由此能回看「过去这一阵都采到了什么」
 * @property signalLastResult 最近一次**类别信号处理结果**编码，取值见 `ReconcileOutcome`
 *           （如 `ENRICHED` / `CREATED` / `NO_AMOUNT`）；空串 = 尚未发生过。
 *           它与 [captureLastResult] 是**两条并行的链路**：后者管「通知被怎么处理了」，
 *           它管「页面 / 截图识别出了什么」。分开放才能区分「通知没到」与「读到了但没用上」
 * @property signalLastText 最近一次信号所依据的文本（截断 [CAPTURE_RAW_MAX_CHARS] 字）。
 *           这是用户唯一能回答「为什么这笔分到了餐饮 / 为什么多了一笔账」的入口
 * @property signalRecent 最近**类别信号**处理历史（新的在前，最多 [CAPTURE_DIAG_LIMIT] 条）。
 *           与 [captureRecent] 同一套结构、同一个展示组件 —— 两条链路的诊断在引导页上
 *           **长得一样**，用户不必为「通知」和「页面识别」分别学一套看法。
 *           只收「产生了影响或定出了分类」的结果；`NO_AMOUNT` 这类高频噪声不入历史
 *           （理由与 [captureRecent] 不收无金额通知一样：否则真正要查的那条会被顶掉）
 * @property signalLastAtMillis 最近一次信号处理时刻（毫秒）
 * @property cardDelivery 最近一次**确认卡片投递**的结果。
 *           `DELIVERED` = 已显示在屏幕上；`NO_BILL` = 没有可展示的账单；
 *           其余为失败原因（抛出异常时的异常信息）。
 *           它存在的理由：卡片投递是**会静默失败**的环节 ——
 *           `WindowManager.addView` 抛异常时用户完全无感，
 *           系统也只在 logcat 里留一行。没有这个字段，就只能靠反复付真钱去猜。
 */
data class CaptureState(
    val smsLastScanMillis: Long = 0L,
    val captureServiceAlive: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val captureLastSuccessAt: Long = 0L,
    val captureEnabled: Boolean = false,
    val captureLastResult: String = "",
    val captureLastRaw: String = "",
    val captureRecent: List<CaptureDiagEntry> = emptyList(),
    val signalLastResult: String = "",
    val signalLastText: String = "",
    val signalLastAtMillis: Long = 0L,
    val signalRecent: List<CaptureDiagEntry> = emptyList(),
    val cardDelivery: String = ""
)
