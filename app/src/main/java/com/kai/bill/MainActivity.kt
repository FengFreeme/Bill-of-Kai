package com.kai.bill

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.kai.bill.core.design.component.AppBackground
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.prefs.KaiPrefs
import com.kai.bill.core.prefs.ThemeConfig
import com.kai.bill.data.notify.PendingNotificationContract
import com.kai.bill.feature.onboarding.FirstRunGuideDialog
import com.kai.bill.feature.whatsnew.ReleaseNote
import com.kai.bill.feature.whatsnew.ReleaseNotes
import com.kai.bill.feature.whatsnew.ReleaseNotesDialog
import com.kai.bill.navigation.KaiNavHost
import com.kai.bill.navigation.MainBottomBar
import com.kai.bill.navigation.Route
import com.kai.bill.review.EditBillContract
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 主 Activity —— 应用的唯一入口与组合根。
 *
 * 只收集 [KaiPrefs.themeConfig] 装配 [BillOfKaiTheme] 并挂上背景与导航，不写 UI 布局与业务逻辑。
 *
 * 额外把**通知点击带来的目标页面**翻译成本 App 路由（见 [PendingNotificationContract]）：
 * 通知由 `data` 发出、那里看不到路由表，翻译只能落在 app 层。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: KaiPrefs

    /**
     * 通知点击要求跳转的目标路由；消费后置空。
     *
     * 用 [mutableStateOf] 而非普通字段：`onNewIntent` 可能在 Activity 已在前台时触发，
     * 只有可观察状态才能让已组合的界面响应跳转。
     */
    private val notificationRoute = mutableStateOf<String?>(null)

    /**
     * 待展示的更新公告；null 表示不展示。
     *
     * 用 [mutableStateOf] 而非普通字段：判断是异步的（要读一次 DataStore 才知道看过没），
     * 结果回来时界面早已组合完成，只有可观察状态才能让卡片出现。
     */
    private val releaseNote = mutableStateOf<ReleaseNote?>(null)

    /**
     * 是否展示首次启动的上手引导卡片。
     *
     * 与 [releaseNote] 同一类：判断要读一次 DataStore，结果回来时界面早已组合完成，
     * 只有可观察状态才能让卡片出现。
     */
    private val firstRunGuide = mutableStateOf(false)

    // NOTE: Android 13+ 需在运行时申请通知权限，否则「已记账」通知不显示
    private val requestPostNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPostNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // 冷启动（进程被杀后点通知 / 从确认卡片点「改分类」）走这里
        notificationRoute.value = intent.toNotificationRoute() ?: intent.toEditBillRoute()

        // 首次启动：弹一次上手引导卡片，并**跳过更新公告** —— 新装用户要先知道「该做什么」，
        // 而不是版本变更说明；两张卡片同时出现也会互相盖住（公告浮在最上层）。
        // 之后每次冷启动才回到「更新公告」那套判断。
        lifecycleScope.launch {
            if (!prefs.firstLaunchDone()) {
                prefs.markFirstLaunchDone()
                firstRunGuide.value = true
                return@launch
            }
            // 更新公告：装上的版本 ≠ 上次看过并关掉的版本时，弹一次。
            // 放在冷启动而不是挂在某个页面上：它是**应用级事件**，挂到首页只会让
            // 「首页要不要负责这件事」变成每次新增页面都要重新回答的问题。
            // 版本没有对应公告时静默跳过（forVersion 返回 null）
            val currentVersion = BuildConfig.VERSION_NAME
            if (prefs.lastSeenReleaseVersion() != currentVersion) {
                releaseNote.value = ReleaseNotes.forVersion(currentVersion)
            }
        }

        enableEdgeToEdge()
        setContent {
            // WHY: 主题配置变化即重组，设置页换肤 / 换背景 / 调透明度后整个 App 立即生效
            val config by prefs.themeConfig.collectAsStateWithLifecycle(
                initialValue = ThemeConfig(),
                lifecycle = LocalLifecycleOwner.current.lifecycle
            )
            BillOfKaiTheme(
                palette = config.palette,
                darkMode = config.darkMode,
                cardAlpha = config.cardAlpha,
                hasBackgroundImage = config.backgroundUri != null
            ) {
                // 当前版本的公告文案；null = 这个版本没写公告。设置页据此决定显不显示入口
                val currentNote = remember { ReleaseNotes.forVersion(BuildConfig.VERSION_NAME) }

                Box(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        backgroundPath = config.backgroundUri,
                        backgroundDim = config.backgroundDim,
                        backgroundScale = config.backgroundScale,
                        backgroundOffsetX = config.backgroundOffsetX,
                        backgroundOffsetY = config.backgroundOffsetY,
                        notificationRoute = notificationRoute.value,
                        onNotificationRouteHandled = { notificationRoute.value = null },
                        releaseNotesVersion = currentNote?.version,
                        // 设置页的「更新公告」入口：复用冷启动那张卡片，只是由用户主动唤出
                        onReleaseNotesClick = { currentNote?.let { releaseNote.value = it } }
                    )
                    // 公告浮在最上层（底栏之上）：它是应用级提示，不属于任何一个页面
                    releaseNote.value?.let { note ->
                        ReleaseNotesDialog(note = note, onDismiss = ::dismissReleaseNote)
                    }
                    // 上手引导同理，且与公告不会同时出现（首次启动只弹引导，见 onCreate）
                    if (firstRunGuide.value) {
                        FirstRunGuideDialog(
                            onStart = {
                                firstRunGuide.value = false
                                // 复用通知那条通路：它本来就是「从外部把用户送到某个页面」
                                notificationRoute.value = Route.ONBOARDING
                            },
                            onDismiss = { firstRunGuide.value = false }
                        )
                    }
                }
            }
        }
    }

    /**
     * 关掉更新公告，并记下「这个版本已经看过了」。
     *
     * 标记写在**用户关掉的那一刻**，而不是启动时：进程若在公告弹出之前被杀，
     * 下次启动应当照常再弹一次，而不是把这条公告永久吞掉。
     */
    private fun dismissReleaseNote() {
        releaseNote.value = null
        lifecycleScope.launch { prefs.markReleaseVersionSeen(BuildConfig.VERSION_NAME) }
    }

    /** App 已在前台 / 后台时点通知走这里（Intent 带 `FLAG_ACTIVITY_SINGLE_TOP`，不会重建 Activity） */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationRoute.value = intent.toNotificationRoute() ?: intent.toEditBillRoute()
    }
}

