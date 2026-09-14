package com.kai.bill.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 采集源与解析器绑定（占位，M4/M5 落地）。
 *
 * 短信 / 通知采集服务、各银行与支付宝的账单解析器，其实现在 M4/M5 才出现。
 * 此处先留空 Module，待采集链路接入时再按 `interface → impl` 的 `@Binds` 补齐。
 */
@Module
@InstallIn(SingletonComponent::class)
interface DataSourceModule
