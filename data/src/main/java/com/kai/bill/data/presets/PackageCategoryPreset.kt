package com.kai.bill.data.presets

import com.kai.bill.domain.model.BillType

/**
 * 「前台 App 包名 → 默认分类」预设。
 *
 * 这是 L2「判断当前软件名」那条路径的词表。它**优先级低于页面文本匹配**：
 * 页面文本是用户真实所见的界面（「美团外卖」），比 App 名更具体，
 * 因此只有当「通知正文」与「信号文本」都没命中分类词时才轮到它。
 *
 * 三条约定：
 * - **仅对支出方向生效**：这些 App 不会产生收入账单，方向判定为收入说明前面判错了，
 *   此时再配一个支出分类只会把错误固化下来；
 * - **一级与二级 id 混用是允许的**（与 [DefaultCategoryKeywords] 同一约定）：
 *   「美团 → 21 早午晚餐」比「美团 → 1 餐饮」更贴近实际场景；拿不准时宁可落到一级；
 * - **id 必须真实存在于 [DefaultCategories]**：写错会让账单落进一个语义不符的分类，
 *   因此配了单测断言全部映射合法。P1 与其它词表一样可迁入数据库热更新。
 */
object PackageCategoryPreset {

    /**
     * 键为包名，值为 [DefaultCategories] 的分类 id。
     *
     * 只收录「商户类 App」——支付类 App（支付宝 / 微信）在这里没有意义，
     * 它们本身不指示消费场景，且已被词表的保留词机制单独处理。
     */
    private val DEFAULTS: Map<String, Long> = mapOf(
        // —— 外卖 / 生鲜 → 餐饮(1) 下钻 ——
        "com.sankuai.meituan" to 21L,             // 早午晚餐
        "com.sankuai.meituan.takeoutnew" to 21L,
        "me.ele" to 21L,
        "com.dingdong.picmap" to 25L,             // 烹饪食材

        // —— 咖啡 / 茶饮 → 咖啡奶茶(24) ——
        "com.luckin.coffee" to 24L,
        "com.starbucks.cn" to 24L,

        // —— 出行 → 打车(27) ——
        "com.sdu.didi.psnger" to 27L,

        // —— 电商 → 购物(3) 一级（各家的子类差异大，不猜细） ——
        "com.taobao.taobao" to 3L,
        "com.jingdong.app.mall" to 3L,
        "com.xunmeng.pinduoduo" to 3L
    )

    /**
     * 查前台包名的默认分类。
     *
     * @param packageName 前台 App 包名；L3 截图来源恒为 null
     * @param direction 已定的账单方向；非支出方向一律返回 null（见类注释）
     * @return 分类 id；无映射返回 null
     */
    fun categoryIdOf(packageName: String?, direction: BillType): Long? =
        if (direction != BillType.EXPENSE) null else packageName?.let(DEFAULTS::get)

    /**
     * 全部映射到的分类 id。
     *
     * 仅供单测断言「映射的 id 必须真实存在」，业务代码请用 [categoryIdOf]。
     */
    internal val mappedCategoryIds: Collection<Long>
        get() = DEFAULTS.values
}
