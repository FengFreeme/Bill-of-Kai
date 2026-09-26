package com.kai.bill.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.kai.bill.core.design.component.KaiBottomBar
import com.kai.bill.core.design.component.KaiBottomBarItem
import com.kai.bill.feature.common.HomeTabIcon
import com.kai.bill.feature.common.SettingsTabIcon
import com.kai.bill.feature.common.StatsTabIcon

/** 底栏显隐的过渡时长：与页面转场节奏对齐，避免二级页刚进来底栏就闪没 */
private const val BAR_FADE_MILLIS = 200

/**
 * 底部导航装配层：读 [NavHostController] → 驱动纯视觉 [KaiBottomBar]；仅含三个顶层 Tab。
 *
 * 显隐策略：**只有顶层 Tab 显示底栏**，二级页隐藏 —— 自定义背景图要求页面透明，
 * 透明页盖不住底栏，故不能像早期那样「底栏常驻、二级页用不透明背景盖住」。
 *
 * @param bottomInset 系统导航栏高度，交给底栏内部一并铺色（保证背景只有一层）
 */
@Composable
fun MainBottomBar(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val routeIndex = TopLevelDestination.entries.indexOfFirst { it.route == currentRoute }
    val isTopLevel = routeIndex >= 0

    // 二级页盖住底栏时，保留离开 Tab 前的选中态
    var selectedIndex by remember { mutableIntStateOf(0) }
    SideEffect {
        if (routeIndex >= 0 && routeIndex != selectedIndex) {
            selectedIndex = routeIndex
        }
    }

    val items = remember {
        TopLevelDestination.entries.map { destination ->
            KaiBottomBarItem(
                label = destination.label,
                icon = { selected ->
                    when (destination) {
                        TopLevelDestination.HOME -> HomeTabIcon(selected = selected)
                        TopLevelDestination.STATS -> StatsTabIcon(selected = selected)
                        TopLevelDestination.SETTINGS -> SettingsTabIcon(selected = selected)
                    }
                }
            )
        }
    }

    AnimatedVisibility(
        visible = isTopLevel,
        enter = fadeIn(animationSpec = tween(BAR_FADE_MILLIS, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(BAR_FADE_MILLIS, easing = FastOutSlowInEasing)),
        modifier = modifier
    ) {
        KaiBottomBar(
            selectedIndex = selectedIndex,
            items = items,
            onItemClick = { index ->
                val destination = TopLevelDestination.entries[index]
                if (currentRoute != destination.route) {
                    navController.navigate(destination.route) {
                        popUpTo(Route.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            bottomInset = bottomInset
        )
    }
}

/** 底部导航的三个顶层目的地 */
private enum class TopLevelDestination(
    val route: String,
    val label: String
) {
    HOME(Route.HOME, "首页"),
    STATS(Route.STATS, "统计"),
    SETTINGS(Route.SETTINGS, "设置")
}
