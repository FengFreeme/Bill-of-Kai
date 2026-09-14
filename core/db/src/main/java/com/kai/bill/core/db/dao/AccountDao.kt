package com.kai.bill.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kai.bill.core.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * 账户表的数据访问对象。
 *
 * 账户**只归档不删除**（见 [com.kai.bill.core.db.entity.AccountEntity.isArchived]）：
 * 历史流水通过 `accountId` 关联账户，物理删除会让老账单变成孤儿数据。
 * 这里的 [deleteById] 仅用于清理误建的账户。
 *
 * **预置数据的写入契约**：与 [CategoryDao] 相同 —— 预置账户必须使用固定的小 id（1..N）
 * 且在 `onCreate` 中写入。若用默认的 `id = 0`，`IGNORE` 会因自增主键失效而重复插入。
 */
@Dao
interface AccountDao {

    /** 观察全部账户（含归档），用于账户管理页 */
    @Query("SELECT * FROM account ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    /** 观察未归档账户，用于「记一笔」的账户选择器 */
    @Query(
        """
        SELECT * FROM account
        WHERE isArchived = 0
        ORDER BY sortOrder ASC, id ASC
        """
    )
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: AccountEntity): Long

    /** 批量写入预置账户；已存在的行返回 -1 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(accounts: List<AccountEntity>): List<Long>

    @Update
    suspend fun update(account: AccountEntity)

    /**
     * 仅刷新预置账户的图标列（emoji → Ionicons key 的迁移用）。
     *
     * 不触碰 name/type 等字段，避免覆盖用户对预置账户的改名。
     * 预置账户占用固定 id，按 id 精确定位即可。
     */
    @Query("UPDATE account SET icon = :icon WHERE id = :id")
    suspend fun refreshIcon(id: Long, icon: String?)

    @Query("DELETE FROM account WHERE id = :id")
    suspend fun deleteById(id: Long)
}
