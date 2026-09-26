package com.kai.bill.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 采集源与解析器绑定（占位）。短信 / 通知采集、各银行与支付宝解析器的实现在 M4/M5 才出现，
 * 此处先留空，待采集链路接入时再按 `interface → impl` 的 `@Binds` 补齐。
 */
@Module
@InstallIn(SingletonComponent::class)
interface DataSourceModule
