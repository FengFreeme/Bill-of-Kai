# 小凯记账 · 混淆规则
#
# M0 阶段发布包关闭了混淆（isMinifyEnabled = false）。
# 以下规则为将来开启混淆时预留，避免 Room / Hilt / Kotlin 元数据被错误裁剪。

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---- Kotlin 序列化与反射元数据 ----
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# ---- 领域模型（账单解析依赖字段名）----
-keep class com.kai.bill.domain.model.** { *; }

# ---- 关闭日志中的行号信息（可选）----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
