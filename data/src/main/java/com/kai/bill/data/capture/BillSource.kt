package com.kai.bill.data.capture

import com.kai.bill.domain.model.SourceType

/**
 * 可插拔采集源统一接口。
 *
 * M4+：NotificationCapture / SmsScanner 实现本接口，
 * 输出原始文本到 [BillIngestor]。
 */
interface BillSource {

    /** 来源类型 */
    val sourceType: SourceType

    /** 启动采集（若适用）。 */
    fun start() {}

    /** 停止采集（若适用）。 */
    fun stop() {}
}
