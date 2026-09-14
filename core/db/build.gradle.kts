/**
 * core:db · 本地数据库模块
 *
 * 职责：表结构（Entity）、查询（DAO）、类型转换、数据库版本与迁移。
 * 边界：只向外暴露 Entity 与 DAO，**不做** Entity ⇔ 领域模型的转换
 *      （转换职责在 :data 的 mapper 包）。
 *
 * 当前为 M0 阶段：5 张表与 DAO 查询已定义，业务实现在 M1/M2 填充。
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kai.bill.core.db"
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

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.core)
}

// Room 把每个版本的 schema 导出到 core/db/schemas 供迁移校验。
// 没有它就只能靠手写 SQL 猜上一版的表结构，M1 起加迁移时极易出错。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
