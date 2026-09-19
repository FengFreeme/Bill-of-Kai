package com.kai.bill.data.capture.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「这一屏是不是账单详情页」的回归测试 —— 用的是真机上跑出来的**真实页面文本**。
 *
 * 每次误判都会凭空建一笔错账并弹卡，所以三类真账单要放行、四类非账单要拦住。
 */
class BillPageHeuristicsTest {

    // ---------------- 应当放行：真账单详情页 ----------------

    @Test
    fun `微信商户账单详情页-是账单页`() {
        assertTrue(
            BillPageHeuristics.isBillDetail(
                "全部账单 本服务由财付通提供 当前状态 支付成功 支付时间 2026年9月18日 19:49:03 " +
                    "商品 余额充值 商户全称 常州门户文化传媒有限公司 收单机构 宝付支付（上海）有限公司 " +
                    "支付方式 零钱 交易单号 4500000479202609184073820364 账单服务 对订单有疑惑 " +
                    "发起群收款 在此商户的交易 常来停智慧停车 -1.00"
            )
        )
    }

    @Test
    fun `微信个人转账详情页-是账单页`() {
        assertTrue(
            BillPageHeuristics.isBillDetail(
                "全部账单 本服务由财付通提供 当前状态 支付成功 收款方备注 二维码收款 支付方式 零钱 " +
                    "转账时间 2026年9月18日 23:09:06 转账单号 10001073012026091800609962660139 " +
                    "账单服务 对订单有疑惑 扫二维码付款-给Sparking -0.10"
            )
        )
    }

    @Test
    fun `支付宝账单详情页-是账单页`() {
        assertTrue(
            BillPageHeuristics.isBillDetail(
                "返回 全部账单 账单详情 55.84元 交易成功 支付时间 2026-09-19 15:41:05 " +
                    "付款方式 支付宝小荷包(烟火储备金) 商品说明 小九烧烤外卖订单 支付奖励 更多 账单管理"
            )
        )
    }

    // ---------------- 应当拦住：非账单页 ----------------

    @Test
    fun `支付宝小荷包账户页-不是账单页`() {
        // 真机复现（2026-09-19），**按 dump 到的真实文本**：这一屏曾被判成账单详情页 ——
        // 明细里那行「退款-商户单号 XP…」命中了「商户单号」字段，
        // 而「总金额 564.18」又被当成了交易额，于是凭空记了一笔 ¥564.18 并弹卡。
        assertFalse(
            BillPageHeuristics.isBillDetail(
                "返回 主页 设置 烟火储备金 邀请他人加入 总金额(元) 564.18 查看权益 " +
                    "定期金额¥0.00 累计收益¥0.09 转出 转入 功能 插件 推荐 动态 账单 9月 统计明细 " +
                    "月支出 ¥1191.27 月收入 ¥1755.36 " +
                    "凯 -11.80 得力按动笔芯0.5子弹头学生中性笔替芯 今天 21:37 " +
                    "凯 12.39 退款-商户单号XP2126091913101430960500003083 今天 13:55 " +
                    "发布 选择扣款渠道 按支付宝设置的扣款顺序 招商银行(2836) 中国农业银行(7674) 余额 " +
                    "评论 还没有评论"
            )
        )
    }

    @Test
    fun `微信聊天页-不是账单页`() {
        assertFalse(
            BillPageHeuristics.isBillDetail(
                "浮窗 退出浮窗 切换到发消息 我的账单 支付服务 摇优惠 搜索 设置 " +
                    "使用零钱支付 1.00账单详情 收款方 Sparking 交易状态 支付成功 对方已收款"
            )
        )
    }

    @Test
    fun `微信主界面-不是账单页`() {
        assertFalse(
            BillPageHeuristics.isBillDetail(
                "浮窗 退出浮窗 搜索 更多功能 微信 通讯录 发现 我 正在加载… 视频号 最近 " +
                    "微信支付 个人收款码到账¥0.01"
            )
        )
    }

    @Test
    fun `仅含收益的账户页-不是账单页`() {
        assertFalse(BillPageHeuristics.isBillDetail("累计收益¥0.09 定期金额¥0.00"))
    }

    @Test
    fun `空文本-不是账单页`() {
        assertFalse(BillPageHeuristics.isBillDetail(""))
    }
}
