package com.kai.bill.data.presets

import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.RefundCategory

/**
 * 预置分类（支出 / 收入 / 转账）。
 *
 * 约定（见 `core/db/dao/CategoryDao` 写入契约）：
 * - 必须**显式指定固定 id**（1..N），否则自增主键会让重复启动长出多份同名分类
 * - `isSystem = true`，系统预置分类不允许删除，避免「记一笔」出现空分类面板
 * - 底层 [com.kai.bill.domain.repository.CategoryRepository.insertAll] 用
 *   `OnConflictStrategy.IGNORE`，重复播种天然幂等，不会写重
 *
 * 用户自建分类从 N+1 开始自增，不会与预置撞号。
 */
object DefaultCategories {

    private val expense: List<Category> = listOf(
        Category(1, "餐饮", "restaurant", "#F2775F", BillType.EXPENSE, null, 1, true),
        Category(2, "交通", "car", "#4A90D8", BillType.EXPENSE, null, 2, true),
        Category(3, "购物", "cart", "#E0809A", BillType.EXPENSE, null, 3, true),
        Category(4, "居住", "home", "#C79288", BillType.EXPENSE, null, 4, true),
        Category(5, "通讯", "call", "#5BB5A8", BillType.EXPENSE, null, 5, true),
        Category(6, "娱乐", "game-controller", "#A97BD8", BillType.EXPENSE, null, 6, true),
        Category(7, "医疗", "medkit", "#F2A65A", BillType.EXPENSE, null, 7, true),
        Category(8, "教育", "school", "#6FA8D8", BillType.EXPENSE, null, 8, true),
        Category(9, "旅行", "airplane", "#4DB6AC", BillType.EXPENSE, null, 9, true),
        Category(10, "宠物", "paw", "#D98CA0", BillType.EXPENSE, null, 10, true),
        Category(11, "服饰", "shirt", "#E091B5", BillType.EXPENSE, null, 11, true),
        Category(12, "其他", "grid", "#9AA5B1", BillType.EXPENSE, null, 12, true)
    )

    private val income: List<Category> = listOf(
        Category(13, "工资", "wallet", "#3FC79A", BillType.INCOME, null, 1, true),
        Category(14, "奖金", "gift", "#5FDDB0", BillType.INCOME, null, 2, true),
        Category(15, "理财", "pie-chart", "#4A90D8", BillType.INCOME, null, 3, true),
        Category(16, "兼职", "briefcase", "#6FB1A8", BillType.INCOME, null, 4, true),
        Category(17, "红包", "cash", "#F2775F", BillType.INCOME, null, 5, true),
        Category(18, "其他收入", "add", "#9AA5B1", BillType.INCOME, null, 6, true)
    )

    /**
     * 转账（一级）与还款。
     *
     * 转账有**两个方向，且必须区分**（2026-09-20 真机样本）：
     * - **转出**：钱从我这出去 —— 微信「`周建鑫已收款 ¥200.00`」（付款方视角，对方收了）；
     * - **转入**：钱进我这来 —— 微信「`你已收款，资金已存入零钱 ¥100.00`」。
     *
     * 两者的页面文案**都带「收款」二字**，只按动作词判会把「转出」错记成一笔收入。
     * 方向在 `DefaultMatchKeywords` 的转账组里单独收敛，这里只声明落到哪个分类；
     * 转入 / 转出是**转账的下钻项**（见 [subTransfer]），不是与它并列的一级分类。
     *
     * 19 转账保留为**泛化项**：只认出「这是笔转账」但判不出方向时用它兜底 ——
     * 历史账单引用的就是它，语义不能改（改了老账目会掉进「未分类」）。
     */
    private val transfer: List<Category> = listOf(
        Category(19, "转账", "swap-horizontal", "#7C8894", BillType.TRANSFER, null, 1, true),
        Category(20, "还款", "refresh-circle", "#849994", BillType.TRANSFER, null, 2, true)
    )

    /**
     * 二级分类（转账）：挂在「19 转账」下。
     *
     * 为什么转入 / 转出是**子分类**而不是并列的一级：它们只是同一件事的两个方向，
     * 选中它们时的语义仍是「转账（不计收支）」；做成一级会让转账从一个概念变成三个，
     * 统计与筛选也会多出两个本不存在的维度。
     *
     * 与「20 还款」的分工：还款（还信用卡 / 花呗）是独立场景，仍留在一级。
     */
    private val subTransfer: List<Category> = listOf(
        Category(96, "转出", "arrow-up", "#7C8894", BillType.TRANSFER, 19, 1, true),
        Category(97, "转入", "arrow-down", "#7C8894", BillType.TRANSFER, 19, 2, true)
    )

