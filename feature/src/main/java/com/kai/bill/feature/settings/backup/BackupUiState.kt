package com.kai.bill.feature.settings.backup

/**
 * 数据备份页状态。
 *
 * 导出/导入都是一次性动作，因此不设「进度百分比」，只用 [isWorking] 禁用按钮防重复点击。
 *
 * @property isWorking 是否正在执行导出或导入
 * @property message 最近一次操作的结果提示；null 表示无提示
 * @property isError [message] 是否为失败提示（决定用警示色还是普通色）
 */
data class BackupUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)
