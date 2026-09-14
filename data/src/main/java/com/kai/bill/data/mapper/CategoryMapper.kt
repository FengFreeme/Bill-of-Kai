package com.kai.bill.data.mapper

import com.kai.bill.core.db.entity.CategoryEntity
import com.kai.bill.domain.model.Category

/** Category Entity ⇄ Domain */
object CategoryMapper {

    fun toDomain(entity: CategoryEntity): Category = Category(
        id = entity.id,
        name = entity.name,
        icon = entity.icon,
        colorHex = entity.colorHex,
        type = entity.type.toDomain(),
        parentId = entity.parentId,
        sortOrder = entity.sortOrder,
        isSystem = entity.isSystem
    )

    fun toEntity(category: Category): CategoryEntity = CategoryEntity(
        id = category.id,
        name = category.name,
        icon = category.icon,
        colorHex = category.colorHex,
        type = category.type.toEntity(),
        parentId = category.parentId,
        sortOrder = category.sortOrder,
        isSystem = category.isSystem
    )
}
