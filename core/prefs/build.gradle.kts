/**
 * core:prefs · 配置与状态持久化模块
 *
 * 职责：存放轻量配置与运行水位线（主题配置、短信扫描时间戳等）。
 * 边界：**不存业务数据**，账单/分类/预算一律走 Room。
 *
 * 刻意的设计偏离（记录以便追溯）：本模块额外依赖了 :core:design。
 * 框架文档 §4.1 的依赖矩阵只列了 core:common，但 [ThemeConfig] 需要承载
 * AppPalette / DarkMode 两个主题枚举，而它们天然属于设计系统。
 * 若改在 prefs 里存字符串、由上层转换，反而会把「主题键名」泄漏到 feature 与 app 两处。
 * 权衡后选择让 prefs 依赖 design（依赖图仍是单向无环：prefs → design → common），
 * 且**不违反任何一条铁律**（铁律只约束 domain / feature / design）。
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.kai.bill.core.prefs"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:design"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
