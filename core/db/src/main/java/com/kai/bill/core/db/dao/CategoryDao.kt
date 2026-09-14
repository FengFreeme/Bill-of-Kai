package com.kai.bill.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kai.bill.core.db.entity.BillType
import com.kai.bill.core.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 分类表的数据访问对象。
 *
 * **预置数据的写入契约（重要）**：`data/presets` 中的预置分类**必须显式指定固定 id**，
 * 形如 1、2、3……，并在 Room 的 `onCreate` 回调中一次性写入。
 *
 * 为什么必须指定 id：本表主键是 `autoGenerate` 的，若传默认的 `id = 0`，
 * SQLite 每次都会分配新的自增值，`IGNORE` 就完全失效 —— 每启动一次就长出一堆同名分类。
 * 反过来，预置数据占用 1..N 的小 id 后，用户新建的分类会从 N+1 开始自增，不会撞号。
 */
@Dao
interface CategoryDao {

    /** 观察全部分类，按排序号升序 */
    @Query("SELECT * FROM category ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    /**
     * 观察指定账单类型的分类（记一笔时按支出/收入切换分类面板）。
     */
    @Query(
        """
        SELECT * FROM category
        WHERE type = :type
        ORDER BY sortOrder ASC, id ASC
        """
    )
    fun observeByType(type: BillType): Flow<List<CategoryEntity>>

    /**
     * 观察一级分类（全部账单类型）。
     *
     * `parentId IS NULL` 即一级分类；二级分类会随 [observeByType] 一并返回，
     * 由上层组装成「一级 + 其子分类」的两级视图。
     */
    @Query(
        """
        SELECT * FROM category
        WHERE parentId IS NULL
        ORDER BY sortOrder ASC, id ASC
        """
    )
    fun observeTopLevel(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    /** 批量写入预置分类；已存在的行返回 -1，不影响其它行 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(category: CategoryEntity)

    /**
     * 仅刷新预置分类的图标列（emoji → Ionicons key 的迁移用）。
     *
     * 不触碰 name/colorHex 等字段，避免覆盖用户对预置分类的改名/配色。
     * 预置分类占用固定 id，按 id 精确定位即可。
     */
    @Query("UPDATE category SET icon = :icon WHERE id = :id")
    suspend fun refreshIcon(id: Long, icon: String?)

    /**
     * 删除分类。
     *
     * 带 `isSystem = 0` 条件：预置分类删掉后「记一笔」会出现空分类面板，
     * 与其在应用层做校验，不如在 SQL 层直接禁掉，任何调用路径都绕不过去。
     */
    @Query("DELETE FROM category WHERE id = :id AND isSystem = 0")
    suspend fun deleteIfCustom(id: Long)
}
