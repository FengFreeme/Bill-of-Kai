/**
 * feature · UI 层（所有页面集中在此模块，内部按 package 隔离）
 *
 * 铁律 2：本模块**禁止依赖 :data**，只能依赖 :domain 接口。
 *        UI 不接触数据库、不写业务规则、不做文本解析。
 *
 * 单模块而非多模块：个人项目构建速度优先，package 隔离已足够清晰，
 * 将来若模块膨胀再按页面拆分。
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.kai.bill.feature"
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
    implementation(project(":core:design"))
    implementation(project(":core:prefs"))
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)

    // ---- Compose ----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ---- 依赖注入 ----
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)

    // ---- 图表（Vico 3.x）----
    implementation(libs.vico.compose)

    // ---- 图片加载（背景图预览）----
    implementation(libs.coil.compose)

    // ---- 单元测试 ----
    // 本模块只测**纯逻辑**（如自绘图表里的刻度取点、格式化），不测 Compose 渲染：
    // 后者要跑 instrumentation，代价远大于收益；而「刻度算错」这类问题恰好在纯函数里就能钉死。
    testImplementation(libs.junit)
}
