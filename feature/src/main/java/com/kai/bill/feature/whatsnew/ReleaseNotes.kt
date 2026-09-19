package com.kai.bill.feature.whatsnew

/**
 * 一个版本的更新公告。
 *
 * @property version 对应的 `versionName`，必须与 `app/build.gradle.kts` 里的值**逐字一致**
 * @property sections 分段内容（新增 / 修复 / 优化 …），按用户关心的顺序排
 */
data class ReleaseNote(
    val version: String,
    val sections: List<ReleaseNoteSection>
)

/** 公告里的一个分段：小标题 + 若干条目 */
data class ReleaseNoteSection(
    val heading: String,
    val items: List<String>
)

/**
 * 各次更新的公告文案。
 *
 * 只在「装上的版本」与「上次看过并关掉的版本」不一致时，冷启动弹一次
 * （判断与去重见 `KaiPrefs.lastSeenReleaseVersion`）。
 *
 * 三条约定：
 * - **从新到旧**排列，[forVersion] 取第一条命中的；
 * - 版本号必须与 `versionName` 对得上。对不上时**什么都不弹** —— 所以「改了 versionName
 *   却忘了加公告」的表现是「静默无反应」。这是刻意的取舍：宁可少打扰一次，
 *   也不要弹出一份和当前版本对不上的错公告；
 * - 面向用户写：说清「多了什么、修好了什么」，不写实现细节，也不带任何真实姓名、
 *   商户名与金额。
 */
object ReleaseNotes {

    val entries: List<ReleaseNote> = listOf(
        ReleaseNote(
            version = "1.2.0",
            sections = listOf(
                ReleaseNoteSection(
                    heading = "新增",
                    items = listOf(
                        "分类识别：打开账单详情页也能自动识别，为已有账单补上正确分类；没有对应账单时会自动补记一笔",
                        "记完弹确认卡片：可当场改分类或撤销，全程不用跳进 App",
                        "待审核记录：判不准的通知附上建议结果，确认后才成为账单",
                        "转账区分「转入 / 转出」，且不计入收支统计",
                        "预算进度条随使用比例由浅绿渐变到鲜红",
                        "数据备份：导出账单到文件，换机导入即可迁移"
                    )
                ),
                ReleaseNoteSection(
                    heading = "修复",
                    items = listOf(
                        "修复通知记账没有读取或误读取的问题",
                        "金额误识别：卡号尾号、订单号不再被当成金额",
                        "没花的钱被记成支出：失败、提醒、营销这几类文案会被跳过",
                        "分类几乎全落「其他」；同一笔被重复记账；从聊天内容凭空记账",
                        "环形图标签过多，改为只显示大于 5% 的分类，可点击具体分类高亮显示"
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
