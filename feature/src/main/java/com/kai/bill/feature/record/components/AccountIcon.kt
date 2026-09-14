package com.kai.bill.feature.record.components

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 账户图标 key → 矢量资源名映射。
 *
 * [com.kai.bill.domain.model.Account.icon] 存的是图标 key（如 `"cash"`、`"card-outline"`），
 * 而非 emoji。这里按名称解析 `feature` 模块自托管（Ionicons，MIT）的 Vector Drawable，
 * 避免直接依赖 IDE 偶发无法索引的 `R` 类。
 *
 * 找不到对应资源时返回 null，调用方应降级为文字/emoji。
 */
private val ACCOUNT_KEY_TO_DRAWABLE = mapOf(
    "cash" to "ic_cash",
    "wallet" to "ic_wallet",
    "chatbubbles" to "ic_chatbubbles",
    "card" to "ic_card",
    "card-outline" to "ic_card_outline",
)

/** getIdentifier 较慢，按 drawable 名缓存，避免账户 Chip 首帧反复查资源。 */
private val accountResIdCache = mutableMapOf<String, Int>()

@DrawableRes
fun accountIconRes(context: Context, key: String?): Int? {
    val drawableName = ACCOUNT_KEY_TO_DRAWABLE[key] ?: return null
    val cached = accountResIdCache[drawableName]
    if (cached != null) return cached.takeIf { it != 0 }
    val resId = context.resources.getIdentifier(
        drawableName,
        "drawable",
        context.packageName,
    )
    accountResIdCache[drawableName] = resId
    return resId.takeIf { it != 0 }
}

@Composable
@DrawableRes
fun accountIconRes(key: String?): Int? {
    val context = LocalContext.current
    return remember(key, context) { accountIconRes(context, key) }
}
