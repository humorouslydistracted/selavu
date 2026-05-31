package com.selavu.app.data.local.dao

import androidx.room.*
import com.selavu.app.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, created_at DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("""
        SELECT SUM(amount) FROM expenses 
        WHERE date >= :startDate AND date <= :endDate
    """)
    suspend fun getTotalForDateRange(startDate: String, endDate: String): Double?

    @Query("SELECT SUM(amount) FROM expenses WHERE date = :date")
    suspend fun getTotalForDate(date: String): Double?

    @Query("""
        SELECT SUM(amount) FROM expenses 
        WHERE date >= :monthStart AND date <= :monthEnd
    """)
    suspend fun getMonthTotal(monthStart: String, monthEnd: String): Double?

    @Query("SELECT COUNT(*) FROM expenses WHERE date = :date")
    suspend fun getCountForDate(date: String): Int

    @Query("SELECT SUM(amount) FROM expenses WHERE date = :todayDate")
    suspend fun getTodayTotal(todayDate: String): Double?

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllExpenses(expenses: List<ExpenseEntity>): LongArray
}
