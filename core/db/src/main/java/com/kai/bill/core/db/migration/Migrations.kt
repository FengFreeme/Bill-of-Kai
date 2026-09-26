package com.kai.bill.core.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移集合。
 *
 * 每次升版都按这套流程走：
 * 1. 在 `core/db/schemas/` 下核对 Room 导出的新版 schema（本模块已开 `exportSchema`）
 * 2. 在本文件新增一条 [Migration]（命名 `MIGRATION_x_y`）
 * 3. 加进 [ALL_MIGRATIONS]，并 bump `KaiDatabase` 里的 `DATABASE_VERSION`
 * 4. 确认 `app/di/DatabaseModule` 的 `addMigrations(...)` 已生效
 *
 * **不得再依赖 `fallbackToDestructiveMigration`**：它会在迁移缺失时静默清空用户全部账目，
 * 而用户真机上已经有真实账目。缺迁移时宁可让 Room 抛异常（打不开），也不要静默清库。
 */
/**
 * v1 → v2：新增待确认表 `pending_bill`。
 *
 * 纯增量建表，不改任何既有表的列，因此不会触碰用户已有账目 —— 这是迁移里最安全的一类。
 *
 * 建表 SQL 与索引名**逐字抄自 Room 导出的
 * `core/db/schemas/com.kai.bill.core.db.KaiDatabase/2.json`**（字段顺序、NOT NULL、
 * 索引命名全部与 Room 的期望一致）。这一步不能凭记忆手写：任何偏差都会在 Room 启动时
 * 的 schema 校验（`validateMigration`）里炸成「升级后打不开」，而不是在编译期被发现。
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_bill` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`amountCents` INTEGER NOT NULL, " +
                "`suggestedType` TEXT, " +
                "`suggestedCategoryId` INTEGER, " +
                "`suggestedAccountId` INTEGER, " +
                "`suggestedCountInStats` INTEGER NOT NULL, " +
                "`reason` TEXT NOT NULL, " +
                "`matchedKeyword` TEXT, " +
                "`time` INTEGER NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`rawText` TEXT, " +
                "`dedupHash` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pending_bill_createdAt` " +
                "ON `pending_bill` (`createdAt`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_bill_dedupHash` " +
                "ON `pending_bill` (`dedupHash`)"
        )
    }
}

/**
 * v2 → v3：`bill` 表新增 `isRefund`，并把历史「退款」行标记上。
 *
 * 退款在收入侧立账（与来源页面的「+12.39」一致），统计上却必须从支出里冲抵。
 * 旧实现只有「收入 + 分类 94」这条隐含约定，用户改掉分类就失效；显式字段还能让
 * 「冲抵到原消费分类」成立（退款行的 `categoryId` 会被写成原支出的分类）。
 *
 * NOTE: 回填里的 `94` 是**历史分类 id 的快照**，不是引用当前常量 ——
 * 迁移描述的是当时的数据，分类表以后再调也不该回头改这里。
 * 回填只打标记、不动归属：迁移里做「同金额 + 最近」的回溯，风险大于收益。
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bill` ADD COLUMN `isRefund` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `bill` SET `isRefund` = 1 WHERE `type` = 'INCOME' AND `categoryId` = 94")
    }
}

/**
 * 全部迁移，按版本顺序排列，由 `app/di/DatabaseModule` 的 `addMigrations(*ALL_MIGRATIONS)` 生效。
 *
 * NOTE: 本常量必须声明在它引用的迁移**之后** —— Kotlin 顶层属性按声明顺序初始化，
 * 反过来写会直接编译报错（`Variable must be initialized`）。
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
