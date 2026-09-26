package com.kai.bill.data.presets

import com.kai.bill.data.parser.DirectionKeyword
import com.kai.bill.data.parser.MatchKeywordTables
import com.kai.bill.data.parser.MatchRoute

/**
 * 金额识别模式的唯一真相，S1 [com.kai.bill.data.parser.AmountGate] 直接复用 ——
 * 别处不要再写一份金额正则：两份迟早不一致，而不一致的后果是错账。
 */
object AmountPattern {

    /** 只认「货币语境」里的数字：带符号 `¥12.00`、带小数点 `12.00`、带「元」后缀 `12元`（纯整数不算） */

    val ANY: Regex = Regex(
        "(?:[¥￥]\\s*[\\d,]+(?:\\.\\d{1,2})?)|[\\d,]+\\.\\d{1,2}|[\\d,]+\\s*元"
    )
}

/**
 * 方向级词表（本文件只声明数据，**判定顺序由 [com.kai.bill.data.parser.DirectionRouter] 保证**）。
 *
 * 优先级：`0` 常规、`-10` 泛化词（无具体词命中时才采用）、`10` 强词（必须压过泛义词）；
 * 同级比较**更长的词获胜**。排除词优先于一切 —— 失败 / 预告 / 营销通知也带金额。
 */
object DefaultMatchKeywords {

    // —— 泛化词与强词的优先级刻度 ——
    private const val PRIORITY_GENERIC = -10
    private const val PRIORITY_NORMAL = 0
    private const val PRIORITY_STRONG = 10
    // 支出
    private fun expense(vararg keywords: String): List<DirectionKeyword> =
        keywords.map { DirectionKeyword(it, MatchRoute.EXPENSE, PRIORITY_NORMAL) }
    // 收入
    private fun income(vararg keywords: String): List<DirectionKeyword> =
        keywords.map { DirectionKeyword(it, MatchRoute.INCOME, PRIORITY_NORMAL) }

    /**
     * 泛化入账 / 泛化交易：只在没有具体词命中时才采用。
     *
     * 「入账」既可能是别人给你钱，也可能是自己的钱搬家（提现），因此没有具体词兜底时
     * 一律交人工确认 —— 宁可不记账，也不虚增收入。
     */
    private fun generic(keyword: String, route: MatchRoute): DirectionKeyword =
        DirectionKeyword(keyword, route, PRIORITY_GENERIC)

    /**
     * 排除词：命中即静默丢弃（不落库、不提示、只写诊断）。
     *
     * 四类文案同样带金额，不先拦住就会记成真实收支：
     * 失败（`支付宝 支付失败 ¥88.00`）、预告（`信用卡账单已出，本期应还 ¥1,200`）、
     * 营销（`满50元减10元`）、申请中（`退款申请已提交，预计 3 天到账 ¥29.80`）。
     */
    val exclude: List<DirectionKeyword> = buildList {
        // —— 失败 / 已取消 ——
        addAll(
            listOf(
                "支付失败", "交易失败", "付款失败", "扣款失败", "扣费失败", "转账失败", "退款失败",
                "提现失败", "还款失败", "充值失败", "缴费失败", "未成功", "已取消", "已撤销",
                "已作废", "交易关闭", "订单关闭"
            ).map { DirectionKeyword(it, MatchRoute.EXCLUDE, PRIORITY_STRONG) }
        )
        // —— 预告 / 提醒（尚未发生）——
        // 注意这里**不能放「自动扣款」**：`已自动扣款 ¥88` 是已发生的真实交易，
        // 而 `将于9月20日自动扣款` 是提醒；两者靠「将于 / 即将」区分，不能一刀切。
        addAll(
            listOf(
                "待支付", "待付款", "待缴费", "即将扣款", "将于", "即将", "账单已出", "本期应还",
                "最低还款", "还款日", "请及时还款", "到期提醒", "还款提醒", "请尽快缴费", "余额不足"
            ).map { DirectionKeyword(it, MatchRoute.EXCLUDE, PRIORITY_STRONG) }
        )
        // —— 营销 / 权益 ——
        // 「满50元减10元」用 `元减` 拦：既覆盖该文案，又不会误伤「满足月均资产要求」这类正常文本。
        // 词表统一按 contains 匹配、不跑正则，所以「满」「减」这种单字绝不能单独进表 ——
        // 一旦误伤，丢掉的是一条真实入账，比漏拦营销要严重得多。
        addAll(
            listOf(
                "满减", "立减", "元减", "优惠券", "红包待领", "待领取", "领取红包",
                "免费领", "限时", "折扣", "返券"
            ).map { DirectionKeyword(it, MatchRoute.EXCLUDE, PRIORITY_NORMAL) }
        )
        // —— 申请中 / 处理中（未完成）——
        addAll(
            listOf("退款申请", "申请中", "处理中", "待审核", "预计到账", "预计")
                .map { DirectionKeyword(it, MatchRoute.EXCLUDE, PRIORITY_NORMAL) }
        )
    }

    /**
     * 保留词：品牌 / 应用名。
     *
     * **不参与任何走向判定**，只在匹配前把出现区间标记为「已占用」，让落在其中的短词失效。
     * 典型场景：「支付」是「支付宝」的子串，不处理的话每一条支付宝通知都会命中支出 ——
     * `支付宝 收款 ¥12.00` 被记成支出、`支付宝 转账` 被记成支出。
     *
     * 为什么不直接把「支付」从词表里删掉：删词只解决当下这一处，将来任何人往词表里
     * 加短词（「付」「金」「退」…）都会重踩同一个坑。这里用「先认长词、短词不许进已占区间」
     * 的通用规则兜住，[DirectionRouter] 负责执行。
     */
    val reserved: List<String> = listOf(
        "支付宝",
        "微信支付",
        "云闪付"
    )

