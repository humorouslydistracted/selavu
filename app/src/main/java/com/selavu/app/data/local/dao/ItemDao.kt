package com.selavu.app.data.local.dao

import androidx.room.*
import com.selavu.app.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY name ASC")
    fun getAllItems(): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: ItemEntity)

    @Delete
    suspend fun deleteItem(item: ItemEntity)

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Query("SELECT COUNT(*) FROM expenses WHERE item_id = :itemId")
    suspend fun getExpenseCountForItem(itemId: Int): Int

    @Query("SELECT SUM(amount) FROM expenses WHERE item_id = :itemId")
    suspend fun getTotalAmountForItem(itemId: Int): Double?

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()

    @Query("SELECT * FROM items")
    suspend fun getItemsSync(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getItemByName(name: String): ItemEntity?

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): ItemEntity?

    @Query("DELETE FROM items WHERE LOWER(name) = LOWER(:name)")
    suspend fun deleteItemByName(name: String)
}
