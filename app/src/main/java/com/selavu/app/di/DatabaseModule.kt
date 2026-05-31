package com.selavu.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.selavu.app.data.local.MIGRATION_1_2
import com.selavu.app.data.local.SelavuDatabase
import com.selavu.app.data.local.dao.ExpenseDao
import com.selavu.app.data.local.dao.ItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SelavuDatabase {
        return Room.databaseBuilder(
            context,
            SelavuDatabase::class.java,
            SelavuDatabase.DATABASE_NAME
        )
        .addMigrations(MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideItemDao(database: SelavuDatabase): ItemDao {
        return database.itemDao()
    }

    @Provides
    fun provideExpenseDao(database: SelavuDatabase): ExpenseDao {
        return database.expenseDao()
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("selavu_prefs", Context.MODE_PRIVATE)
    }
}
