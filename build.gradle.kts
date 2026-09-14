/**
 * 根构建脚本
 *
 * 这里只声明插件及其版本（apply false），真正的 apply 在各 Module 内完成，
 * 避免所有模块被迫引入同一批插件。
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
