package com.kai.bill.di

import com.kai.bill.core.common.overlay.CaptureHintOverlay
import com.kai.bill.core.common.overlay.ReviewCardOverlay
import com.kai.bill.review.OverlayCaptureHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 无障碍悬浮反馈（确认卡片 + 提示条）的实现绑定。
 *
 * 实现刻意留在 app 层：悬浮内容里是 `feature` 的 Compose UI，
 * 而触发它的采集链路在 `data`。只有 app 同时看得见两边，
 * 因此由 `data`（与 `feature` 的测试入口）只依赖 `core:common` 的接口，
 * 实现放在这里绑定 —— 依赖方向始终是单向的。
 *
 * [OverlayCaptureHost] 一个实例同时实现两个接口：两类内容共用同一套窗口脚手架，
 * 且同一时刻只会显示一个（卡片优先），因此不需要各自一个单例。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {

    @Binds
    @Singleton
    abstract fun bindReviewCardOverlay(impl: OverlayCaptureHost): ReviewCardOverlay

    @Binds
    @Singleton
    abstract fun bindCaptureHintOverlay(impl: OverlayCaptureHost): CaptureHintOverlay
}