    /**
     * 二级分类（支出）。
     *
     * 三条硬约定：
     * - **id 从 21 起**：1..20 已被历史账单引用，改动它们会让老账目掉进「未分类」兜底，
     *   因此子分类只做纯增量。`parentId` 列与 `index_category_parentId` 在 v1 schema 就已备好，
     *   新增子分类**不改表、不 bump 数据库版本、不需要 Migration**。
     * - **icon 与一级同体系**：全部走 Ionicons key（资源自托管在
     *   `feature/src/main/res/drawable`，映射见 `feature/.../record/components/CategoryIcon.kt`），
     *   不用 emoji，保证两级分类视觉一致。
     * - **colorHex 继承父分类**：二级配色随一级走，下钻饼图色系连贯。
     *
     * 每个「// 一级分类(id)」注释标明该组子分类挂在哪个父分类下。
     */
    private val subExpense: List<Category> = listOf(
        // 餐饮(1)
        Category(21, "早午晚餐", "restaurant", "#F2775F", BillType.EXPENSE, 1, 1, true),
        Category(22, "烟酒茶叶", "wine", "#F2775F", BillType.EXPENSE, 1, 2, true),
        Category(23, "水果零食", "nutrition", "#F2775F", BillType.EXPENSE, 1, 3, true),
        Category(24, "咖啡奶茶", "cafe", "#F2775F", BillType.EXPENSE, 1, 4, true),
        Category(25, "烹饪食材", "basket", "#F2775F", BillType.EXPENSE, 1, 5, true),
        // 交通(2)
        Category(26, "公共交通", "bus", "#4A90D8", BillType.EXPENSE, 2, 1, true),
        Category(27, "打车", "car-sport", "#4A90D8", BillType.EXPENSE, 2, 2, true),
        Category(28, "加油", "flame", "#4A90D8", BillType.EXPENSE, 2, 3, true),
        Category(29, "停车费", "pin", "#4A90D8", BillType.EXPENSE, 2, 4, true),
        Category(30, "维修保养", "construct", "#4A90D8", BillType.EXPENSE, 2, 5, true),
        // 购物(3)
        Category(31, "日用百货", "cart", "#E0809A", BillType.EXPENSE, 3, 1, true),
        Category(32, "数码家电", "laptop", "#E0809A", BillType.EXPENSE, 3, 2, true),
        Category(33, "美妆护肤", "color-palette", "#E0809A", BillType.EXPENSE, 3, 3, true),
        Category(34, "母婴用品", "happy", "#E0809A", BillType.EXPENSE, 3, 4, true),
        Category(35, "家居家装", "bed", "#E0809A", BillType.EXPENSE, 3, 5, true),
        // 居住(4)
        Category(36, "房租", "home", "#C79288", BillType.EXPENSE, 4, 1, true),
        Category(37, "房贷", "business", "#C79288", BillType.EXPENSE, 4, 2, true),
        Category(38, "水电燃气", "water", "#C79288", BillType.EXPENSE, 4, 3, true),
        Category(39, "物业费", "key", "#C79288", BillType.EXPENSE, 4, 4, true),
        Category(40, "装修维修", "hammer", "#C79288", BillType.EXPENSE, 4, 5, true),
        // 通讯(5)
        Category(41, "手机费", "call", "#5BB5A8", BillType.EXPENSE, 5, 1, true),
        Category(42, "宽带", "wifi", "#5BB5A8", BillType.EXPENSE, 5, 2, true),
        Category(43, "会员订阅", "star", "#5BB5A8", BillType.EXPENSE, 5, 3, true),
        Category(44, "快递邮寄", "cube", "#5BB5A8", BillType.EXPENSE, 5, 4, true),
        // 娱乐(6)
        Category(45, "电影演出", "film", "#A97BD8", BillType.EXPENSE, 6, 1, true),
        Category(46, "游戏充值", "game-controller", "#A97BD8", BillType.EXPENSE, 6, 2, true),
        Category(47, "运动健身", "fitness", "#A97BD8", BillType.EXPENSE, 6, 3, true),
        Category(48, "酒吧KTV", "musical-notes", "#A97BD8", BillType.EXPENSE, 6, 4, true),
        Category(49, "旅游度假", "airplane", "#A97BD8", BillType.EXPENSE, 6, 5, true),
        // 医疗(7)
        Category(50, "药品", "medkit", "#F2A65A", BillType.EXPENSE, 7, 1, true),
        Category(51, "门诊", "pulse", "#F2A65A", BillType.EXPENSE, 7, 2, true),
        Category(52, "住院", "medical", "#F2A65A", BillType.EXPENSE, 7, 3, true),
        Category(53, "体检", "heart", "#F2A65A", BillType.EXPENSE, 7, 4, true),
        Category(54, "医疗保险", "shield", "#F2A65A", BillType.EXPENSE, 7, 5, true),
        // 教育(8)
        Category(55, "学费", "school", "#6FA8D8", BillType.EXPENSE, 8, 1, true),
        Category(56, "书籍", "book", "#6FA8D8", BillType.EXPENSE, 8, 2, true),
        Category(57, "课程培训", "bulb", "#6FA8D8", BillType.EXPENSE, 8, 3, true),
        Category(58, "考试报名", "document-text", "#6FA8D8", BillType.EXPENSE, 8, 4, true),
        // 旅行(9)
        Category(59, "旅途交通", "train", "#4DB6AC", BillType.EXPENSE, 9, 1, true),
        Category(60, "住宿", "bed", "#4DB6AC", BillType.EXPENSE, 9, 2, true),
        Category(61, "门票", "ticket", "#4DB6AC", BillType.EXPENSE, 9, 3, true),
        Category(62, "旅行购物", "bag", "#4DB6AC", BillType.EXPENSE, 9, 4, true),
        // 宠物(10)
        Category(63, "宠物食品", "paw", "#D98CA0", BillType.EXPENSE, 10, 1, true),
        Category(64, "宠物医疗", "medical", "#D98CA0", BillType.EXPENSE, 10, 2, true),
        Category(65, "宠物用品", "basket", "#D98CA0", BillType.EXPENSE, 10, 3, true),
        Category(66, "宠物服务", "cut", "#D98CA0", BillType.EXPENSE, 10, 4, true),
        // 服饰(11)
        Category(67, "上衣", "shirt", "#E091B5", BillType.EXPENSE, 11, 1, true),
        Category(68, "裤装", "layers", "#E091B5", BillType.EXPENSE, 11, 2, true),
        Category(69, "鞋靴", "walk", "#E091B5", BillType.EXPENSE, 11, 3, true),
        Category(70, "包包", "bag", "#E091B5", BillType.EXPENSE, 11, 4, true),
        Category(71, "饰品", "diamond", "#E091B5", BillType.EXPENSE, 11, 5, true),
        // 其他(12)
        Category(72, "礼金", "gift", "#9AA5B1", BillType.EXPENSE, 12, 1, true),
        Category(73, "公益捐赠", "heart", "#9AA5B1", BillType.EXPENSE, 12, 2, true),
        Category(74, "罚款", "warning", "#9AA5B1", BillType.EXPENSE, 12, 3, true),
        Category(75, "其他支出", "grid", "#9AA5B1", BillType.EXPENSE, 12, 4, true)
    )

