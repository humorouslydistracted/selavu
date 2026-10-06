package com.selavu.app.data.local.dao

import androidx.room.*
import com.selavu.app.data.local.entity.QuickActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickActionDao {
    @Query("SELECT * FROM quick_actions ORDER BY id ASC")
    fun getAllQuickActions(): Flow<List<QuickActionEntity>>

    @Insert
    suspend fun insertQuickAction(action: QuickActionEntity): Long

    @Update
    suspend fun updateQuickAction(action: QuickActionEntity)

    @Delete
    suspend fun deleteQuickAction(action: QuickActionEntity)

    @Query("DELETE FROM quick_actions")
    suspend fun deleteAllQuickActions()
}
