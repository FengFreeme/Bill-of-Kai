package com.kai.bill.feature.settings.category

import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.CategoryNode

/**
 * 分类管理页的 UI 状态。
 *
 * 分类是一棵两层树（一级 + 其下二级），这里只保存「当前类型」下的那部分，
 * 由 ViewModel 按 [type] 过滤后组装。
 *
 * @property type 当前编辑的账单类型（支出 / 收入）；转账分类固定两项，不开放管理
 * @property tree 该类型下的两级分类
 * @property expandedIds 已展开的一级分类 ID
 * @property editing 正在编辑的目标（新增 / 改名）；null 表示无对话框
 * @property errorMessage 最近一次操作失败提示
 */
data class CategoryManageUiState(
    val type: BillType = BillType.EXPENSE,
    val tree: List<CategoryNode> = emptyList(),
    val expandedIds: Set<Long> = emptySet(),
    val editing: CategoryEditTarget? = null,
    val errorMessage: String? = null
)

/**
 * 分类编辑目标：决定弹出的对话框是「新增」还是「改名」，以及写入哪个父节点。
 */
sealed interface CategoryEditTarget {

    /** 新增一级分类 */
    data class NewParent(val type: BillType) : CategoryEditTarget

    /** 在某个一级分类下新增二级分类；[parentName] 仅用于对话框文案 */
    data class NewChild(val parentId: Long, val parentName: String) : CategoryEditTarget

    /** 给已有分类改名 */
    data class Rename(val category: Category) : CategoryEditTarget
}
