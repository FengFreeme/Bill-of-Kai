package com.kai.bill.feature.stats.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.domain.model.stats.CategoryStat
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.pieModel
import com.patrykandpatrick.vico.compose.pie.rememberPieChart

/**
 * 分类构成饼图（Vico）。
 *
 * 扇区颜色取分类自身的 `colorHex`，与下方 [CategoryRankList] 的色点同源，
 * 保证「看图例找扇区」时颜色能对上。
 *
 * 金额换算成**元**入图：分位数值可达数百万，交给图表内部转 Float 会丢精度。
 *
 * 不画扇区内标签：分类色是用户可编辑的，浅色扇区配白字会看不清，
 * 与其做对比度自适应，不如让下方图例统一承担「名称 + 金额 + 占比」。
 *
 * @param stats 分类统计（按金额降序）
 * @param modifier 外部修饰符
 */
@Composable
fun CategoryPieChart(
    stats: List<CategoryStat>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { PieChartModelProducer() }

    // 占比为 0 的分类画出来既看不见、又会挤占有限扇区，直接过滤
    val visible = remember(stats) { stats.filter { it.amountCents > 0L } }

    LaunchedEffect(visible) {
        modelProducer.runTransaction {
            pieModel { series(visible.map { it.amountCents / 100.0 }) }
        }
    }

    val fallback = AppTheme.color.primary
    val slices = remember(visible, fallback) {
        visible.map { stat ->
            val color = runCatching { Color(android.graphics.Color.parseColor(stat.colorHex)) }
                .getOrDefault(fallback)
            PieChart.Slice(fill = Fill(color))
        }
    }

    PieChartHost(
        chart = rememberPieChart(
            sliceProvider = remember(slices) { PieChart.SliceProvider.series(slices) }
        ),
        modelProducer = modelProducer,
        modifier = modifier
    )
}
