package com.kai.bill.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kai.bill.core.design.component.KaiBottomBarHeight
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.feature.budget.BudgetRoute
import com.kai.bill.feature.home.HomeRoute
import com.kai.bill.feature.onboarding.PermissionCheckRoute
import com.kai.bill.feature.record.RecordRoute
import com.kai.bill.feature.settings.SettingsScreen
import com.kai.bill.feature.settings.account.AccountManageRoute
import com.kai.bill.feature.settings.appearance.AppearanceRoute
import com.kai.bill.feature.settings.backup.BackupRoute
import com.kai.bill.feature.settings.category.CategoryManageRoute
import com.kai.bill.feature.settings.review.NeedsReviewRoute
import com.kai.bill.feature.settings.rule.ParseRuleRoute
import com.kai.bill.feature.stats.CategoryDetailRoute
import com.kai.bill.feature.stats.StatsRoute

/**
 * 应用导航图：3 个 Tab 根 + 若干二级页。各页都是无状态或自带 ViewModel 的 `*Route` 组合，
 * NavHost 只负责串路由，不持有业务状态。
 *
 * 转场随背景配置切换（见 [slideComposable]）：无背景图用滑动盖住式（每页有不透明背景，旧页不透出）；
 * 有背景图用交叉淡化（页面要透出根部背景图，滑动会让静止旧页透出来造成花屏）。
 *
 * @param tabBottomInset 系统区底部 inset（如导航手势条），叠加到 Tab 底栏预留高度上
 * @param notificationRoute 通知点击带来的目标路由；消费一次即由 [onNotificationRouteHandled] 清空
 *        （否则每次重组都会重新导航）
 * @param releaseNotesVersion 当前安装版本的公告版本号（null = 该版本无公告，设置页不显示入口）。
 *        由 `MainActivity` 传入而非让 `feature` 读 `BuildConfig`：否则 `feature` 会反向依赖 `app`
 */
@Composable
fun KaiNavHost(
    navController: NavHostController,
    tabBottomInset: Dp = 0.dp,
    backgroundPath: String? = null,
    notificationRoute: String? = null,
    onNotificationRouteHandled: () -> Unit = {},
    releaseNotesVersion: String? = null,
    onReleaseNotesClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tabBottomPad = KaiBottomBarHeight + tabBottomInset

    // NOTE: launchSingleTop 保证用户已在该页时不会叠出第二层
    LaunchedEffect(notificationRoute) {
        val target = notificationRoute ?: return@LaunchedEffect
        navController.navigate(target) { launchSingleTop = true }
        onNotificationRouteHandled()
    }

    NavHost(
        navController = navController,
        startDestination = Route.HOME,
        modifier = modifier
    ) {
        // ---- Tab 根：预留底栏高度，避免内容被常驻底栏挡住 ----
        slideComposable(
            route = Route.HOME,
            contentBottomPadding = tabBottomPad,
            backgroundPath = backgroundPath
        ) {
            HomeRoute(
                onRecordClick = { navController.navigate(Route.RECORD) },
                onBillClick = { billId -> navController.navigate(Route.recordEdit(billId)) },
                onBudgetClick = { navController.navigate(Route.BUDGET) },
                onReviewClick = { navController.navigate(Route.NEEDS_REVIEW) }
            )
        }
        slideComposable(
            route = Route.STATS,
            contentBottomPadding = tabBottomPad,
            backgroundPath = backgroundPath
        ) {
            StatsRoute(
                onCategoryClick = { categoryId ->
                    navController.navigate(Route.categoryDetail(categoryId))
                }
            )
        }
        slideComposable(
            route = Route.SETTINGS,
            contentBottomPadding = tabBottomPad,
            backgroundPath = backgroundPath
        ) {
            SettingsScreen(
                onAppearanceClick = { navController.navigate(Route.APPEARANCE) },
                onCategoryClick = { navController.navigate(Route.categoryManage()) },
                onAccountClick = { navController.navigate(Route.ACCOUNT_MANAGE) },
                onRulesClick = { navController.navigate(Route.PARSE_RULE) },
                onBudgetClick = { navController.navigate(Route.BUDGET) },
                onReviewClick = { navController.navigate(Route.NEEDS_REVIEW) },
                onOnboardingClick = { navController.navigate(Route.ONBOARDING) },
                onBackupClick = { navController.navigate(Route.BACKUP) },
                releaseNotesVersion = releaseNotesVersion,
                onReleaseNotesClick = onReleaseNotesClick
            )
        }

        // ---- 二级页：全屏铺满，盖住底层底栏 ----
        slideComposable(Route.RECORD, backgroundPath = backgroundPath) {
            RecordRoute(
                onClose = { navController.popBackStack() },
                onManageCategory = { parentId -> navController.navigate(Route.categoryManage(parentId)) }
            )
        }
        slideComposable(
            route = Route.RECORD_EDIT,
            arguments = listOf(navArgument("billId") { type = NavType.LongType }),
            backgroundPath = backgroundPath
        ) { backStackEntry ->
            val billId = backStackEntry.arguments?.getLong("billId") ?: 0L
            RecordRoute(
                billId = billId,
                onClose = { navController.popBackStack() },
                onManageCategory = { parentId -> navController.navigate(Route.categoryManage(parentId)) }
            )
        }
        slideComposable(Route.APPEARANCE, backgroundPath = backgroundPath) { AppearanceRoute() }
        slideComposable(Route.BUDGET, backgroundPath = backgroundPath) { BudgetRoute() }
        slideComposable(Route.ONBOARDING, backgroundPath = backgroundPath) { PermissionCheckRoute() }
        slideComposable(
            route = Route.CATEGORY_MANAGE,
            // parentId 可选：记一笔二级网格的「＋」会带上所属大类，进页面直接定位到它
            arguments = listOf(navArgument("parentId") {
                type = NavType.LongType
                defaultValue = -1L
            }),
            backgroundPath = backgroundPath
        ) { backStackEntry ->
            val parentId = backStackEntry.arguments?.getLong("parentId") ?: -1L
            CategoryManageRoute(
                onBack = { navController.popBackStack() },
                focusParentId = parentId
            )
        }
        slideComposable(
            route = Route.CATEGORY_DETAIL,
            arguments = listOf(navArgument("categoryId") { type = NavType.LongType }),
            backgroundPath = backgroundPath
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: 0L
            CategoryDetailRoute(
                categoryId = categoryId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Route.categoryManage()) },
                onBillClick = { billId -> navController.navigate(Route.recordEdit(billId)) }
            )
        }
        slideComposable(Route.ACCOUNT_MANAGE, backgroundPath = backgroundPath) {
            AccountManageRoute(onBack = { navController.popBackStack() })
        }
        slideComposable(Route.PARSE_RULE, backgroundPath = backgroundPath) { ParseRuleRoute() }
        slideComposable(Route.NEEDS_REVIEW, backgroundPath = backgroundPath) {
            NeedsReviewRoute(
                onBack = { navController.popBackStack() },
                onEditBill = { billId -> navController.navigate(Route.recordEdit(billId)) }
            )
        }
        slideComposable(Route.BACKUP, backgroundPath = backgroundPath) {
            BackupRoute(onBack = { navController.popBackStack() })
        }
    }
}

