package com.kai.bill.domain.repository

import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * 分类仓储。
 *
 * 分类是统计的第一维度，也是「记一笔」页面的必选项，因此读取非常频繁；
 * 全部以 [Flow] 暴露，保证在分类管理页增删后，记一笔页的分类面板立即同步。
 */
interface CategoryRepository {

    /**
     * 观察全部分类。
     *
     * @return 按排序号升序排列的分类流
     */
    fun observeAll(): Flow<List<Category>>

    /**
     * 观察指定账单类型的分类。
     *
     * @param type 账单类型；记一笔页在「支出 / 收入」之间切换时用它取对应面板
     * @return 该类型下的分类流，按排序号升序
     */
    fun observeByType(type: BillType): Flow<List<Category>>

    /**
     * 一次性读取单个分类。
     *
     * @param id 分类主键
     * @return 对应分类；不存在返回 null
     */
    suspend fun getById(id: Long): Category?

    /**
     * 新增一个分类。
     *
     * @param category 待新增分类；[Category.id] 传 0 由数据库自增
     * @return 新增后的主键；**返回 -1 表示已存在同主键记录，未插入**
     */
    suspend fun insert(category: Category): Long

    /**
     * 批量写入分类，用于预置数据播种。
     *
     * NOTE: 预置分类**必须显式指定固定 id**，否则自增主键会让「重复启动」长出多份同名分类。
     *
     * @param categories 待写入的分类
     * @return 每行对应的主键；值为 -1 表示该行因主键冲突被跳过
     */
    suspend fun insertAll(categories: List<Category>): List<Long>

    /**
     * 更新一个分类；以主键匹配。
     *
     * @param category 待更新分类，[Category.id] 必须非 0
     */
    suspend fun update(category: Category)

    /**
     * 仅刷新指定分类的图标列（用于 emoji → Ionicons key 的冷启动迁移）。
     *
     * 不改动 name/colorHex 等用户可编辑字段。
     *
     * @param id 分类主键
     * @param icon 新的图标标识（key）
     */
    suspend fun refreshIcon(id: Long, icon: String?)

    /**
     * 删除分类，但**系统预置分类删不掉**。
     *
     * 保护逻辑在 SQL 层（`isSystem = 0` 条件）而非这里：预置分类被删空后
     * 「记一笔」会出现空白分类面板，这种事故不该依赖调用方自觉。
     *
     * @param id 分类主键
     */
    suspend fun deleteIfCustom(id: Long)
}
