package com.kai.bill.domain.repository

import com.kai.bill.domain.model.Account
import kotlinx.coroutines.flow.Flow

/**
 * 账户仓储。
 *
 * 账户是统计的第二维度。注意预算**不按账户分设**，因此本接口不提供任何预算相关方法。
 */
interface AccountRepository {

    /**
     * 观察全部账户（含已归档）。
     *
     * @return 按排序号升序排列的账户流
     */
    fun observeAll(): Flow<List<Account>>

    /**
     * 观察未归档的账户。
     *
     * 与 [observeAll] 分开提供：`记一笔` 的账户选择器只该出现活跃账户，
     * 而账户管理页需要看到全部（否则归档账户将无法恢复）。
     *
     * @return 按排序号升序排列的活跃账户流
     */
    fun observeActive(): Flow<List<Account>>

    /**
     * 一次性读取单个账户。
     *
     * @param id 账户主键
     * @return 对应账户；不存在返回 null
     */
    suspend fun getById(id: Long): Account?

    /**
     * 新增一个账户。
     *
     * @param account 待新增账户；[Account.id] 传 0 由数据库自增
     * @return 新增后的主键；返回 -1 表示已存在同主键记录，未插入
     */
    suspend fun insert(account: Account): Long

    /**
     * 批量写入账户，用于预置数据播种。
     *
     * NOTE: 与分类相同，预置账户**必须显式指定固定 id**，否则重复播种会产生重复账户。
     *
     * @param accounts 待写入的账户
     * @return 每行对应的主键；值为 -1 表示该行被跳过
     */
    suspend fun insertAll(accounts: List<Account>): List<Long>

    /**
     * 更新一个账户（归档 / 改名 / 调序都走这里）。
     *
     * @param account 待更新账户，[Account.id] 必须非 0
     */
    suspend fun update(account: Account)

    /**
     * 仅刷新指定账户的图标列（用于 emoji → Ionicons key 的冷启动迁移）。
     *
     * 不改动 name/type 等用户可编辑字段。
     *
     * @param id 账户主键
     * @param icon 新的图标标识（key）
     */
    suspend fun refreshIcon(id: Long, icon: String?)

    /**
     * 物理删除一个账户。
     *
     * 仅用于清理误建的账户 —— 正常下线请用 `update` 把 [Account.isArchived] 置为 true，
     * 否则历史账单会因为找不到账户而变成孤儿数据。
     *
     * @param id 账户主键
     */
    suspend fun deleteById(id: Long)
}
