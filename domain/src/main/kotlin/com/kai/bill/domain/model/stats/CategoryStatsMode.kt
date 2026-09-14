package com.kai.bill.domain.model.stats

/**
 * 分类统计的聚合维度。
 *
 * - [PARENT]：按一级分类聚合（扇区 = 一级分类）；
 * - [CHILD]：按二级分类聚合（扇区 = 子分类）。
 *
 * 统计页通过「主分类 / 子分类」切换，详情页固定为 [CHILD]。
 */
enum class CategoryStatsMode {
    PARENT,
    CHILD
}
