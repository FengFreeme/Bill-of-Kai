package com.google.android.accessibility.selecttospeak

import com.kai.bill.data.capture.accessibility.KaiAccessibilityCore
import dagger.hilt.android.AndroidEntryPoint

/**
 * 「小凯记账 · 分类识别」无障碍服务：清单中声明的入口，自身不含逻辑。
 *
 * @note 为什么叫这个名字：微信按无障碍服务的**完整类名**决定是否放出 MMWebViewUI
 *   （账单详情页）的节点树 —— 类名不在它的名单里时只返回空树，详情页一个字都读不到，
 *   所以这里沿用公开的辅助技术类名。出处：AOSP 随选朗读 SelectToSpeak（Apache-2.0）。
 *   该名称不要改；备选 com.android.switchaccess.SwitchAccessService，
 *   R8 保名规则见 app/proguard-rules.pro。
 * @see KaiAccessibilityCore 全部实现
 */
@AndroidEntryPoint
class SelectToSpeakService : KaiAccessibilityCore()
