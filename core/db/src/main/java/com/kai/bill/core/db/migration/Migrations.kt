package com.kai.bill.core.db.migration

import androidx.room.migration.Migration

/**
 * 数据库迁移集合。
 *
 * M0 阶段库版本为 1，**尚无迁移**。自 M1 起每次升版都：
 * 1. 在 `core/db/schemas/` 下核对 Room 导出的新版 schema
 * 2. 在本文件新增一条 [Migration]（命名 `MIGRATION_x_y`）
 * 3. 加进 [ALL_MIGRATIONS]，并 bump [com.kai.bill.core.db.KaiDatabase.VERSION]
 *
 * 遗漏迁移会让 Room 走 `fallbackToDestructiveMigration`，直接清空用户全部账目，
 * 这是本项目最不能接受的事故，因此务必逐步核对。
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
