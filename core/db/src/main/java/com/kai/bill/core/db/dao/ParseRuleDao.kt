package com.kai.bill.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kai.bill.core.db.entity.ParseRuleEntity
import com.kai.bill.core.db.entity.SourceType
import kotlinx.coroutines.flow.Flow

/**
 * 解析规则表的数据访问对象。
 *
 * 规则按 `priority` **降序**返回：RuleEngine 取到第一条命中的规则即停止，
 * 由 SQL 排好序可以让引擎省掉一次内存排序，也避免「引擎排序」与「SQL 排序」口径不一致。
 */
@Dao
interface ParseRuleDao {

    /** 观察全部规则（含已禁用），用于规则管理页 */
    @Query("SELECT * FROM parse_rule ORDER BY priority DESC, id ASC")
    fun observeAll(): Flow<List<ParseRuleEntity>>

    /** 观察已启用的规则 */
    @Query(
        """
        SELECT * FROM parse_rule
        WHERE enabled = 1
        ORDER BY priority DESC, id ASC
        """
    )
    fun observeEnabled(): Flow<List<ParseRuleEntity>>

    /**
     * 观察某个来源下已启用的规则，优先级降序。
     *
     * @param source 来源；匹配前先按来源过滤，避免上千条规则全量参与正则回溯
     */
    @Query(
        """
        SELECT * FROM parse_rule
        WHERE source = :source AND enabled = 1
        ORDER BY priority DESC, id ASC
        """
    )
    fun observeEnabledBySource(source: SourceType): Flow<List<ParseRuleEntity>>

    @Upsert
    suspend fun upsert(rule: ParseRuleEntity)

    /**
     * 批量写入预置规则。
     *
     * **预置规则的写入契约**：规则同样**必须带固定 id**。带 id 时 `@Upsert` 会按主键
     * 更新既有行 —— 这正是「规则热更新」想要的：改版后只需重新播种一次，
     * 用户手工调过的 `enabled`、`priority` 之类字段也能被新版本覆盖回来。
     * 若用默认的 `id = 0`，则退化为纯插入，重复播种会长出一堆重复规则。
     */
    @Upsert
    suspend fun upsertAll(rules: List<ParseRuleEntity>)

    @Query("DELETE FROM parse_rule WHERE id = :id")
    suspend fun deleteById(id: Long)
}
