package com.kai.bill.feature.settings.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.toCategoryTree
import com.kai.bill.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 用户新建的一级分类默认色；与预置的灰(#9AA5B1)错开一点，便于区分自建与预置 */
private const val NEW_CATEGORY_COLOR = "#8A94A6"

/**
 * 分类管理页 ViewModel。
 *
 * 只做三件事：新增（一级 / 二级）、改名、删除。排序号在同级末尾追加，不做拖拽排序。
 *
 * 两条保护规则（都在本类里拦，UI 不必重复判断）：
 * - 预置分类（`isSystem`）不可删 —— DAO 的 `deleteIfCustom` 已带 `isSystem = 0` 条件，这里再提示一次；
 * - **名下还有二级分类的一级不可删** —— 直接删父会留下一批 `parentId` 指向空档的孤儿行，
 *   而分类表没有外键级联，孤儿会静默掉出树视图、却又仍被账单引用，比拦下来更难收拾。
 *
 * @property categoryRepository 分类仓储
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryManageViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val type = MutableStateFlow(BillType.EXPENSE)
    /** 已展开的一级分类 ID；进入页面时默认全展开，之后完全听用户的 */
    private val expandedIds = MutableStateFlow<Set<Long>>(emptySet())
    private var expandedInitialized = false
    private val editing = MutableStateFlow<CategoryEditTarget?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val tree = type.flatMapLatest { t ->
        categoryRepository.observeAll().map { all ->
            all.filter { it.type == t }.toCategoryTree()
        }
    }

    val uiState: StateFlow<CategoryManageUiState> = combine(
        tree,
        type,
        expandedIds,
        editing,
        errorMessage
    ) { nodes, t, expanded, edit, error ->
        CategoryManageUiState(
        type = t,
        tree = nodes,
        expandedIds = expanded,
        editing = edit,
        errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CategoryManageUiState()
    )

    init {
        // 默认展开启用二级的一级分类，仅在首次拿到数据时初始化一次
        viewModelScope.launch {
            tree.collect { nodes ->
                if (!expandedInitialized) {
                    expandedInitialized = true
                    expandedIds.value = nodes
                        .filter { it.children.isNotEmpty() }
                        .map { it.parent.id }
                        .toSet()
                }
            }
        }
    }

    // ---- 状态切换 ----

    fun onTypeChange(newType: BillType) {
        type.value = newType
        expandedInitialized = false
        expandedIds.value = emptySet()
        editing.value = null
        errorMessage.value = null
    }

    fun toggleExpand(parentId: Long) {
        expandedIds.value = if (parentId in expandedIds.value) {
            expandedIds.value - parentId
        } else {
            expandedIds.value + parentId
        }
    }

    // ---- 编辑对话框 ----

    fun onRequestAddParent() {
        editing.value = CategoryEditTarget.NewParent(type.value)
    }

    fun onRequestAddChild(parentId: Long) {
        val parentName = uiState.value.tree
            .firstOrNull { it.parent.id == parentId }
            ?.parent
            ?.name
            .orEmpty()
        editing.value = CategoryEditTarget.NewChild(parentId, parentName)
    }

    fun onRequestRename(category: Category) {
        editing.value = CategoryEditTarget.Rename(category)
    }

    fun dismissEdit() {
        editing.value = null
    }

    /** 提交对话框内容；空名直接拒绝，不弹 Toast 之外的任何东西 */
    fun submitEdit(name: String) {
        val target = editing.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            errorMessage.value = "分类名不能为空"
            return
        }
        viewModelScope.launch {
            runCatching {
                when (target) {
                    is CategoryEditTarget.NewParent -> insertParent(trimmed, target.type)
                    is CategoryEditTarget.NewChild -> insertChild(trimmed, target.parentId)
                    is CategoryEditTarget.Rename ->
                        categoryRepository.update(target.category.copy(name = trimmed))
                }
            }.onSuccess {
                editing.value = null
                errorMessage.value = null
            }.onFailure { e ->
                errorMessage.value = e.message ?: "保存失败"
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }

    // ---- 删除 ----

    fun delete(category: Category) {
        viewModelScope.launch {
            if (category.isSystem) {
                errorMessage.value = "预置分类不可删除"
                return@launch
            }
            val hasChildren = uiState.value.tree
                .any { it.parent.id == category.id && it.children.isNotEmpty() }
            if (hasChildren) {
                errorMessage.value = "请先删除「${category.name}」名下的二级分类"
                return@launch
            }
            runCatching { categoryRepository.deleteIfCustom(category.id) }
                .onFailure { e -> errorMessage.value = e.message ?: "删除失败" }
        }
    }

    // ---- 私有写入 ----

    private suspend fun insertParent(name: String, type: BillType) {
        categoryRepository.insert(
            Category(
                name = name,
                icon = null,
                colorHex = NEW_CATEGORY_COLOR,
                type = type,
                parentId = null,
                sortOrder = nextSortOrder(type, parentId = null),
                isSystem = false
            )
        )
    }

    private suspend fun insertChild(name: String, parentId: Long) {
        val parent = categoryRepository.getById(parentId) ?: return
        categoryRepository.insert(
            Category(
                name = name,
                icon = null,
                // 子分类沿用父色，饼图下钻时色系连贯
                colorHex = parent.colorHex,
                type = parent.type,
                parentId = parentId,
                sortOrder = nextSortOrder(parent.type, parentId),
                isSystem = false
            )
        )
    }

    /** 同级末尾追加：取现有同级最大排序号 +1 */
    private suspend fun nextSortOrder(type: BillType, parentId: Long?): Int {
        val siblings = categoryRepository.observeAll()
            .first()
            .filter { it.type == type && it.parentId == parentId }
        return (siblings.maxOfOrNull { it.sortOrder } ?: 0) + 1
    }
}
