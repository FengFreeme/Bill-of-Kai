/**
 * core:common · 通用基础模块
 *
 * 提供与业务无关的工具：金额换算、时间区间、结果封装、厂商识别、常用扩展。
 * 依赖倒置的最底层，**不依赖任何其它模块**。
 *
 * 注意：本模块是 Android Library（RomDetector 需读 android.os.Build），
 *      因此不可被纯 JVM 的 :domain 模块依赖。领域层如需同类能力需自行定义。
 */
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kai.bill.core.common"
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
