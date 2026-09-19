package com.kai.bill.domain.model

/**
 * 数据导入结果统计。
 *
 * 把「新增」与「跳过」分开报，用户才能判断这次导入是否如预期：
 * 反复导入同一份备份时，应该是「0 新增 + 全部跳过」，而不是报错。
 *
 * @property billsImported 新增成功的账单条数
 * @property billsSkipped 因重复或分类缺失被跳过的账单条数
 * @property categoriesCreated 新建的分类数（本机没有的才会建）
 * @property accountsCreated 新建的账户数
 * @property budgetsRestored 恢复的预算条数
 */
data class ImportSummary(
    val billsImported: Int,
    val billsSkipped: Int,
    val categoriesCreated: Int,
    val accountsCreated: Int,
    val budgetsRestored: Int
) {

    /** 备份文件里一共涉及多少条账单 */
    val billsTotal: Int
        get() = billsImported + billsSkipped
}
