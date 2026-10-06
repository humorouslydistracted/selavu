package com.selavu.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.selavu.app.data.local.dao.ExpenseDao
import com.selavu.app.data.local.dao.ItemDao
import com.selavu.app.data.local.dao.QuickActionDao
import com.selavu.app.data.local.entity.ExpenseEntity
import com.selavu.app.data.local.entity.ItemEntity
import com.selavu.app.data.local.entity.QuickActionEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE expenses ADD COLUMN notes TEXT DEFAULT '' NOT NULL")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE expenses ADD COLUMN include INTEGER DEFAULT 1 NOT NULL")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `quick_actions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`created_at` TEXT NOT NULL)"
        )
    }
}

@Database(
    entities = [ItemEntity::class, ExpenseEntity::class, QuickActionEntity::class],
    version = 4,
    exportSchema = false
)
abstract class SelavuDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun quickActionDao(): QuickActionDao

    companion object {
        const val DATABASE_NAME = "selavu.db"
    }
}
