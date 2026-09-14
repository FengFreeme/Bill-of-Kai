/**
 * core:design · 设计系统模块
 *
 * 提供纯色主题与复用组件：三套配色、深浅双模、ExtendedColors、
 * GlassCard / GlassBottomBar / SegmentTabs 等（Glass* 为历史命名，实现已是纯色）。
 *
 * 铁律 3：本模块**禁止依赖 :domain**，只认识 Color / Dp / String。
 * 业务语义封装（如按账单类型取色）放在 :feature 的 common 包中。
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kai.bill.core.design"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:common"))

    implementation(libs.androidx.core.ktx)

    // ---- Compose ----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ---- 图片加载（自定义背景图）----
    implementation(libs.coil.compose)
}
