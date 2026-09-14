/**
 * data · 数据实现层
 *
 * 职责：把 :domain 定义的接口翻译成具体实现 ——
 *      Room 查询、系统服务采集、文本解析、去重、文件读写。
 *
 * 依赖方向：data -> domain / core:db / core:prefs / core:common
 * M0 阶段：仅落地 Entity ⇔ 领域模型的映射与预置数据（纯函数，不依赖数据库）。
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.kai.bill.data"
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
    implementation(project(":core:db"))
    implementation(project(":core:prefs"))
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)

    // ---- 单元测试（纯 JVM，不依赖设备，作为真机回归的代码层基线）----
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
