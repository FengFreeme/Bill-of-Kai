package com.kai.bill.feature.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kai.bill.core.common.money.MoneyFormatter
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.domain.model.stats.CategoryStat

/**
 * 分类排行列表：色点 + 分类名 + 占比条 + 金额 + 笔数。
 *
 * 数据已由 `ObserveCategoryStatsUseCase` 按金额降序排好并算好 [CategoryStat.ratio]，
 * 这里只做渲染，**不重新计算比率** —— 否则容易和饼图用上不同的分母。
 *
 * 占比条用纯色 Box 而非 `LinearProgressIndicator`：后者带自己的最小高度与
 * 主题色，压到 6dp 会出现圆角与颜色都对不上的问题。
 *
 * @param stats 分类统计（按金额降序）
 * @param onItemClick 点击某一项：进入该分类的详情页
 * @param modifier 外部修饰符
 */
@Composable
fun CategoryRankList(
    stats: List<CategoryStat>,
    onItemClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // 与饼图使用同一套渐变色，按金额降序一一对应，保证排行项颜色与扇区颜色一致
    val colors = remember(stats) { gradientSliceColors(stats.size) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        stats.forEachIndexed { index, stat ->
            CategoryRankItem(
                stat = stat,
                accent = colors.getOrElse(index) { AppTheme.color.primary },
                onClick = { onItemClick(stat.categoryId) }
            )
        }
    }
}

@Composable
private fun CategoryRankItem(stat: CategoryStat, accent: Color, onClick: () -> Unit) {
    val ratio = stat.ratio.coerceIn(0f, 1f)

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = stat.categoryName,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.color.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                text = MoneyFormatter.withSymbol(stat.amountCents),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.color.onSurface
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppTheme.color.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = ratio)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent)
            )
        }

        Text(
            text = "${(ratio * 100).toInt()}% · ${stat.billCount} 笔",
            style = AppTheme.typography.labelSmall,
            color = AppTheme.color.onSurfaceVariant
        )
    }
}
