package com.kai.bill.data.capture.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentGateTest {

    private val wechat = "com.tencent.mm"
    private val alipay = "com.eg.android.AlipayGphone"
    private val cmb = "cmb.pb"
    private val sms = "com.android.mms"

    // ---------------- 微信：口径不变 ----------------

    @Test
    fun `微信支付通知-放行`() {
        assertTrue(NotificationContentGate.passes(wechat, "微信支付 [2条]微信支付: 退款到账通知"))
    }

    @Test
    fun `微信聊天通知-不通过`() {
        assertFalse(NotificationContentGate.passes(wechat, "老爸 [视频号]瑞核助残-老陈的视频"))
    }

    // ---------------- 支付宝：动账放行，广告拦住 ----------------

    @Test
    fun `支付宝支付成功-放行`() {
        assertTrue(NotificationContentGate.passes(alipay, "支付宝 支付成功 ¥28.30"))
    }

    @Test
    fun `支付宝商户词无动作词-放行`() {
        // 真机样本形态：只有品牌词，支付宝渠道放宽放行
        assertTrue(NotificationContentGate.passes(alipay, "支付宝 星巴克 ¥38.00"))
    }

    @Test
    fun `支付宝提额广告-不通过`() {
        assertFalse(NotificationContentGate.passes(alipay, "支付宝 花呗可提额至 50000 元，点击查看"))
    }

    @Test
    fun `支付宝满减广告-不通过`() {
        assertFalse(NotificationContentGate.passes(alipay, "支付宝 满 100 元立减 20 元"))
    }

    // ---------------- 银行 App ----------------

    @Test
    fun `银行动账通知-放行`() {
        assertTrue(NotificationContentGate.passes(cmb, "您尾号1234的储蓄卡消费支出人民币100.00元"))
    }

    @Test
    fun `银行无动作词但有卡号-放行`() {
        assertTrue(NotificationContentGate.passes(cmb, "您尾号1234的账户发生一笔交易 金额100.00元"))
    }

    @Test
    fun `银行额度广告-不通过`() {
        assertFalse(NotificationContentGate.passes(cmb, "信用卡提额 最高 50000 元 立即查看"))
    }

    // ---------------- 短信：真动账放行，广告一律拦住 ----------------

    @Test
    fun `短信银行动账-放行`() {
        assertTrue(NotificationContentGate.passes(sms, "【工商银行】您尾号1234的储蓄卡入账1000.00元"))
    }

    @Test
    fun `短信验证码-不通过`() {
        assertFalse(NotificationContentGate.passes(sms, "【某某】您的验证码是 123456"))
    }

    /** 真机误记样本一：品牌词「京东」给了方向 → 曾记成 1100 元 · 网购 */
    @Test
    fun `短信京东营销-不通过`() {
        val text = "【京东】小米18 Pro系列定档9月23日19点发布！购机享以旧换新补贴至高1100元，" +
            "戳 3.cn/v/-1ZBuJ2E 拒收请回复R"
        assertFalse(NotificationContentGate.passes(sms, text))
    }

    /** 真机误记样本二：品牌词「腾讯视频」给了方向 → 曾记成 50 元 · 会员订阅 */
    @Test
    fun `短信电信营销-不通过`() {
        val text = "【中国电信】尊敬的用户，商家邀您参与国潮风华，解锁腾讯视频SVIP年卡，" +
            "50元充值券等多重好礼；https://a.189.cn/nfBcFY 拒收请回复R。"
        assertFalse(NotificationContentGate.passes(sms, text))
    }

    /** 去掉营销词的短信广告：仍然拦得住（靠「无方向词 / 无结构词」，不是靠话术） */
    @Test
    fun `短信无营销词但只有品牌词-也不通过`() {
        assertFalse(NotificationContentGate.passes(sms, "【京东】小米18 Pro系列 9月23日19点发布 1100元"))
    }

    @Test
    fun `短信带链接-按广告处理`() {
        assertFalse(NotificationContentGate.passes(sms, "【某某商城】点击领取 https://a.189.cn/abc"))
    }

    /** 真机误记样本三：`8.3折` 被当成 8.30 元 + 品牌词「瑞幸」给了方向 → 记成 ¥8.30 支出 */
    @Test
    fun `短信瑞幸营销-不通过`() {
        val text = "[9条]【瑞幸咖啡】送你8.3折专享礼，今日推荐试试生椰拿铁（首创）→ yyds.co/ENe2…"
        assertFalse(NotificationContentGate.passes(sms, text))
    }

    /** 真机误记样本四：`11.25GB` 被当成 11.25 元 + 品牌词「中国移动」给了方向 → 记成 ¥11.25 支出 */
    @Test
    fun `短信流量提醒-不通过`() {
        val text = "【流量提醒】尊敬的客户，您好！截止09月24日19点28分，您198****6030本月移动上网流量已使用" +
            "11.25GB，其中国内通用流量资源已使用5.44GB。超出后按0.29元/MB计费，每GB最高收取5元。" +
            "详情请登录中国移动APP https://dx.10086.cn/A/RhasHA。【中国移动】"
        assertFalse(NotificationContentGate.passes(sms, text))
    }

    /** 假「交易提醒」广告：带方向词「支出」，只能靠「点击领取」拦下 */
    @Test
    fun `假交易提醒广告-不通过`() {
        assertFalse(
            NotificationContentGate.passes(
                alipay,
                "交易提醒 你有一笔6.00元的支出，点击领取2元流量红包。"
            )
        )
    }

    /** 反向护栏：真账也会说「点击查看详情」，不能被当成广告丢掉 */
    @Test
    fun `真实动账带点击查看详情-放行`() {
        assertTrue(
            NotificationContentGate.passes(
                alipay,
                "小荷包「烟火储备金」资金变动 凯支付了5.00元，点击查看详情>"
            )
        )
    }

    // ---------------- 其它包不受本闸门约束 ----------------

    @Test
    fun `其它包直接放行`() {
        assertTrue(NotificationContentGate.passes("com.sankuai.meituan", "美团外卖 已支付 ¥28.30"))
    }
}
