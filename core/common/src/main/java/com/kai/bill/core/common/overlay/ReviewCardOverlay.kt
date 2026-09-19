package com.kai.bill.core.common.overlay

/**
 * 确认卡片的悬浮层宿主 —— 「识别到账单后，把卡片盖在当前页面上」这件事的平台能力抽象。
 *
 * **为什么放在 core:common**：它需要被三方共用 ——
 * `data`（自动流水线触发）、`feature`（引导页的「测试确认卡片」按钮）、
 * `app`（唯一实现，因为只有 app 同时看得见 `feature` 的 Compose UI 与 `domain` 的仓储）。
 * 放在任一侧都会形成反向依赖，而它本身又只是一个平台能力（窗口叠加），
 * 与 [com.kai.bill.core.common.ext] 里那些系统能力扩展是同一性质。
 *
 * ## 实现约定的关键约束
 *
 * 实现**必须**用 `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` 添加窗口：
 *
 * - 它是**唯一不需要任何权限**就能盖在别的 App 之上的通道，只要本应用的无障碍服务在运行；
 * - 这不是绕过限制的野路子，而是系统为该场景设计的正式窗口类型
 *   （商业产品同款：小黑记账的「自动记账面板」正是这样显示，其悬浮窗权限处于 denied 状态）；
 * - 走「后台 startActivity + 半透明 Activity」是**行不通**的：Android 10 起的后台启动 Activity
 *   限制共有 8 条官方豁免，`SYSTEM_ALERT_WINDOW` 在列，而「无障碍服务」**不在**，
 *   且系统只是**静默拦截**（不抛异常），代码里看不出任何失败 —— 本项目踩过这个坑。
 *
 * 因此：**不要再退回到启动 Activity 的方案**，也不要为此申请悬浮窗权限。
 */

/**
 * 卡片要解释的**来由**：这笔是刚记下的，还是本来就有、只是把分类补全了。
 *
 * 两者的用户预期完全不同：
 * - 「新建」需要用户当场确认分类对不对（钱是新记进去的）；
 * - 「补分类」则要说明「这笔我早就记过了，系统只改了分类」——
 *   否则用户看到卡片会以为程序又记了一笔（同一笔钱被记两次是记账 App 最严重的错觉）。
 */
enum class ReviewCardReason {

    /** 由这次识别**新建**的一笔（`ReconcileOutcome.CREATED`） */
    CREATED,

    /** 已有账单，这次只是**补上了分类**（`ReconcileOutcome.ENRICHED`） */
    ENRICHED
}

interface ReviewCardOverlay {

    /**
     * 把指定账单的确认卡片显示为悬浮层。
     *
     * 若已有卡片在显示，实现应先移除旧的再显示新的（同一时刻只允许一张）。
     *
     * @param billId 账单主键
     * @param reason 这笔的来由（新建 / 补分类），决定卡片文案
     * @return true 表示卡片已显示在屏幕上
     */
    suspend fun show(billId: Long, reason: ReviewCardReason = ReviewCardReason.CREATED): Boolean

    /**
     * 用**最近一笔账单**显示卡片，供引导页的「测试确认卡片」按钮使用 ——
     * 有了它，验证卡片是否工作就不必真的去付一笔钱。
     *
     * @return true 表示卡片已显示；false 表示一笔账单都没有
     */
    suspend fun showLatest(): Boolean

    /** 移除悬浮层；未显示时是空操作（幂等） */
    fun dismiss()
}
