package com.kai.bill.data.parser

import java.security.MessageDigest

/**
 * 去重键生成。
 *
 * 组合「金额 + 分钟级时间 + 归一化商户」做 md5，作为
 * [com.kai.bill.core.db.entity.BillEntity.dedupHash] 的唯一索引值；
 * 同一笔消费的通知 / 短信双通道到达时，后到者会因唯一索引冲突被 IGNORE。
 */
object DedupKey {

    fun build(amountCents: Long, tradeTimeMillis: Long, merchant: String?): String {
        val raw = "$amountCents|${tradeTimeMillis / 60_000L}|${merchant.orEmpty().trim()}"
        return md5(raw)
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