/**
 * 「去改这一笔」请求 → 本 App 的编辑页路由。
 *
 * 刻意复用通知那条通路（同一个 `notificationRoute` 状态）：两者都是「从外部把用户送到某个页面」，
 * 只是发起者不同（通知 vs 确认卡片）。再开一条平行通路，就会多一处需要同步维护的跳转逻辑。
 */
private fun Intent?.toEditBillRoute(): String? =
    this?.getLongExtra(EditBillContract.EXTRA_EDIT_BILL_ID, 0L)
        ?.takeIf { it > 0L }
        ?.let(Route::recordEdit)

/**
 * 通知里的「目标页面」标记 → 本 App 的路由名。
 *
 * 未识别的标记返回 null（例如通知来自更高版本、带了个本版本还不认识的目标），
 * 此时只打开 App 首页，不做多余跳转。
 */
private fun Intent?.toNotificationRoute(): String? =
    when (this?.getStringExtra(PendingNotificationContract.EXTRA_DESTINATION)) {
        PendingNotificationContract.DESTINATION_NEEDS_REVIEW -> Route.NEEDS_REVIEW
        else -> null
    }

/**
 * 组合根：背景层 + 底栏 + 全屏 NavHost。
 *
 * [AppBackground] 铺满最底层（无图纯色、有图「图 + 遮罩」）；Scaffold 背景透明让背景图透上来；
 * Tab 根页透明（透出根部背景图，图像与底栏区域连续），二级页自铺整屏背景盖住底栏。
 */
@Composable
private fun AppRoot(
    backgroundPath: String?,
    backgroundDim: Float,
    backgroundScale: Float,
    backgroundOffsetX: Float,
    backgroundOffsetY: Float,
    notificationRoute: String?,
    onNotificationRouteHandled: () -> Unit,
    releaseNotesVersion: String?,
    onReleaseNotesClick: () -> Unit
) {
    val navController = rememberNavController()

    AppBackground(
        imagePath = backgroundPath,
        dim = backgroundDim,
        scale = backgroundScale,
        offsetX = backgroundOffsetX,
        offsetY = backgroundOffsetY
    ) {
        Scaffold(containerColor = Color.Transparent) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                        end = innerPadding.calculateEndPadding(LayoutDirection.Ltr)
                    )
            ) {
                MainBottomBar(
                    navController = navController,
                    // NOTE: 系统导航栏高度交给底栏「内部」铺色（单层背景）；
                    //       若在外层再叠一层同色，半透明时会叠出深浅差、露出缝隙
                    bottomInset = innerPadding.calculateBottomPadding(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                KaiNavHost(
                    navController = navController,
                    tabBottomInset = innerPadding.calculateBottomPadding(),
                    backgroundPath = backgroundPath,
                    notificationRoute = notificationRoute,
                    onNotificationRouteHandled = onNotificationRouteHandled,
                    releaseNotesVersion = releaseNotesVersion,
                    onReleaseNotesClick = onReleaseNotesClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