    /**
     * 方向动作词。
     *
     * 只放「动作」，不放商户与场景词 —— 商户词属于分类级（见 [DefaultCategoryKeywords]），
     * 两者分开才能让「先定方向、再在方向内选分类」的路由成立。
     *
     * 「支付」这类短词可以放心收（例如 `支付宝 支付 ¥88`），品牌名重叠由 [reserved] 处理。
     * 「交易提醒」不收：它过于宽泛，且真实样本里都带着「支出 / 退款 / 转出」这类具体动作词，
     * 靠具体词判定更准。
     */
    val direction: List<DirectionKeyword> = buildList {
        // —— 支出：支付 / 消费 / 扣款 / 缴费 ——
        addAll(
            expense(
                "支付成功", "已支付", "扫码支付", "快捷支付", "支付", "付款成功", "已付款", "付款",
                "消费支出", "POS消费", "消费", "支出", "扣款成功", "已扣款", "扣款", "扣费",
                "代扣", "缴费成功", "缴费", "缴纳", "续费", "交易成功", "订购", "下单"
            )
        )

        // —— 收入：具体收入词（可放心计入统计）——
        addAll(
            income(
                // 收款
                "收款到账", "收款成功", "扫码收款", "收款", "代收",
                // 薪酬
                "发工资", "工资", "薪资", "薪水", "月薪", "底薪", "年终奖", "季度奖", "绩效奖",
                "项目奖", "全勤奖", "奖金", "绩效", "提成", "稿费", "劳务费", "加班费", "补贴到账",
                // 报销 / 退款
                "报销到账", "报销", "理赔", "退款成功", "已退款", "退款", "退还", "退回",
                // 理财收益
                "基金收益", "股票收益", "理财收益", "收益发放", "收益", "利息", "分红",
                "返现", "返利", "中奖", "奖励金",
                // 经营
                "营业收入", "货款", "直播带货", "收了笔款"
            )
        )

        // —— 转账：还款类。语义明确、用户需要「本月还了多少」，直接落库但不计统计 ——
        addAll(
            listOf(
                "信用卡还款", "还信用卡", "自动还款", "分期还款", "已还款", "还款成功", "还款"
            ).map { DirectionKeyword(it, MatchRoute.TRANSFER, PRIORITY_NORMAL) }
        )

        // —— 转账：个人转账的**转入 / 转出**（2026-09-20 真机样本）——
        // 为什么必须单独成组：微信个人转账的两个方向，页面文案**都带「收款」二字** ——
        //   · 转出：`周建鑫已收款 ¥200.00`（我把钱转给了周建鑫，付款方视角）
        //   · 转入：`你已收款，资金已存入零钱 ¥100.00`（别人转给我）
        // 只按裸的「收款」判会把**转出记成一笔收入**（¥200 凭空进收入统计），
        // 所以先在方向这一层把两者都收敛到转账，再由分类词区分是转入还是转出。
        //
        // 只收「**只可能出现在个人转账里**」的表述，宁可漏也不误伤：
        // - **不**收「转账时间 / 转账单号」：微信「扫码付款」的账单详情里同样有这两个字段
        //   （真机样本 `扫二维码付款-给Sparking`），拿它当方向证据会把真支出判成转账；
        // - **不**收裸的「对方已收款」：付款成功页的文案里就有它（`支付成功 对方已收款`），
        //   收进来会让每一笔付款都被判成转账；
        // - 「已收款」放在**常规优先级**：`支付成功 / 收款到账 / 收款成功` 都比它长，
        //   在付款与商户收款场景里会先被选走，它只在「某某已收款」这类转账页里胜出。
        addAll(
            listOf("你已收款", "资金已存入零钱", "转账给", "已转账")
                .map { DirectionKeyword(it, MatchRoute.TRANSFER, PRIORITY_STRONG) }
        )
        addAll(
            listOf("收到一笔转账", "转账收入", "已收款")
                .map { DirectionKeyword(it, MatchRoute.TRANSFER, PRIORITY_NORMAL) }
        )

        // —— 待确认 · 自己的钱搬家 ——
        addAll(
            listOf(
                "转出成功", "已转出", "转出到", "转出", "取现", "代付",
                "零钱提现", "余额提现", "提现到", "提现"
            ).map { DirectionKeyword(it, MatchRoute.SELF_TRANSFER, PRIORITY_NORMAL) }
        )

        // —— 待确认 · 红包 ——
        addAll(
            listOf("微信红包", "收到红包", "发红包", "红包", "压岁钱", "生日红包")
                .map { DirectionKeyword(it, MatchRoute.GIFT, PRIORITY_NORMAL) }
        )

        // —— 待确认 · 泛化入账（与「工资 / 报销」等具体词同现时由优先级让位）——
        add(generic("入账", MatchRoute.GENERIC_INBOUND))
        add(generic("转入", MatchRoute.GENERIC_INBOUND))
        add(generic("存入", MatchRoute.GENERIC_INBOUND))
        add(generic("到账", MatchRoute.GENERIC_INBOUND))
    }

    /**
     * 三组词表的组装结果，交给流水线使用。
     *
     * 这是 P0 与 P1 的**唯一接缝**：P1 词表入库后，只需让 Hilt 改成从仓储提供
     * 同一类型的实例（并保持热更新缓存），四级阶段类与 [com.kai.bill.data.ingest.IngestPipeline] 零改动。
     */
    val tables: MatchKeywordTables = MatchKeywordTables(
        direction = direction,
        exclude = exclude,
        category = DefaultCategoryKeywords.all,
        reserved = reserved
    )
}