    /** 二级分类（收入）。id / icon / 配色约定同 [subExpense]。 */
    private val subIncome: List<Category> = listOf(
        // 工资(13)
        Category(76, "基本工资", "wallet", "#3FC79A", BillType.INCOME, 13, 1, true),
        Category(77, "绩效", "trending-up", "#3FC79A", BillType.INCOME, 13, 2, true),
        Category(78, "补贴", "add-circle", "#3FC79A", BillType.INCOME, 13, 3, true),
        Category(79, "加班费", "time", "#3FC79A", BillType.INCOME, 13, 4, true),
        // 奖金(14)
        Category(80, "年终奖", "gift", "#5FDDB0", BillType.INCOME, 14, 1, true),
        Category(81, "项目奖金", "trophy", "#5FDDB0", BillType.INCOME, 14, 2, true),
        Category(82, "提成", "stats-chart", "#5FDDB0", BillType.INCOME, 14, 3, true),
        // 理财(15)
        Category(83, "利息", "pie-chart", "#4A90D8", BillType.INCOME, 15, 1, true),
        Category(84, "基金收益", "analytics", "#4A90D8", BillType.INCOME, 15, 2, true),
        Category(85, "股票收益", "trending-up", "#4A90D8", BillType.INCOME, 15, 3, true),
        Category(86, "分红", "cash", "#4A90D8", BillType.INCOME, 15, 4, true),
        // 兼职(16)
        Category(87, "稿费", "create", "#6FB1A8", BillType.INCOME, 16, 1, true),
        Category(88, "外快", "briefcase", "#6FB1A8", BillType.INCOME, 16, 2, true),
        Category(89, "劳务费", "construct", "#6FB1A8", BillType.INCOME, 16, 3, true),
        // 红包(17)
        Category(90, "微信红包", "cash", "#F2775F", BillType.INCOME, 17, 1, true),
        Category(91, "生日礼金", "gift", "#F2775F", BillType.INCOME, 17, 2, true),
        Category(92, "婚礼礼金", "heart", "#F2775F", BillType.INCOME, 17, 3, true),
        // 其他收入(18)
        Category(93, "报销", "receipt", "#9AA5B1", BillType.INCOME, 18, 1, true),
        // 退款 id 用常量：统计口径要用它（退款不参与一级上卷），见 RefundCategory
        Category(RefundCategory.ID, "退款", "arrow-undo", "#9AA5B1", BillType.INCOME, 18, 2, true),
        Category(95, "中奖", "trophy", "#9AA5B1", BillType.INCOME, 18, 3, true)
    )

    val all: List<Category> =
        expense + income + transfer + subExpense + subIncome + subTransfer
}
