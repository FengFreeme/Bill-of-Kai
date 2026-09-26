package com.kai.bill.navigation

/**
 * 应用内路由常量。
 *
 * 集中管理而非散落字符串字面量：导航调用与 NavHost 注册必须对齐，漏改一个只在运行时才暴露「页面跳不过去」。
 * 底部 3 Tab（首页 / 统计 / 设置）是顶层目的地，记一笔、预算、外观等均为二级页。
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
    /**
     * 分类管理。`parentId` 是可选的：带上时进入页面会定位到该一级分类，并直接弹「在它下面新增」
     * （记一笔二级网格末尾的「＋」就是带着所属大类进去的，否则新增会落到列表末尾成一级分类）。
     *
     * 注册路由用本常量（含 `{parentId}` 占位），**导航一律走 [categoryManage]** ——
     * 直接把常量交给 `navigate()` 会把 `{parentId}` 当字面量塞给 LongType。
     */
    const val CATEGORY_MANAGE = "category_manage?parentId={parentId}"

    /** 打开分类管理的路由；[parentId] 为 null 表示普通进入（不定位、不自动弹新增框） */
    fun categoryManage(parentId: Long? = null): String =
        if (parentId == null) "category_manage" else "category_manage?parentId=$parentId"

    const val ACCOUNT_MANAGE = "account_manage"
    const val PARSE_RULE = "parse_rule"
    const val NEEDS_REVIEW = "needs_review"
    const val BACKUP = "backup"

    /** 需要显示底栏的 Tab 根路由 */
    val tabRoots: Set<String> = setOf(HOME, STATS, SETTINGS)
}
