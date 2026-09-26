/**
 * app · 应用壳模块
 *
 * 职责最小化：只做三件事
 * 1. 应用入口（KaiApplication / MainActivity）
 * 2. 导航装配（KaiNavHost / MainBottomBar）
 * 3. 依赖绑定的唯一位置（di/ 包）
 *
 * 本模块**禁止**编写任何业务逻辑。
 */
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val signingProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(FileInputStream(f))
}

/**
 * 用 Git 提交数作为 versionCode：每次提交自动 +1，不必手动维护。
 *
 * 回退策略：非 Git 仓库 / 无提交历史 / git 不在 PATH 时返回 1，
 * 保证任何环境下（例如刚 clone 还未 init）都能正常构建。
 */
val gitCommitCount: Int = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

android {
    namespace = "com.kai.bill"
    compileSdk = 37

    defaultConfig {
        // 应用标识保持完整名称，与包名（namespace）解耦：
        // 改名包名不会影响已安装应用的升级与数据
        applicationId = "com.kai.billofkai"
        minSdk = 31
        targetSdk = 36
        // versionCode 由 Git 提交数自动递增（见文件上方 gitCommitCount）
        // versionName 面向用户，语义化版本，手动维护
        versionCode = gitCommitCount
        versionName = "1.3.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(signingProps.getProperty("STORE_FILE")!!)
            storePassword = signingProps.getProperty("STORE_PASSWORD")
            keyAlias = signingProps.getProperty("KEY_ALIAS")
            keyPassword = signingProps.getProperty("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 更新公告要按「当前装的是哪个版本」决定弹不弹：BuildConfig.VERSION_NAME 是编译期常量，
        // 直接可用；否则每次启动都得去 PackageManager 查一次，还要处理查询失败的兜底
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ---- 工程内模块：app 是唯一可以依赖全部模块的模块 ----
    implementation(project(":core:common"))
    implementation(project(":core:design"))
    implementation(project(":core:db"))
    implementation(project(":core:prefs"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":feature"))

    // ---- AndroidX 基础 ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

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

    // ---- 本地存储 ----
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)

    // ---- 异步 ----
    implementation(libs.kotlinx.coroutines.android)
}
