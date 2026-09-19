package com.kai.bill.data.presets

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预设分类与预设分类词的**一致性**校验。
 *
 * 两者分文件维护、靠 id 关联，改动时最容易犯的错是「指向一个不存在的分类」或
 * 「方向写反」—— 这类错误的后果不是崩溃，而是**静默失效**：词永远匹配不上，
 * 账单一路落到「其他」兜底，而且没有任何日志会告诉你。
 * 这三条断言把这类错误挡在编译后的第一次跑测里。
 */
class DefaultCategoryKeywordPresetTest {

    private val categories = DefaultCategories.all
    private val byId = categories.associateBy { it.id }
    private val keywords = DefaultCategoryKeywords.all

    @Test
    fun `分类词指向的分类必须存在`() {
        val orphan = keywords.filterNot { it.categoryId in byId }
        assertTrue(
            "分类词指向了不存在的分类 id：${orphan.map { it.keyword to it.categoryId }}",
            orphan.isEmpty()
        )
    }

    @Test
    fun `分类词声明的方向必须与分类本身的方向一致`() {
        // 不一致的词是**永远匹配不上**的：S3 会先按账单类型把词表收窄到同一方向，
        // 一个声明为「支出」的词根本不会出现在「收入」的候选里。
        val mismatched = keywords.filter { keyword ->
            val categoryType = byId[keyword.categoryId]?.type
            categoryType != null && categoryType != keyword.direction
        }
        assertTrue(
            "方向与分类类型不一致（这些词永远不会生效）：" +
                mismatched.map { "${it.keyword} 声明=${it.direction} → ${it.categoryId}(${byId[it.categoryId]?.type})" },
            mismatched.isEmpty()
        )
    }

    @Test
    fun `预置分类 id 不能重复`() {
        val duplicated = categories.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("预置分类 id 重复：$duplicated", duplicated.isEmpty())
    }

    @Test
    fun `二级分类的 parentId 必须指向存在的一级分类`() {
        val ids = categories.map { it.id }.toSet()
        val orphan = categories.filter { it.parentId != null && it.parentId !in ids }
        assertTrue(
            "二级分类指向了不存在的父分类：${orphan.map { it.name to it.parentId }}",
            orphan.isEmpty()
        )
    }
}
