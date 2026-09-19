package com.kai.bill.data.pending

import com.kai.bill.domain.repository.PendingBillRepository
import com.kai.bill.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 待确认记录的过期清理。
 *
 * 待确认是「当时判不准、等用户拍板」的临时数据：用户一直不理会时它会一直堆着，
 * 列表越长越没人看，最后彻底失去意义。因此给一个保留期，过期的直接清掉 ——
 * 用户真想要这笔账，还有原始通知和手动记账两条路。
 *
 * 触发时机：冷启动时清一次即可。不需要 WorkManager 定时任务 ——
 * 待确认列表本来就是「打开 App 才看」的东西，多跑后台任务只是徒增耗电。
 */
@Singleton
class PendingBillCleaner @Inject constructor(
    private val repository: PendingBillRepository,
    private val clock: Clock
) {

    /**
     * @return 清理掉的条数（0 表示没有过期记录）
     */
    suspend fun purgeExpired(): Int =
        repository.deleteOlderThan(clock.nowMillis() - RETAIN_MILLIS)

    private companion object {

        /** 保留期：30 天 */
        const val RETAIN_DAYS = 30L

        const val RETAIN_MILLIS = RETAIN_DAYS * 24 * 60 * 60 * 1000
    }
}