/**
 * 转场规格：进入用减速曲线（LinearOutSlowIn）、退出用加速曲线（FastOutLinearIn）。
 *
 * 这是 Material 的标准进出节奏 —— 进场起步快、收尾柔和；离场迅速让位。
 * 比两端对称的 FastOutSlowIn 更顺，也不会显得拖沓。
 */
private val SlideEnterSpec =
    tween<IntOffset>(durationMillis = 300, easing = LinearOutSlowInEasing)
private val SlideExitSpec =
    tween<IntOffset>(durationMillis = 240, easing = FastOutLinearInEasing)
private val FadeEnterSpec =
    tween<Float>(durationMillis = 260, easing = LinearOutSlowInEasing)
private val FadeExitSpec =
    tween<Float>(durationMillis = 220, easing = FastOutLinearInEasing)

/**
 * 转场封装：按是否启用背景图自动选择动效。
 *
 * - **滑动**（无背景图）：前进/Tab 向右时新页从右滑入盖住静止旧页；返回时当前页向右滑出。
 *   旧页静止（`ExitTransition.None`）—— 因为每页都有不透明背景，不会透出。
 * - **淡化**（有背景图）：页面背景要透出根部背景图，滑动会花屏，故新页淡入 + 旧页淡出。
 */
private fun NavGraphBuilder.slideComposable(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    contentBottomPadding: Dp = 0.dp,
    backgroundPath: String? = null,
    content: @Composable (NavBackStackEntry) -> Unit
) {
    val useSlide = backgroundPath == null

    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = {
            if (useSlide) {
                val towards = if (slideDir(initialState, targetState) > 0) {
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else {
                    AnimatedContentTransitionScope.SlideDirection.Right
                }
                slideIntoContainer(towards = towards, animationSpec = SlideEnterSpec)
            } else {
                fadeIn(animationSpec = FadeEnterSpec)
            }
        },
        // NOTE: 滑动模式旧页静止、被新页盖住（新页背景不透明）；淡化模式旧页同步淡出，
        //       避免「新页先出现、旧页内容才慢慢消失」
        exitTransition = {
            if (useSlide) ExitTransition.None else fadeOut(animationSpec = FadeExitSpec)
        },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = {
            if (useSlide) {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = SlideExitSpec
                )
            } else {
                fadeOut(animationSpec = FadeExitSpec)
            }
        },
        content = { entry ->
            val isTabRoot = contentBottomPadding > 0.dp
            // NOTE: Tab 根页的背景层与内容层都要为常驻底栏留空；背景层若铺满整屏会把底栏盖住
            val areaModifier = Modifier
                .fillMaxSize()
                .then(
                    if (isTabRoot) Modifier.padding(bottom = contentBottomPadding)
                    else Modifier
                )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                // NOTE: 无背景图铺不透明主题背景（滑动转场要求各页不透明）；有背景图则保持透明，
                //       统一透出根部那张全屏背景图 —— 各页再自己画一次会因绘制区域不同（少了 inset）
                //       导致 Crop 缩放比例不一致，看起来像「照片被缩放」
                if (backgroundPath == null) {
                    Box(modifier = areaModifier.background(AppTheme.color.background))
                }
                Box(modifier = areaModifier) {
                    content(entry)
                }
            }
        }
    )
}

/** Tab 从左到右的顺序，用于判定切换方向 */
private val TabOrder = listOf(Route.HOME, Route.STATS, Route.SETTINGS)

/**
 * 计算滑动方向：+1 表示新页从右侧滑入（视觉向左），-1 表示从左滑入（视觉向右）。
 *
 * - 都在 Tab 内：比较下标，目标更靠右则 +1，否则 -1；
 * - 涉及二级页（不在 [TabOrder]）：统一 +1（点击进入向左）。
 */
private fun slideDir(initial: NavBackStackEntry, target: NavBackStackEntry): Int {
    val from = initial.destination.route
    val to = target.destination.route
    val fromIdx = TabOrder.indexOf(from)
    val toIdx = TabOrder.indexOf(to)
    return if (fromIdx >= 0 && toIdx >= 0) {
        if (toIdx >= fromIdx) 1 else -1
    } else {
        1
    }
}
