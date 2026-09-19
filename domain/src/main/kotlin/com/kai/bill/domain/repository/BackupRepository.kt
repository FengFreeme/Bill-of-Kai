package com.kai.bill.domain.repository

import com.kai.bill.domain.model.ImportSummary

/**
 * 数据备份仓储 —— 整库导出为文本 / 从文本导入，用于跨设备迁移。
 *
 * **为什么关联用「名称」而不是 id**：
 * 账单里的 `categoryId` / `accountId` 是本机自增主键，换一台手机后
 * 同样一个 id 可能指向完全不同的分类或账户，照搬过去必然错乱。
 * 因此导出时把关联字段翻译成名称，导入时再按名称找回或新建。
 *
 * **为什么导入是幂等的**：
 * 账单表 `dedupHash` 上有唯一索引，重复的账单插入会被静默跳过，
 * 所以同一份备份可以反复导入而不会产生重复数据。
 */
interface BackupRepository {

    /**
     * 导出全部数据（账单 + 分类 + 账户 + 预算）为 JSON 文本。
     *
     * @return 可直接写入文件的 JSON 字符串
     */
    suspend fun export(): String

    /**
     * 从 JSON 文本导入，按名称重建关联并合并去重。
     *
     * @param json [export] 产出的文本
     * @return 本次导入的统计结果
     * @throws java.io.IOException 文件格式不正确、或备份版本高于当前 App 支持
     */
    suspend fun import(json: String): ImportSummary
}
