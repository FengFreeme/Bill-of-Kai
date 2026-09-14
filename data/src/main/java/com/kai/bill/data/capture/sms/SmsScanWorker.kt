package com.kai.bill.data.capture.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager 周期兜底扫短信。M5 落地。
 */
class SmsScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = Result.success()
}
