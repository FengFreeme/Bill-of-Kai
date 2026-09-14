package com.kai.bill.data.capture.sms

import com.kai.bill.data.capture.BillSource
import com.kai.bill.domain.model.SourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * content://sms/inbox 增量扫描。M5 落地。
 */
@Singleton
class SmsScanner @Inject constructor() : BillSource {
    override val sourceType: SourceType = SourceType.SMS
}
