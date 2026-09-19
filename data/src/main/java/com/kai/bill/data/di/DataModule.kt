package com.kai.bill.data.di

import com.kai.bill.data.capture.notification.DefaultNotificationCaptureBridge
import com.kai.bill.data.capture.notification.NotificationCaptureBridge
import com.kai.bill.data.ingest.ReconcileConfig
import com.kai.bill.data.notify.BillPendingNotifier
import com.kai.bill.data.notify.BillReviewNotifier
import com.kai.bill.data.notify.BillSavedNotifier
import com.kai.bill.data.notify.DefaultBillPendingNotifier
import com.kai.bill.data.notify.DefaultBillReviewNotifier
import com.kai.bill.data.notify.DefaultBillSavedNotifier
import com.kai.bill.data.parser.MatchKeywordTables
import com.kai.bill.data.presets.DefaultMatchKeywords
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * data 模块 Hilt 绑定。
 *
 * 把接口声明与实现分离（服务层用接口、单测可 mock、实现可替换），
 * 这里统一用 [Binds] 注册默认实现。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindNotificationCaptureBridge(
        impl: DefaultNotificationCaptureBridge
    ): NotificationCaptureBridge

    @Binds
    @Singleton
    abstract fun bindBillSavedNotifier(
        impl: DefaultBillSavedNotifier
    ): BillSavedNotifier

    @Binds
    @Singleton
    abstract fun bindBillPendingNotifier(
        impl: DefaultBillPendingNotifier
    ): BillPendingNotifier

    @Binds
    @Singleton
    abstract fun bindBillReviewNotifier(
        impl: DefaultBillReviewNotifier
    ): BillReviewNotifier

    companion object {

        /**
         * 词表来源：P0 是代码常量，P1 改成 Room 仓储（观察变更 + 内存缓存）。
         *
         * 之所以要经过 Hilt 而不是让四级阶段类直接引用常量对象：这是把「引擎」与「词表存储」
         * 解耦的唯一接缝 —— 换存储时只改这一处绑定，阶段类与流水线零改动。
         */
        @Provides
        @Singleton
        fun provideMatchKeywordTables(): MatchKeywordTables = DefaultMatchKeywords.tables

        /**
         * 信号决策的可调参数。
         *
         * 走 Hilt 而不是让 `SignalReconciler` 直接引用常量：这两个窗口是「需真机实测后调」
         * 的旋钮，集中一处提供，将来换成用户可调配置也只改这一处绑定。
         */
        @Provides
        @Singleton
        fun provideReconcileConfig(): ReconcileConfig = ReconcileConfig()
    }
}
