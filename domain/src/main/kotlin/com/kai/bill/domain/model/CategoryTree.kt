package com.kai.bill.domain.model

/**
 * 分类树节点：一个一级分类 + 它名下的二级分类。
 *
 * 分类表是自引用的扁平结构（`parentId` 为 null 即一级），而记一笔的分类面板、
 * 分类管理页都需要「一级 + 其子分类」的两级视图，因此统一在这里组装，
 * 避免每个页面各写一遍 `groupBy`。
 *
 * @property children 其下二级分类，按 `sortOrder` 升序；可能为空
 */
data class CategoryNode(
    val parent: Category,
    val children: List<Category>
)

/**
 * 把扁平分类列表组装成两级视图。
 *
 * 只把 `parentId == null` 的分类当作一级；`parentId` 指向已不存在分类的孤儿行会被丢弃
 * （正常删除路径不会产生孤儿，这里只是兜底）。
 */
fun List<Category>.toCategoryTree(): List<CategoryNode> {
    val childrenByParent = filter { it.parentId != null }.groupBy { it.parentId }
    return filter { it.parentId == null }
        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        .map { parent ->
            CategoryNode(
                parent = parent,
                children = childrenByParent[parent.id].orEmpty()
                    .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            )
        }
}

/**
 * 二级分类 id → 一级分类 id 的映射（统计上卷用）。
 *
 * 账单存的是用户实际选中的最末级分类 id，而饼图 / 排行展示的是一级维度，
 * 因此聚合后要把子分类的金额合并到父分类。一级分类自身不在映射里，
 * 调用方统一写 `map[categoryId] ?: categoryId` 即可完成上卷。
 */
fun List<Category>.childToParentIdMap(): Map<Long, Long> =
    mapNotNull { category -> category.parentId?.let { category.id to it } }.toMap()

/**
 * 展开某个分类（含自身）及其全部直接子分类 id。
 *
 * 用于「按分类筛选时，选中一级分类等于选中它名下所有二级分类」的语义。
 */
fun List<Category>.categoryIdsWithChildren(rootId: Long): Set<Long> =
    setOf(rootId) + filter { it.parentId == rootId }.map { it.id }
