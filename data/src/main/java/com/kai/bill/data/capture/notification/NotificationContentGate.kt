package com.kai.bill.data.capture.notification

import com.kai.bill.data.parser.KeywordMatcher
import com.kai.bill.data.parser.Matchable
import com.kai.bill.data.presets.DefaultCategoryKeywords
import com.kai.bill.data.presets.DefaultMatchKeywords
import com.kai.bill.domain.model.SourceType

/**
 * 通知内容闸门（第二道，第一道是 [NotificationWhitelist] 的包名）。
 *
 * 广告同样带金额（`以旧换新补贴至高1100元`、`50元充值券`），而且**往往还带品牌词**，
 * 于是会被流水线的「分类词兜底方向」判成一笔真实支出（真机两次误记：
 * 京东短信 → 1100 元 · 网购；中国电信短信 → 50 元 · 会员订阅）。
 * 只靠金额与排除词拦不住，所以这里与微信同一思路：**先证明它是账单，才放行**。
 *
 * 各来源的口径：
 * - 短信：必须有**方向词或结构词**（尾号 / 余额 / 交易时间），且不含广告词、不含链接；
 * - 银行 App：同上，但不动链接规则（银行推送偶有官网链接）；
 * - 支付宝：证据放宽到分类词，保住 `支付宝 星巴克 ¥38` 这类真通知；
 * - 微信：必须带「微信支付」（见 [WechatPayNoticeGate]）；
 * - 其余包不设限，去留由流水线的四级路由决定。
 *
 * 关键取舍：**短信 / 银行渠道不接受品牌词当证据**。品牌词是广告进流水线的主要入口
 * （京东 / 淘宝 / 腾讯视频 / 会员…），而真实动账必然说得出「消费 / 入账 / 扣款」或带上卡号。
 */
object NotificationContentGate {

    /**
     * @param packageName 通知来源包名
     * @param rawText 抽取并归一化后的通知文本（标题 + 正文）
     * @return true 表示这条通知可以进入解析
     */
    fun passes(packageName: String, rawText: String): Boolean = when (packageName) {
        // 营销短信基本都带短链，而真实动账短信不带 —— 这条与话术无关，换文案也绕不过
        in NotificationWhitelist.SMS -> passesAccountNotice(rawText, linkImpliesAd = true)

        in NotificationWhitelist.BANKS -> passesAccountNotice(rawText, linkImpliesAd = false)

        in NotificationWhitelist.ALIPAY -> isBillNotice(rawText)

        else -> WechatPayNoticeGate.passes(packageName, rawText)
    }

    /** 短信 / 银行 App：只认方向词或结构词，品牌词不算证据 */
    private fun passesAccountNotice(rawText: String, linkImpliesAd: Boolean): Boolean {
        if (hasAdWord(rawText)) return false
        if (linkImpliesAd && LINK.containsMatchIn(rawText)) return false
        return hasDirectionWord(rawText) || hasStructureWord(rawText)
    }

    /** 支付宝：证据可放宽到分类词 */
    private fun isBillNotice(rawText: String): Boolean =
        !hasAdWord(rawText) && (
            hasDirectionWord(rawText) ||
                hasStructureWord(rawText) ||
                hasCategoryWord(rawText)
            )

    private fun hasAdWord(rawText: String): Boolean {
        val text = rawText.lowercase()
        return AD_WORDS.any { text.contains(it) }
    }

    private fun hasDirectionWord(rawText: String): Boolean =
        keywordHit(DefaultMatchKeywords.direction, rawText)

    private fun hasCategoryWord(rawText: String): Boolean =
        keywordHit(DefaultCategoryKeywords.all, rawText)

    /** 结构证据词：出现即说明是账户 / 卡相关的通知，广告不会带 */
    private fun hasStructureWord(rawText: String): Boolean {
        val text = rawText.lowercase()
        return STRUCTURE_WORDS.any { text.contains(it) }
    }

    /**
     * 词表命中判断直接复用流水线的 [KeywordMatcher] 与词表，不再维护第二份清单：
     * 两处口径不一致的结果就是「闸门放行了，流水线却判不出方向」。
     * 用 [KeywordMatcher] 而不是 `contains` 还顺带保住了「支付」不许命中「支付宝」的保留区间。
     */
    private fun keywordHit(candidates: List<Matchable>, rawText: String): Boolean =
        KeywordMatcher.best(
            candidates = candidates,
            rawText = rawText,
            source = SourceType.NOTIFICATION,
            reserved = DefaultMatchKeywords.reserved
        ) != null

    /** 结构证据词（全部小写；匹配前对文本做 lowercase） */
    private val STRUCTURE_WORDS: List<String> = listOf(
        "尾号", "卡号", "交易时间", "交易金额", "账户余额", "可用余额", "当前余额"
    )

    /**
     * 链接：`http(s)://` / `www.` / 任意域名后接路径（`a.189.cn/xxx`、`3.cn/xxx`、`yyds.co/xxx`）。
     *
     * 域名那一支刻意不枚举顶级域：短链用的 `.co` / `.top` / `.xyz` 之类枚举不完，
     * 漏一个就等于漏一类广告。短信渠道本就是「带链接即广告」，宽松匹配反而更符合口径。
     */
    private val LINK = Regex(
        """(https?://|www\.|[a-z0-9-]+\.[a-z]{2,}/)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 广告词 = 方向词表里的排除词 + 通知侧额外的高频营销词。
     *
     * 排除词那份必须复用：同一句话在闸门与流水线里的判定不能打架。
     */
    private val AD_WORDS: List<String> by lazy {
        DefaultMatchKeywords.exclude.map { it.keyword } + EXTRA_AD_WORDS
    }

    /**
     * 通知侧额外广告词（全部小写）。
     *
     * 只收「几乎只出现在营销文案里」的词。刻意**不收**下面这些：
     * - `额度`：真机动账短信常带「可用额度」，收了会把真实消费一起丢掉；
     * - `会员` / `试用`：`会员续费 25 元` 是真实支出；
     * - `中奖`：方向词表把它当收入词，两边不能打架。
     * 宁可少收几个，也不要误伤真实动账 —— 广告还有排除词兜一层。
     */
    private val EXTRA_AD_WORDS: List<String> = listOf(
        // 授信 / 费率
        "提额", "免息", "年化", "费率",
        // 权益 / 活动
        "积分", "权益", "福利", "抽奖", "秒杀", "特惠", "办卡", "推荐办理",
        // 促销话术（真机样本：京东「以旧换新补贴至高1100元」）
        "至高", "低至", "以旧换新", "购机", "仅需", "立省", "包邮", "戳",
        // 券与邀约（真机样本：中国电信「50元充值券…邀您参与…解锁…好礼」）
        "券", "好礼", "邀您", "解锁", "专享",
        // 假「交易提醒」广告（真机样本：`交易提醒 你有一笔6.00元的支出，点击领取2元流量红包。`）
        // 刻意**不收**裸的「点击」：真实动账里也有 `小荷包…凯支付了5.00元，点击查看详情>`，
        // 收了会把真账一起丢掉。只收「点击领取」这种「点一下能拿东西」的措辞。
        "点击领取", "点击领",
        // 退订尾巴：短信广告的固定结尾
        "退订", "拒收", "回复td", "回复t"
    )
}
