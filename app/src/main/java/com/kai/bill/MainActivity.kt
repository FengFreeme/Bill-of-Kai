package com.kai.bill

import android.Manifest
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.kai.bill.core.design.component.AppBackground
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.prefs.KaiPrefs
import com.kai.bill.core.prefs.ThemeConfig
import com.kai.bill.navigation.KaiNavHost
import com.kai.bill.navigation.MainBottomBar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 主 Activity —— 应用的唯一入口与组合根。
 *
 * 职责最小化：收集 [KaiPrefs.themeConfig]（主题色 / 深模 / 背景图 / 卡片透明度），
 * 装配 [BillOfKaiTheme]，再挂上背景与导航。不写任何 UI 布局、不写业务逻辑。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: KaiPrefs

    // Android 13+ 需在运行时申请通知权限，否则「已记账」通知不显示
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
        enableEdgeToEdge()
        setContent {
            // 主题配置变化即重组：设置页换肤 / 换背景 / 调透明度后，整个 App 立即生效
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
                AppRoot(
                    backgroundPath = config.backgroundUri,
                    backgroundDim = config.backgroundDim
                )
            }
        }
    }
}

/**
 * 组合根：背景层 + 底栏常驻 + 全屏 NavHost。
 *
 * 布局策略：
 * - [AppBackground] 铺满最底层：无背景图时为纯色，有背景图时为「图 + 遮罩」；
 * - Scaffold 背景透明，让底层的背景图透上来；
 * - 底栏始终挂在底层，进入二级页时不卸载、不收起；
 * - Tab 根页背景透明（透出根部背景图，图像与底栏区域连续），二级页自铺整屏背景盖住底栏。
 */
@Composable
private fun AppRoot(backgroundPath: String?, backgroundDim: Float) {
    val navController = rememberNavController()

    AppBackground(imagePath = backgroundPath, dim = backgroundDim) {
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
                    // 系统导航栏高度交给底栏「内部」铺色（单层背景）。
                    // 若在外层再叠一层同色，半透明时会叠出深浅差、露出缝隙。
                    bottomInset = innerPadding.calculateBottomPadding(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                KaiNavHost(
                    navController = navController,
                    tabBottomInset = innerPadding.calculateBottomPadding(),
                    backgroundPath = backgroundPath,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
