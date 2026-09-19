package com.kai.bill.data.presets

import com.kai.bill.data.parser.CategoryKeyword
import com.kai.bill.domain.model.BillType

/**
 * 分类级词表（P0：代码常量；P1 迁入 `match_keyword` 表）。
 *
 * 来源：原 `CategoryKeywordMatcher` 的 60 组人工整理词库（品牌词 / 场景词 / 口语化表达），
 * 迁移时按方向拆分，让它能被 [com.kai.bill.data.parser.CategoryRouter] 收窄到「当前方向」内匹配。
 *
 * 三条约定：
 * - **一级与二级分类 id 混用是允许的**：统计与饼图按分类聚合，两种粒度混用不会算错
 *   （例如「工资」直接落 13 一级，「咖啡奶茶」落 24 二级）；分类管理 UI 需允许选任意层级。
 * - **分类词不决定方向**（方向由 [DefaultMatchKeywords] 的动作词决定），
 *   但每个词都声明所属 [CategoryKeyword.direction]，用于把搜索范围收窄到当前方向。
 * - **口语化词是安全的**：本匹配只在「已抽出金额 + 已定方向」之后执行，
 *   聊天等无关文本根本进不到这一步（旧实现的同一论证见 `CategoryKeywordMatcher` KDoc）。
 */
object DefaultCategoryKeywords {

    private fun expense(categoryId: Long, vararg keywords: String): List<CategoryKeyword> =
        keywords.map { CategoryKeyword(it, categoryId, BillType.EXPENSE) }

    private fun income(categoryId: Long, vararg keywords: String): List<CategoryKeyword> =
        keywords.map { CategoryKeyword(it, categoryId, BillType.INCOME) }

    private fun transfer(categoryId: Long, vararg keywords: String): List<CategoryKeyword> =
        keywords.map { CategoryKeyword(it, categoryId, BillType.TRANSFER) }

