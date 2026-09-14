package com.kai.bill.data.repository

import com.kai.bill.core.db.dao.CategoryDao
import com.kai.bill.data.mapper.CategoryMapper
import com.kai.bill.data.mapper.toEntity
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** [CategoryRepository] 的 Room 实现。注入 [CategoryDao]，经 [CategoryMapper] 转换。 */
@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { it.map(CategoryMapper::toDomain) }

    override fun observeByType(type: BillType): Flow<List<Category>> =
        categoryDao.observeByType(type.toEntity()).map { it.map(CategoryMapper::toDomain) }

    override suspend fun getById(id: Long): Category? =
        categoryDao.getById(id)?.let(CategoryMapper::toDomain)

    override suspend fun insert(category: Category): Long =
        categoryDao.insert(CategoryMapper.toEntity(category))

    override suspend fun insertAll(categories: List<Category>): List<Long> =
        categoryDao.insertAll(categories.map(CategoryMapper::toEntity))

    override suspend fun update(category: Category) =
        categoryDao.update(CategoryMapper.toEntity(category))

    override suspend fun refreshIcon(id: Long, icon: String?) =
        categoryDao.refreshIcon(id, icon)

    override suspend fun deleteIfCustom(id: Long) = categoryDao.deleteIfCustom(id)
}
