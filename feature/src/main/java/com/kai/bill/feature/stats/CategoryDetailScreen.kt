package com.kai.bill.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.common.money.MoneyFormatter
import com.kai.bill.core.design.component.EmptyState
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.domain.model.Account
import kotlin.math.roundToInt
import com.kai.bill.domain.model.Category
import com.kai.bill.feature.home.components.DailyBillGroup
import com.kai.bill.feature.stats.components.DonutChart
import com.kai.bill.feature.stats.components.DonutSlice
import com.kai.bill.feature.stats.components.gradientSliceColors
import com.kai.bill.feature.stats.components.vividSliceColor

/**
 * 分类详情页：某个一级分类在「本月」的构成与流水。
 *
 * 自上而下：顶栏（返回 / 分类名 / 编辑）→ 顶部 tab（总览 + 各二级分类）
 * → 三列汇总 → 「子分类构成」环形图 → 按日分组的流水。
 *
 * 无状态：只消费 [CategoryDetailUiState]，交互通过回调上抛给 [CategoryDetailViewModel]。
 *
 * 环形图颜色用 [vividSliceColor] 按序取亮色，**不用分类自身的 `colorHex`** ——
 * 二级分类的颜色继承自父分类，同一个一级分类下会是完全相同的色值，画出来无法区分。
 *
 * @param uiState 详情页状态
 * @param categories 全部分类（流水渲染用名称 / 图标映射）
 * @param accounts 全部账户（同上）
 * @param onChildSelected 顶部 tab 切换；null 表示总览
 * @param onEdit 跳分类管理
 * @param onBillClick 点击单条流水（进编辑）
 * @param onBack 返回
 * @param modifier 外部修饰符
 */
@Composable
fun CategoryDetailScreen(
    uiState: CategoryDetailUiState,
    categories: List<Category>,
    accounts: List<Account>,
    onChildSelected: (Long?) -> Unit,
    onEdit: () -> Unit,
    onBillClick: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val accountById = remember(accounts) { accounts.associateBy { it.id } }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "‹",
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.color.onSurface
                )
            }
            Text(
                text = uiState.categoryName.ifBlank { "分类详情" },
                style = AppTheme.typography.titleLarge,
                color = AppTheme.color.onSurface,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "编辑",
                    style = AppTheme.typography.labelLarge,
                    color = AppTheme.color.primary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "tabs") {
                CategoryTabs(
                    children = uiState.childTabs,
                    selectedChildId = uiState.selectedChildId,
                    onSelect = onChildSelected
                )
            }

            item(key = "summary") {
                SummaryRow(
                    totalCents = uiState.totalCents,
                    averageCents = uiState.averageCents,
                    billCount = uiState.billCount
                )
            }

            if (uiState.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        title = "本月还没有记录",
                        subtitle = "这个分类下暂时没有流水"
                    )
                }
            } else if (uiState.composition.isNotEmpty()) {
                item(key = "composition") {
                    ListCard(modifier = Modifier.fillMaxWidth()) {
                        // 垂直留 16dp；水平交给标题（16dp）与图表（4dp）各自控制，
                        // 这样图表标签能更贴近卡片边缘
                        Column(modifier = Modifier.padding(vertical = 16.dp)) {
                            Text(
                                text = if (uiState.selectedChildId == null) "子分类构成" else "构成",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = AppTheme.typography.titleSmall,
                                color = AppTheme.color.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            var selectedIndex by remember(uiState.composition) { mutableIntStateOf(-1) }
                            val slices = remember(uiState.composition) {
                                val colors = gradientSliceColors(uiState.composition.size)
                                uiState.composition.mapIndexed { index, stat ->
                                    DonutSlice(
                                        name = stat.categoryName,
                                        ratio = stat.ratio,
                                        color = colors.getOrElse(index) { vividSliceColor(index) }
                                    )
                                }
                            }
                            val centerTitle: String
                            val centerCaption: String
                            if (selectedIndex in uiState.composition.indices) {
                                val stat = uiState.composition[selectedIndex]
                                centerTitle = MoneyFormatter.plain(stat.amountCents)
                                centerCaption = "${stat.categoryName} ${(stat.ratio * 100).roundToInt()}%"
                            } else {
                                centerTitle = MoneyFormatter.plain(uiState.totalCents)
                                centerCaption = "共支出(元)"
                            }
                            DonutChart(
                                slices = slices,
                                centerTitle = centerTitle,
                                centerCaption = centerCaption,
                                selectedIndex = selectedIndex,
                                onSliceClick = { selectedIndex = if (selectedIndex == it) -1 else it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            uiState.groups.forEach { group ->
                item(key = "group_${group.date}") {
                    DailyBillGroup(
                        group = group,
                        categories = categoryById,
                        accounts = accountById,
                        onBillClick = onBillClick
                    )
                }
            }
        }
    }
}

/** 顶部 tab：总览 + 各二级分类；选中项文字与下划线取主题色 */
@Composable
private fun CategoryTabs(
    children: List<Category>,
    selectedChildId: Long?,
    onSelect: (Long?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        CategoryTab(
            text = "总览",
            selected = selectedChildId == null,
            onClick = { onSelect(null) }
        )
        children.forEach { child ->
            CategoryTab(
                text = child.name,
                selected = child.id == selectedChildId,
                onClick = { onSelect(child.id) }
            )
        }
    }
}

@Composable
private fun CategoryTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            style = AppTheme.typography.titleSmall,
            color = if (selected) AppTheme.color.primary else AppTheme.color.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) AppTheme.color.primary else AppTheme.color.surface)
        )
    }
}

/** 三列汇总：总支出 / 单笔均值 / 共计 */
@Composable
private fun SummaryRow(totalCents: Long, averageCents: Long, billCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryCell(
            caption = "总支出(元)",
            value = MoneyFormatter.plain(totalCents),
            modifier = Modifier.weight(1f)
        )
        SummaryDivider()
        SummaryCell(
            caption = "单笔均值(元)",
            value = MoneyFormatter.plain(averageCents),
            modifier = Modifier.weight(1f)
        )
        SummaryDivider()
        SummaryCell(
            caption = "共计",
            value = "$billCount 笔",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCell(caption: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = caption,
            style = AppTheme.typography.labelMedium,
            color = AppTheme.color.onSurfaceVariant
        )
        Text(
            text = value,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.color.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(AppTheme.color.outlineVariant)
    )
}

/**
 * 分类详情的路由，负责接上 ViewModel。
 *
 * @param categoryId 目标一级分类 ID
 * @param onBack 返回
 * @param onEdit 跳分类管理
 * @param onBillClick 点击流水进编辑
 * @param modifier 外部修饰符
 * @param viewModel 由 Hilt 注入
 */
@Composable
fun CategoryDetailRoute(
    categoryId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onBillClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    LaunchedEffect(categoryId) {
        viewModel.load(categoryId)
    }

    CategoryDetailScreen(
        uiState = uiState,
        categories = categories,
        accounts = accounts,
        onChildSelected = viewModel::onChildSelected,
        onEdit = onEdit,
        onBillClick = onBillClick,
        onBack = onBack,
        modifier = modifier
    )
}

@Preview(name = "分类详情-浅色", showBackground = true)
@Composable
private fun CategoryDetailScreenLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        CategoryDetailScreen(
            uiState = CategoryDetailUiState(categoryName = "餐饮"),
            categories = emptyList(),
            accounts = emptyList(),
            onChildSelected = {},
            onEdit = {},
            onBillClick = {},
            onBack = {}
        )
    }
}
