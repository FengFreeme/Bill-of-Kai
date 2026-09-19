package com.kai.bill.di

import com.kai.bill.core.common.overlay.CaptureToast
import com.kai.bill.core.common.overlay.ReviewCardOverlay
import com.kai.bill.review.OverlayReviewCardHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 确认卡片悬浮层的实现绑定。
 *
 * 实现刻意留在 app 层：确认卡片要显示的是 `feature` 里的 Compose 内容，
 * 而触发它的采集链路在 `data`。只有 app 同时看得见两边，
 * 因此由 `data`（与 `feature` 的测试入口）只依赖 `core:common` 的接口，
 * 实现放在这里绑定 —— 依赖方向始终是单向的。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {

    @Binds
    @Singleton
    abstract fun bindReviewCardOverlay(impl: OverlayReviewCardHost): ReviewCardOverlay

    /**
     * 轻提示与确认卡片共用同一个实现。
     *
     * 两者都需要「用无障碍服务的窗口 token 往屏幕上挂 Compose 视图」这套脚手架，
     * 分开写只会把 `OverlayViewOwner` 那套宿主复制两份 —— 它们的差别只在窗口参数
     * （卡片全屏可点，提示贴底且完全穿透触摸），那部分在实现里已经分开处理。
     */
    @Binds
    @Singleton
    abstract fun bindCaptureToast(impl: OverlayReviewCardHost): CaptureToast
}
