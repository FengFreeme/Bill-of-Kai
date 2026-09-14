package com.kai.bill.domain.repository

import com.kai.bill.domain.model.ParseRule
import com.kai.bill.domain.model.SourceType
import kotlinx.coroutines.flow.Flow

/**
 * 解析规则仓储。
 *
 * 规则**入库而非硬编码**，因此本接口是「不发包修复解析」的关键：
 * 支付宝 / 微信改版时，只需更新规则数据，App 无需重新编译。
 */
interface ParseRuleRepository {

    /**
     * 观察全部规则（含已禁用），用于规则管理页。
     *
     * @return 按优先级**降序**排列的规则流
     */
    fun observeAll(): Flow<List<ParseRule>>

    /**
     * 观察已启用的规则。
     *
     * @return 按优先级降序排列的规则流
     */
    fun observeEnabled(): Flow<List<ParseRule>>

    /**
     * 观察某个来源下已启用的规则。
     *
     * 按来源过滤是性能关键：规则可能积累到上千条，
     * 若每次解析都把全部规则拿去跑正则，回溯开销会直接卡住入库流水线。
     *
     * @param source 账单来源
     * @return 该来源下按优先级降序排列的启用规则流
     */
    fun observeEnabledBySource(source: SourceType): Flow<List<ParseRule>>

    /**
     * 新增或更新一条规则；以主键匹配。
     *
     * @param rule 待保存规则；[ParseRule.id] 为 0 时新增，否则更新
     */
    suspend fun upsert(rule: ParseRule)

    /**
     * 批量新增或更新规则，用于预置规则播种与「规则热更新」。
     *
     * NOTE: 预置规则**必须显式指定固定 id**，否则 `@Upsert` 退化为纯插入，
     * 每次播种都会新增一批重复规则，规则引擎的匹配结果将变得不可预测。
     *
     * @param rules 待保存的规则
     */
    suspend fun upsertAll(rules: List<ParseRule>)

    /**
     * 按主键删除一条规则。
     *
     * @param id 规则主键
     */
    suspend fun deleteById(id: Long)
}
