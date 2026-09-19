package com.kai.bill.feature.whatsnew

/**
 * 一个版本的更新公告。
 *
 * @property version 对应的 `versionName`，必须与 `app/build.gradle.kts` **逐字一致**；
 *         [ReleaseNotes.forVersion] 是按它精确匹配的
 * @property lead 一句话摘要。放最上面：用户先看这一句，决定要不要往下读
 * @property sections 分段内容（新增 / 修复 / 口径调整 …），按用户关心的顺序排
 */
data class ReleaseNote(
    val version: String,
    val lead: String,
    val sections: List<ReleaseNoteSection>
)

/**
 * 公告里的一个分段：小标题 + 若干条目。
 *
 * @property heading 小标题
 * @property items 条目；每行一条，不写实现细节
 */
data class ReleaseNoteSection(
    val heading: String,
    val items: List<String>
)

/**
 * 各次更新的公告文案。
 *
 * 只在「装上的版本」与「上次看过并关掉的版本」不一致时，冷启动弹一次
 * （判断见 `MainActivity`，去重见 `KaiPrefs.lastSeenReleaseVersion`）。
 *
 * 三条约定：
 * - **从新到旧**排列，[forVersion] 取第一条命中的；
 * - 版本号必须与 `versionName` 对得上。对不上时**什么都不弹** ——
 *   所以「改了 versionName 却忘了加公告」的表现是「静默无反应」。这是刻意的取舍：
 *   宁可少打扰一次，也不要弹出一份和当前版本对不上的错公告；
 * - **面向用户写**：说清「多了什么、修好了什么」，不写实现细节，
 *   也不带任何真实姓名、商户名与金额（页面文案本身就带这些，一律转成通用说法）。
 */
object ReleaseNotes {

    val entries: List<ReleaseNote> = listOf(
        ReleaseNote(
            version = "1.2.0",
            lead = "这次的重点是把分类认准，并且让每一次自动记账都看得见、改得动。",
            sections = listOf(
                ReleaseNoteSection(
                    heading = "新增",
                    items = listOf(
                        "分类识别：打开账单详情页也能自动识别，为已有账单补上正确分类；页面上有这笔消费、但没收到通知时，会自动补记一笔",
                        "记账确认卡片：记完自动弹出，可当场改分类（直接在卡片里选，不用跳进 App）或撤销这一笔",
                        "识别结果提示：已经记录过的账单会提示「无需重复记录」，判不准的会提示已加入待确认",
                        "待审核记录：判不准的通知会附上建议结果，确认后才成为账单",
                        "转账区分「转入 / 转出」，分开记录",
                        "预算进度条随使用比例由浅绿渐变到鲜红，一眼看出还剩多少余地",
                        "数据备份：账单可导出到文件，换机导入即可迁移",
                        "采集状态可自查：能看到通知与分类识别是否正常工作、最近一次处理结果是什么"
                    )
                ),
                ReleaseNoteSection(
                    heading = "修复",
                    items = listOf(
                        "转出被记成收入：转账页面两边的文案都带「收款」二字，现在会区分是你收还是对方收",
                        "金额误识别：卡号尾号、订单号不再被当成金额",
                        "没花的钱被记成支出：支付失败、账单提醒、营销优惠这几类文案会被跳过",
                        "从聊天内容凭空记账；账户页余额被当成一笔交易",
                        "同一笔被重复记账",
                        "分类几乎全落「其他」",
                        "环形图标签过多：只显示占比大于 5% 的分类，可点选某个分类高亮查看"
                    )
                ),
                ReleaseNoteSection(
                    heading = "口径调整",
                    items = listOf(
                        "个人转账不再计入「收入」统计：收到一笔转账会归为「转账 · 转入」。确实要算收入时，可在编辑页打开「计入统计」",
                        "转账细分为「转出 / 转入」两个子分类（挂在「转账」下），历史「转账」账单不受影响"
                    )
                )
            )
        )
    )

    /**
     * 取某个版本的公告。
     *
     * @param version 当前安装包的 `versionName`
     * @return 对应公告；**没有对应条目时返回 null**，调用方应当什么都不做
     */
    fun forVersion(version: String): ReleaseNote? = entries.firstOrNull { it.version == version }
}
