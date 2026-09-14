/**
 * domain · 领域层（纯 Kotlin JVM 模块）
 *
 * 铁律 1：本模块使用 kotlin("jvm") 插件而非 Android 插件，
 *        因此**编译期即禁止** import android.* —— 依赖倒置由构建系统强制保证。
 *
 * 职责：领域模型、Repository 接口、UseCase、纯函数计算器。
 * 依赖：仅 kotlinx-coroutines（Flow 所需），不依赖任何工程内模块。
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // 领域模型与 Repository 接口需要 Flow 作为响应式契约
    implementation(libs.kotlinx.coroutines.core)

    // Repository 接口 / UseCase 使用 JSR-330 @Inject 注解，但不引入 Hilt 运行时
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
