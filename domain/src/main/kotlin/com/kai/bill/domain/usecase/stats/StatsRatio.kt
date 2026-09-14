package com.kai.bill.domain.usecase.stats

/**
 * 计算「部分占整体」的比率，取值 0f~1f。
 *
 * 分母为 0（区间内无流水）时返回 0f 而不是 NaN：NaN 传到 UI 后
 * 会让进度条与饼图算出非法尺寸，直接崩在绘制阶段。
 *
 * 统计用例共用此实现，保证饼图与排行榜的分母口径完全一致。
 */
internal fun ratioOf(partCents: Long, totalCents: Long): Float =
    if (totalCents == 0L) 0f else partCents.toFloat() / totalCents.toFloat()
