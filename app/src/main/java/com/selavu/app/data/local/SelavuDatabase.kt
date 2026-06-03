package com.selavu.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.selavu.app.data.local.dao.ExpenseDao
import com.selavu.app.data.local.dao.ItemDao
import com.selavu.app.data.local.entity.ExpenseEntity
import com.selavu.app.data.local.entity.ItemEntity

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

@Database(entities = [ItemEntity::class, ExpenseEntity::class], version = 3, exportSchema = false)
abstract class SelavuDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        const val DATABASE_NAME = "selavu.db"
    }
}
