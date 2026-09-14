package com.kai.bill.data.parser

import com.kai.bill.domain.model.BillType

/**
 * 商户 / 场景词 / 口语化表达 → (二级分类 id, 收支类型) 的轻量映射。
 *
 * 用途：通知经 [RuleEngine] 命中（已确认来自支付宝 / 微信等白名单且含金额）后，
 * 再用本映射按正文里的商户名、场景词或口语化表达细化到具体二级分类，
 * 避免所有自动账单都落「其他支出 / 支出」。
 *
 * 为什么可以放心使用较口语化的词（如「搓一顿」「打个车」）：
 * 本匹配**只在通知已被规则命中之后**执行，聊天等无关文本根本不会进入 [BillIngestor]，
 * 故误抓风险极低。
 *
 * 数据来源：人工整理的收支类型文案库（按场景 / 分类结构化），分类 id 对应 [DefaultCategories]。
 * 优先匹配「更具体的词」（关键词更长者胜），让品牌词压过泛义词，降低覆盖偏差。
 */
object CategoryKeywordMatcher {

    private data class Entry(
        val keywords: List<String>,
        val categoryId: Long,
        val type: BillType
    )

    private val entries: List<Entry> = listOf(
        // —— 餐饮(1) ——
        Entry(listOf("星巴克", "瑞幸", "喜茶", "蜜雪冰城", "咖啡", "奶茶", "喝一杯"), 24, BillType.EXPENSE),
        Entry(
            listOf(
                "早饭", "早餐", "豆浆", "油条", "包子", "煎饼", "午饭", "工作餐", "外卖",
                "食堂", "晚饭", "夜宵", "烧烤", "火锅", "聚餐", "家宴", "搓一顿", "恰饭", "干饭"
            ),
            21, BillType.EXPENSE
        ),
        Entry(listOf("可乐", "薯片", "甜品", "蛋糕", "水果", "坚果", "零食", "整点零食"), 23, BillType.EXPENSE),
        Entry(listOf("超市", "菜市场", "生鲜", "盒马", "叮咚", "买菜", "京东买菜", "烹饪食材"), 25, BillType.EXPENSE),

        // —— 交通(2) ——
        Entry(listOf("滴滴", "出租车", "顺风车", "拼车", "专车", "花小猪", "打车", "打个车"), 27, BillType.EXPENSE),
        Entry(listOf("地铁", "公交", "轮渡", "城际铁路", "坐地铁"), 26, BillType.EXPENSE),
        Entry(listOf("中石化", "中石油", "充电", "换电", "加油", "加油卡", "加个油"), 28, BillType.EXPENSE),
        Entry(listOf("停车费", "停车月卡", "路边停车", "小区停车", "停车", "停个车"), 29, BillType.EXPENSE),
        Entry(listOf("洗车", "保养", "修车", "换胎", "4S店"), 30, BillType.EXPENSE),
        Entry(listOf("机票", "火车票", "高铁", "大巴", "船票", "飞一趟"), 59, BillType.EXPENSE),
        Entry(listOf("ETC", "过路费", "高速费"), 28, BillType.EXPENSE),

        // —— 购物(3) ——
        Entry(listOf("纸巾", "洗衣液", "牙膏", "洗发水", "清洁用品", "日用品"), 31, BillType.EXPENSE),
        Entry(listOf("手机", "电脑", "耳机", "充电器", "平板", "配件", "电子产品"), 32, BillType.EXPENSE),
        Entry(listOf("护肤品", "化妆品", "香水", "面膜", "口红", "美妆"), 33, BillType.EXPENSE),
        Entry(listOf("衣服", "裤子", "鞋子", "包包", "帽子", "配饰", "服饰"), 11, BillType.EXPENSE),
        Entry(listOf("家具", "收纳", "装饰", "灯具", "厨具", "家居"), 35, BillType.EXPENSE),
        Entry(
            listOf(
                "淘宝", "京东", "拼多多", "抖音商城", "得物", "网购",
                "剁手", "买买买", "囤货", "下单", "海淘", "薅羊毛", "薅了波羊毛"
            ),
            3, BillType.EXPENSE
        ),

        // —— 居住(4) ——
        Entry(listOf("租金", "押一付三", "房租月付", "房租", "租房", "交了个租"), 36, BillType.EXPENSE),
        Entry(listOf("月供", "公积金还贷", "商贷", "房贷"), 37, BillType.EXPENSE),
        Entry(listOf("水费", "电费", "燃气费", "暖气费", "水电"), 38, BillType.EXPENSE),
        Entry(listOf("物业费", "车位费", "维修基金"), 39, BillType.EXPENSE),
        Entry(listOf("硬装", "软装", "设计费", "建材", "装修"), 40, BillType.EXPENSE),

        // —— 通讯(5) ——
        Entry(listOf("话费", "手机费", "充值话费"), 41, BillType.EXPENSE),
        Entry(listOf("宽带"), 42, BillType.EXPENSE),
        Entry(listOf("视频会员", "会员", "订阅", "爱奇艺", "优酷", "腾讯视频", "Netflix", "充个值"), 43, BillType.EXPENSE),
        Entry(listOf("快递", "邮寄", "顺丰", "邮政"), 44, BillType.EXPENSE),

        // —— 娱乐(6) ——
        Entry(listOf("电影票", "演唱会", "话剧", "展览", "电影"), 45, BillType.EXPENSE),
        Entry(listOf("游戏充值", "皮肤", "点券", "Steam", "PSN", "游戏"), 46, BillType.EXPENSE),
        Entry(listOf("健身", "游泳", "瑜伽", "球类", "滑雪", "攀岩", "运动", "撸个铁"), 47, BillType.EXPENSE),
        Entry(listOf("酒店", "民宿", "景点门票", "签证", "旅行团", "旅游", "旅行", "嗨一下", "出去玩"), 49, BillType.EXPENSE),

        // —— 医疗(7) ——
        Entry(listOf("西药", "中药", "保健品", "维生素", "药品", "买药"), 50, BillType.EXPENSE),
        Entry(listOf("挂号", "门诊", "看病"), 51, BillType.EXPENSE),
        Entry(listOf("体检"), 53, BillType.EXPENSE),
        Entry(listOf("洗牙", "补牙", "矫正", "种植牙"), 7, BillType.EXPENSE),

        // —— 教育(8) ——
        Entry(listOf("幼儿园", "小学", "中学", "大学", "培训班", "学费"), 55, BillType.EXPENSE),
        Entry(listOf("买书", "电子书", "Kindle", "得到", "书籍"), 56, BillType.EXPENSE),
        Entry(listOf("网课", "考证", "语言学习", "课程"), 57, BillType.EXPENSE),

        // —— 宠物(10) ——
        Entry(listOf("狗粮", "猫砂", "宠物医院", "宠物", "遛弯"), 10, BillType.EXPENSE),

        // —— 人情 / 其他支出(12) ——
        Entry(listOf("份子钱", "随礼", "请吃饭", "送礼", "随了个份子"), 72, BillType.EXPENSE),
        Entry(listOf("捐款", "公益", "众筹"), 73, BillType.EXPENSE),
        Entry(listOf("违章罚款", "逾期费", "滞纳金", "罚款"), 74, BillType.EXPENSE),

        // —— 收入 ——
        Entry(listOf("工资", "月薪", "底薪", "年薪", "发工资", "发工资了", "进账", "搬砖收入", "血汗钱"), 13, BillType.INCOME),
        Entry(listOf("年终奖", "季度奖", "绩效奖", "全勤奖", "项目奖", "奖金"), 14, BillType.INCOME),
        Entry(listOf("餐补", "交通补", "住房补", "通讯补", "高温补贴", "补贴"), 78, BillType.INCOME),
        Entry(listOf("副业", "打零工", "小时工", "freelance", "兼职"), 16, BillType.INCOME),
        Entry(listOf("微信红包", "压岁钱", "生日红包", "收到红包", "红包收入"), 90, BillType.INCOME),
        Entry(listOf("收到一笔转账", "转账收入"), 17, BillType.INCOME),
        Entry(listOf("收款到账"), 18, BillType.INCOME),
        Entry(listOf("退货退款", "理赔", "报销", "退款", "退款成功"), 94, BillType.INCOME),
        Entry(listOf("余额宝", "理财通", "基金收益", "利息", "分红", "理财"), 15, BillType.INCOME),
        Entry(listOf("营业收入", "货款", "淘宝店", "直播带货", "提成", "返利", "收了笔款"), 18, BillType.INCOME)
    )

    /**
     * 按正文匹配最具体的分类：命中多个 entry 时取「最长关键词」所在 entry（更具体者胜）。
     * 未命中返回 null，调用方应回退到规则默认分类。
     */
    fun match(text: String): Pair<Long, BillType>? {
        return entries
            .filter { e -> e.keywords.any { kw -> text.contains(kw, ignoreCase = true) } }
            .maxByOrNull { e -> e.keywords.maxOf { it.length } }
            ?.let { it.categoryId to it.type }
    }
}
