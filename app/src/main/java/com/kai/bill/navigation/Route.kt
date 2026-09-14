package com.kai.bill.navigation

/**
 * 应用内路由常量。
 *
 * 集中管理而非散落字符串字面量：导航调用与 NavHost 注册必须在同一处对齐，
 * 漏改一个就会在运行时才暴露「页面跳不过去」。
 *
 * 底部 **3 Tab**（首页 / 统计 / 设置）是顶层目的地；
 * 记一笔、预算、外观等均为二级页，不在底栏。
 */
object Route {
    // ---- Tab 根 ----
    const val HOME = "home"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    // ---- 二级页 ----
    const val RECORD = "record"
    const val RECORD_EDIT = "record/{billId}"

    /** 编辑指定账单的路由 */
    fun recordEdit(billId: Long): String = "record/$billId"

    const val CATEGORY_DETAIL = "category_detail/{categoryId}"

    /** 查看指定分类详情的路由（从统计页的分类排行进入） */
    fun categoryDetail(categoryId: Long): String = "category_detail/$categoryId"
    const val APPEARANCE = "appearance"
    const val BUDGET = "budget"
    const val ONBOARDING = "onboarding"
    const val CATEGORY_MANAGE = "category_manage"
    const val ACCOUNT_MANAGE = "account_manage"
    const val PARSE_RULE = "parse_rule"
    const val NEEDS_REVIEW = "needs_review"

    /** 需要显示底栏的 Tab 根路由 */
    val tabRoots: Set<String> = setOf(HOME, STATS, SETTINGS)
}
