package com.kai.bill.domain.model

/**
 * 分类 —— 统计的第一维度（第二维度是账户）。
 *
 * @property id 主键，0 表示尚未落库
 * @property name 分类名，如「餐饮」
 * @property icon 图标标识（存名称而非资源 ID，便于将来支持用户自定义）
 * @property colorHex 分类色，形如 `#F2775F`；用字符串而非 int，方便热更新与跨端表达
 * @property type 适用的账单类型；记一笔时按支出 / 收入分别展示分类面板
 * @property parentId 父分类 ID；为 null 即一级分类，非 null 指向其所属的一级分类。
 *   两级视图与统计上卷分别见 `CategoryTree.kt` 的 toCategoryTree / childToParentIdMap
 * @property sortOrder 排序号，升序
 * @property isSystem 是否系统预置；预置分类不允许删除，避免用户把默认分类删空
 */
data class Category(

    val id: Long = 0,

    val name: String,

    val icon: String?,

    val colorHex: String,

    val type: BillType,

    val parentId: Long?,

    val sortOrder: Int,

    val isSystem: Boolean
)