    val all: List<CategoryKeyword> = buildList {
        // —— 餐饮(1) ——
        addAll(expense(24, "星巴克", "瑞幸", "喜茶", "蜜雪冰城", "咖啡", "奶茶", "喝一杯"))
        addAll(
            expense(
                21, "早饭", "早餐", "豆浆", "油条", "包子", "煎饼", "午饭", "工作餐", "外卖",
                "食堂", "晚饭", "夜宵", "烧烤", "火锅", "聚餐", "家宴", "搓一顿", "恰饭", "干饭"
            )
        )
        addAll(expense(23, "可乐", "薯片", "甜品", "蛋糕", "水果", "坚果", "零食", "整点零食"))
        addAll(expense(25, "超市", "菜市场", "生鲜", "盒马", "叮咚", "买菜", "京东买菜", "烹饪食材"))

        // —— 交通(2) ——
        addAll(expense(27, "滴滴", "出租车", "顺风车", "拼车", "专车", "花小猪", "打车", "打个车"))
        addAll(expense(26, "地铁", "公交", "轮渡", "城际铁路", "坐地铁"))
        addAll(expense(28, "中石化", "中石油", "充电", "换电", "加油", "加油卡", "加个油", "ETC", "过路费", "高速费"))
        addAll(expense(29, "停车费", "停车月卡", "路边停车", "小区停车", "停车", "停个车"))
        addAll(expense(30, "洗车", "保养", "修车", "换胎", "4S店"))
        addAll(expense(59, "机票", "火车票", "高铁", "大巴", "船票", "飞一趟"))

        // —— 购物(3) ——
        addAll(expense(31, "纸巾", "洗衣液", "牙膏", "洗发水", "清洁用品", "日用品"))
        addAll(expense(32, "手机", "电脑", "耳机", "充电器", "平板", "配件", "电子产品"))
        addAll(expense(33, "护肤品", "化妆品", "香水", "面膜", "口红", "美妆"))
        addAll(expense(11, "衣服", "裤子", "鞋子", "包包", "帽子", "配饰", "服饰"))
        addAll(expense(35, "家具", "收纳", "装饰", "灯具", "厨具", "家居"))
        addAll(
            expense(
                3, "淘宝", "京东", "拼多多", "抖音商城", "得物", "网购",
                "剁手", "买买买", "囤货", "海淘", "薅羊毛", "薅了波羊毛"
            )
        )

        // —— 居住(4) ——
        addAll(expense(36, "租金", "押一付三", "房租月付", "房租", "租房", "交了个租"))
        addAll(expense(37, "月供", "公积金还贷", "商贷", "房贷"))
        addAll(expense(38, "水费", "电费", "燃气费", "暖气费", "水电"))
        addAll(expense(39, "物业费", "车位费", "维修基金"))
        addAll(expense(40, "硬装", "软装", "设计费", "建材", "装修"))

        // —— 通讯(5) ——
        addAll(expense(41, "话费", "手机话费", "充值话费"))
        addAll(expense(42, "宽带"))
        addAll(expense(43, "视频会员", "会员", "订阅", "爱奇艺", "优酷", "腾讯视频", "Netflix"))
        addAll(expense(44, "快递", "邮寄", "顺丰", "邮政"))

        // —— 娱乐(6) ——
        addAll(expense(45, "电影票", "演唱会", "话剧", "展览", "电影"))
        addAll(expense(46, "游戏充值", "皮肤", "点券", "Steam", "PSN", "游戏"))
        addAll(expense(47, "健身", "游泳", "瑜伽", "球类", "滑雪", "攀岩", "运动", "撸个铁"))
        addAll(expense(49, "酒店", "民宿", "景点门票", "签证", "旅行团", "旅游", "旅行", "嗨一下", "出去玩"))

        // —— 医疗(7) ——
        addAll(expense(50, "西药", "中药", "保健品", "维生素", "药品", "买药"))
        addAll(expense(51, "挂号", "门诊", "看病"))
        addAll(expense(53, "体检"))
        addAll(expense(7, "洗牙", "补牙", "矫正", "种植牙"))

        // —— 教育(8) ——
        addAll(expense(55, "幼儿园", "小学", "中学", "大学", "培训班", "学费"))
        addAll(expense(56, "买书", "电子书", "Kindle", "得到", "书籍"))
        addAll(expense(57, "网课", "考证", "语言学习", "课程"))

        // —— 宠物(10) ——
        addAll(expense(10, "狗粮", "猫砂", "宠物医院", "宠物", "遛弯"))

        // —— 人情 / 其他支出(12) ——
        addAll(expense(72, "份子钱", "随礼", "请吃饭", "送礼", "随了个份子"))
        addAll(expense(73, "捐款", "公益", "众筹"))
        addAll(expense(74, "违章罚款", "逾期费", "滞纳金", "罚款"))

        // —— 收入 ——
        addAll(income(13, "工资", "月薪", "底薪", "年薪", "发工资", "发工资了", "进账", "搬砖收入", "血汗钱"))
        addAll(income(14, "年终奖", "季度奖", "绩效奖", "全勤奖", "项目奖", "奖金"))
        addAll(income(78, "餐补", "交通补", "住房补", "通讯补", "高温补贴", "补贴"))
        addAll(income(16, "副业", "打零工", "小时工", "freelance", "兼职"))
        addAll(income(15, "余额宝", "理财通", "基金收益", "股票收益", "利息", "分红", "理财"))
        // 红包类：方向由 [DefaultMatchKeywords] 判为「待确认」，这里的分类是**建议值**（收入·红包）
        addAll(income(90, "微信红包", "压岁钱", "生日红包", "收到红包", "红包收入"))
        addAll(income(18, "收款到账", "营业收入", "货款", "直播带货", "返利", "收了笔款"))
        // 修正：旧实现把「收到一笔转账」映射到 17 红包（一级），语义上应是 18 其他收入
        addAll(income(18, "收到一笔转账", "转账收入"))
        // 修正：旧实现把「报销 / 理赔」映射到 94 退款，这里拆到 93 报销
        addAll(income(93, "报销到账", "报销", "理赔"))
        addAll(income(94, "退货退款", "退款成功", "退款"))

        // —— 转账 ——
        // 19 与 20 是两个不同场景：19 转账（钱在账户之间搬），20 还款（还信用卡 / 花呗）。
        // 「转出 / 提现」这些词既出现在方向词表（判为待确认），也在这里给**建议分类**用 ——
        // 否则转出类通知的建议分类会落到 TRANSFER 的兜底 20 还款，用户看到「转出成功」被建议成
        // 「还款」会一头雾水。
        addAll(transfer(19, "转账", "转出成功", "转出", "取现", "提现", "代付"))
        addAll(transfer(20, "信用卡还款", "还信用卡", "自动还款", "分期还款", "还款成功", "还款"))

        // 转账(19) 的两个二级：按钱的方向分。
        // 依据是微信 / 支付宝转账详情页的两句固定文案：
        // - `你已收款，资金已存入零钱 ¥100` → 钱进来了，落「转入」(96)；
        // - `<对方>已收款 ¥200`            → 我转出去、对方收到了，落「转出」(97)。
        // 「资金已存入零钱」「你已收款」都比「已收款」长，而 [KeywordMatcher.best] 同级时
        // 比长度，所以收款方视角一定先命中「转入」，剩下的「已收款」才归「转出」。
        addAll(transfer(96, "资金已存入零钱", "你已收款"))
        addAll(transfer(97, "已收款", "转账给"))
    }
}
