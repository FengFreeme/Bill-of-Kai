/**
 * 小凯记账 · 工程模块声明
 *
 * 仓库顺序说明：阿里云镜像在前、官方源兜底。
 * 国内网络环境下镜像能显著加速依赖下载；若镜像缺失某个构件，会自动回落到官方源。
 */
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    // 强制所有模块统一使用此处声明的仓库，禁止模块内自行声明，避免仓库来源散落
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "BillOfKai"

// ---------- 8 个 Gradle Module ----------
// 包名统一前缀 com.kai.bill
include(":app")

include(":core:common")
include(":core:design")
include(":core:db")
include(":core:prefs")

// domain 为纯 Kotlin JVM 模块（零 Android 依赖），源码位于 src/main/kotlin
include(":domain")

include(":data")
include(":feature")
