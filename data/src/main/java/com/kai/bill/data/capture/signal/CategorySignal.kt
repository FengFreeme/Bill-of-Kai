package com.kai.bill.data.capture.signal

/**
 * 类别信号来源。
 *
 * **声明顺序即可信度顺序**（越靠前越可信）：无障碍读到的是用户当下真实所见的界面，
 * 比识别截图得到的文本更少出错。
 *
 * ⚠️ [CategorySignalStore.recent] 按 `ordinal` **升序**排序，因此**调整本枚举顺序会
 * 直接改变分类命中结果**。新增来源时请把更可信的那一类放在前面。
 */
enum class CategorySignalOrigin {

    /** L2：无障碍读取到的当前页面可见文本 */
    ACCESSIBILITY,

    /** L3：截图 OCR 得到的文本 */
    SCREENSHOT
}

/**
 * 一条类别线索 —— L2 / L3 的产出物。
 *
 * 它是「候选账单 + 候选分类」的合体：既可能用于给既有账单**补分类**，
 * 也可能在找不到可匹配账单时**独立成账**（见 `SignalReconciler`）。
 * 但它本身不落库 —— 建账一律复用 `IngestPipeline`，
 * 以保证金额、方向、去重口径与 L1 完全一致。
 *
 * 隐私约定：本对象**只在内存中短时存活**（见 [CategorySignalStore]），不落盘、不写日志。
 *
 * @property origin 信号来源，用于排序、诊断，以及建账时的账单来源
 * @property packageName 采集信号时前台的 App 包名；用于「包名 → 默认分类」，
 *           L2 建账时也用于账户路由；L3 无法得知来源，恒为 null
 * @property text 已清洗为单行的页面 / OCR 文本
 * @property amountCents 抽到的金额（分）；抽不到时为 null。
 *           用途有二：① 回填时筛选候选账单；② 建账时作为金额
 * @property tradeTimeMillis 从文本中抽到的**交易时间**；抽不到时为 null
 * @property capturedAtMillis 信号采集时刻（毫秒），是 [tradeTimeMillis] 的兜底
 */
data class CategorySignal(

    val origin: CategorySignalOrigin,

    val packageName: String?,

    val text: String,

    val amountCents: Long?,

    val tradeTimeMillis: Long?,

    val capturedAtMillis: Long
) {

    /**
     * 有效交易时间：优先用文本里抽到的交易时间，抽不到才退回采集时刻。
     *
     * 为什么重要：L3 截图可能在交易发生后几分钟才产生，若一律以截图时刻当交易时间，
     * 账单会记在错误的时点（甚至跨天、跨月），也会让「与既有账单匹配」失效。
     */
    val effectiveTimeMillis: Long
        get() = tradeTimeMillis ?: capturedAtMillis
}
