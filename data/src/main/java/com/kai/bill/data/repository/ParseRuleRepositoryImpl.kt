package com.kai.bill.data.repository

import com.kai.bill.core.db.dao.ParseRuleDao
import com.kai.bill.data.mapper.ParseRuleMapper
import com.kai.bill.data.mapper.toEntity
import com.kai.bill.domain.model.ParseRule
import com.kai.bill.domain.model.SourceType
import com.kai.bill.domain.repository.ParseRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ParseRuleRepository] 的 Room 实现。
 */
@Singleton
class ParseRuleRepositoryImpl @Inject constructor(
    private val dao: ParseRuleDao
) : ParseRuleRepository {

    override fun observeAll(): Flow<List<ParseRule>> =
        dao.observeAll().map { it.map(ParseRuleMapper::toDomain) }

    override fun observeEnabled(): Flow<List<ParseRule>> =
        dao.observeEnabled().map { it.map(ParseRuleMapper::toDomain) }

    override fun observeEnabledBySource(source: SourceType): Flow<List<ParseRule>> =
        dao.observeEnabledBySource(source.toEntity()).map { it.map(ParseRuleMapper::toDomain) }

    override suspend fun upsert(rule: ParseRule) =
        dao.upsert(ParseRuleMapper.toEntity(rule))

    override suspend fun upsertAll(rules: List<ParseRule>) =
        dao.upsertAll(rules.map(ParseRuleMapper::toEntity))

    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}
