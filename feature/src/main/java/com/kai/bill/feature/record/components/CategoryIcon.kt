package com.kai.bill.feature.record.components

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 分类图标 key → 矢量资源名映射。
 *
 * [com.kai.bill.domain.model.Category.icon] 存的是图标 key（如 `"restaurant"`），
 * 而非 emoji 或资源 ID。这里按名称解析 `feature` 模块自托管（Ionicons，MIT）的
 * Vector Drawable，避免直接依赖 IDE 偶发无法索引的 `R` 类。
 *
 * 找不到对应资源时返回 null，调用方应降级为文字/emoji。
 */
private val ICON_KEY_TO_DRAWABLE = mapOf(
    "restaurant" to "ic_restaurant",
    "car" to "ic_car",
    "cart" to "ic_cart",
    "home" to "ic_home",
    "call" to "ic_call",
    "game-controller" to "ic_game_controller",
    "medkit" to "ic_medkit",
    "school" to "ic_school",
    "airplane" to "ic_airplane",
    "paw" to "ic_paw",
    "shirt" to "ic_shirt",
    "grid" to "ic_grid",
    "wallet" to "ic_wallet",
    "gift" to "ic_gift",
    "pie-chart" to "ic_pie_chart",
    "briefcase" to "ic_briefcase",
    "cash" to "ic_cash",
    "add" to "ic_add",
    "swap-horizontal" to "ic_swap_horizontal",
    "refresh-circle" to "ic_refresh_circle",

    // ---- 二级分类图标 ----
    "wine" to "ic_wine",
    "nutrition" to "ic_nutrition",
    "cafe" to "ic_cafe",
    "basket" to "ic_basket",
    "bus" to "ic_bus",
    "car-sport" to "ic_car_sport",
    "flame" to "ic_flame",
    "pin" to "ic_pin",
    "construct" to "ic_construct",
    "laptop" to "ic_laptop",
    "color-palette" to "ic_color_palette",
    "happy" to "ic_happy",
    "bed" to "ic_bed",
    "business" to "ic_business",
    "water" to "ic_water",
    "key" to "ic_key",
    "hammer" to "ic_hammer",
    "wifi" to "ic_wifi",
    "star" to "ic_star",
    "cube" to "ic_cube",
    "film" to "ic_film",
    "fitness" to "ic_fitness",
    "musical-notes" to "ic_musical_notes",
    "pulse" to "ic_pulse",
    "medical" to "ic_medical",
    "heart" to "ic_heart",
    "shield" to "ic_shield",
    "book" to "ic_book",
    "bulb" to "ic_bulb",
    "document-text" to "ic_document_text",
    "train" to "ic_train",
    "ticket" to "ic_ticket",
    "bag" to "ic_bag",
    "cut" to "ic_cut",
    "layers" to "ic_layers",
    "walk" to "ic_walk",
    "diamond" to "ic_diamond",
    "warning" to "ic_warning",
    "trending-up" to "ic_trending_up",
    "add-circle" to "ic_add_circle",
    "time" to "ic_time",
    "trophy" to "ic_trophy",
    "stats-chart" to "ic_stats_chart",
    "analytics" to "ic_analytics",
    "create" to "ic_create",
    "receipt" to "ic_receipt",
    "arrow-undo" to "ic_arrow_undo",
    "arrow-down" to "ic_arrow_down",
    "arrow-up" to "ic_arrow_up",
)

/** getIdentifier 较慢，按 drawable 名缓存，避免分类网格首帧反复查资源。 */
private val categoryResIdCache = mutableMapOf<String, Int>()

@DrawableRes
fun categoryIconRes(context: Context, key: String?): Int? {
    val drawableName = ICON_KEY_TO_DRAWABLE[key] ?: return null
    val cached = categoryResIdCache[drawableName]
    if (cached != null) return cached.takeIf { it != 0 }
    val resId = context.resources.getIdentifier(
        drawableName,
        "drawable",
        context.packageName,
    )
    categoryResIdCache[drawableName] = resId
    return resId.takeIf { it != 0 }
}

@Composable
@DrawableRes
fun categoryIconRes(key: String?): Int? {
    val context = LocalContext.current
    return remember(key, context) { categoryIconRes(context, key) }
}
