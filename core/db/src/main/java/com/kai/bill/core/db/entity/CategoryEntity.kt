package com.kai.bill.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 分类表。
 *
 * @property name 分类名，如「餐饮」
 * @property icon 图标标识（存名称而非资源 ID，便于将来支持用户自定义）
 * @property colorHex 分类色，形如 `#F2775F`；存字符串而非 int，方便热更新与跨端表达
 * @property type 该分类适用的账单类型，记一笔时按支出/收入分别展示
 * @property parentId 父分类 ID，**预留二级分类**；当前 UI 只用一级，为 null 即顶级
 * @property sortOrder 排序号，升序
 * @property isSystem 是否系统预置；预置分类不允许删除，避免用户把默认分类删空
 */
@Entity(
    tableName = "category",
    indices = [Index("parentId"), Index("type")]
)
data class CategoryEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val icon: String?,

    val colorHex: String,

    val type: BillType,

    val parentId: Long?,

    val sortOrder: Int,

    val isSystem: Boolean
)
