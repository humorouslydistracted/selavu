package com.selavu.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.selavu.app.data.local.dao.ExpenseDao
import com.selavu.app.data.local.dao.ItemDao
import com.selavu.app.data.local.entity.ExpenseEntity
import com.selavu.app.data.local.entity.ItemEntity
import com.opencsv.CSVReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val itemDao: ItemDao,
    private val expenseDao: ExpenseDao,
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    val allItems = itemDao.getAllItems()
    val allExpenses = expenseDao.getAllExpenses()

    suspend fun insertItem(item: ItemEntity) {
        itemDao.insertItem(item)
        writeCsvBackup()
    }

    suspend fun deleteItem(item: ItemEntity) {
        itemDao.deleteItem(item)
        writeCsvBackup()
    }

    suspend fun getExpenseCountForItem(itemId: Int) = itemDao.getExpenseCountForItem(itemId)
    suspend fun getTotalAmountForItem(itemId: Int) = itemDao.getTotalAmountForItem(itemId)

    suspend fun insertExpense(expense: ExpenseEntity) {
        expenseDao.insertExpense(expense)
        writeCsvBackup()
    }

    suspend fun updateExpense(expense: ExpenseEntity) {
        expenseDao.updateExpense(expense)
        writeCsvBackup()
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpense(expense)
        writeCsvBackup()
    }

    suspend fun getTotalForDateRange(startDate: String, endDate: String) =
        expenseDao.getTotalForDateRange(startDate, endDate)

    suspend fun getTotalForDate(date: String) = expenseDao.getTotalForDate(date)
    suspend fun getMonthTotal(monthStart: String, monthEnd: String) =
        expenseDao.getMonthTotal(monthStart, monthEnd)

    suspend fun getTodayTotal(todayDate: String) = expenseDao.getTodayTotal(todayDate)
    suspend fun getCountForDate(date: String) = expenseDao.getCountForDate(date)

    suspend fun clearAllData() {
        expenseDao.deleteAllExpenses()
        itemDao.deleteAllItems()
        writeCsvBackup()
    }

    suspend fun getItemByName(name: String) = itemDao.getItemByName(name)
    suspend fun getItemById(id: Int) = itemDao.getItemById(id)
    suspend fun deleteItemByName(name: String) = itemDao.deleteItemByName(name)

    suspend fun previewImportCsv(uri: android.net.Uri): Int = withContext(Dispatchers.IO) {
        persistUriReadPermission(uri)
        parseCsvRows(uri).size
    }

    suspend fun importCsv(uri: android.net.Uri): Int = withContext(Dispatchers.IO) {
        persistUriReadPermission(uri)
        try {
            val rows = parseCsvRows(uri)
            var addedCount = 0
            for (row in rows) {
                val finalItemId = resolveItemId(row.itemIdStr, row.itemName, row.createdAt)
                val expenseId = row.id?.takeIf { it > 0 } ?: 0
                val expense = ExpenseEntity(
                    id = expenseId,
                    itemName = row.itemName,
                    itemId = finalItemId,
                    amount = row.amount,
                    date = row.date,
                    createdAt = row.createdAt,
                    notes = row.notes
                )
                val result = expenseDao.insertAllExpenses(listOf(expense))
                if (result.isNotEmpty() && result[0] != -1L) {
                    addedCount++
                }
            }
            writeCsvBackup()
            addedCount
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    private fun persistUriReadPermission(uri: android.net.Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }

    private data class CsvExpenseRow(
        val id: Int?,
        val itemName: String,
        val itemIdStr: String,
        val amount: Double,
        val date: String,
        val createdAt: String,
        val notes: String
    )

    private suspend fun parseCsvRows(uri: android.net.Uri): List<CsvExpenseRow> =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = CSVReader(InputStreamReader(inputStream))
                reader.readNext() // skip header
                val rows = mutableListOf<CsvExpenseRow>()
                var line: Array<String>?
                while (reader.readNext().also { line = it } != null) {
                    val parts = line!!
                    if (parts.size >= 5) {
                        val parsedId = parts[0].trim().toIntOrNull()
                        rows.add(
                            CsvExpenseRow(
                                id = parsedId,
                                itemName = parts[1],
                                itemIdStr = parts[2].trim(),
                                amount = parts[3].toDoubleOrNull() ?: 0.0,
                                date = parts[4],
                                createdAt = if (parts.size > 5) parts[5] else
                                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                notes = if (parts.size > 6) parts[6] else ""
                            )
                        )
                    }
                }
                rows
            } ?: emptyList()
        }

    private suspend fun resolveItemId(
        itemIdStr: String,
        itemName: String,
        createdAt: String
    ): Int? {
        if (itemIdStr.isEmpty()) return null

        val parsedItemId = itemIdStr.toIntOrNull() ?: return null
        val existingById = itemDao.getItemById(parsedItemId)
        if (existingById != null) return existingById.id

        val existingByName = itemDao.getItemByName(itemName)
        if (existingByName != null) return existingByName.id

        val newItem = ItemEntity(name = itemName, createdAt = createdAt)
        itemDao.insertItem(newItem)
        return itemDao.getItemByName(itemName)?.id
    }

    suspend fun exportCsv(uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val expenses = expenseDao.getAllExpenses().first()
                val writer = java.io.PrintWriter(outputStream)
                writer.println("id,item_name,item_id,amount,date,created_at,notes")
                expenses.forEach { expense ->
                    val row = listOf(
                        expense.id.toString(),
                        escapeCsv(expense.itemName),
                        expense.itemId?.toString() ?: "",
                        String.format("%.1f", expense.amount),
                        expense.date,
                        expense.createdAt,
                        escapeCsv(expense.notes)
                    ).joinToString(",")
                    writer.println(row)
                }
                writer.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun writeCsvBackup() = withContext(Dispatchers.IO) {
        val backupEnabled = sharedPreferences.getBoolean("backupEnabled", false)
        if (!backupEnabled) return@withContext

        try {
            val expenses = expenseDao.getAllExpenses().first()
            val file = File(context.filesDir, "selavu_backup.csv")
            FileWriter(file).use { writer ->
                writer.write("id,item_name,item_id,amount,date,created_at,notes\n")
                expenses.forEach { expense ->
                    val row = listOf(
                        expense.id.toString(),
                        escapeCsv(expense.itemName),
                        expense.itemId?.toString() ?: "",
                        String.format("%.1f", expense.amount),
                        expense.date,
                        expense.createdAt,
                        escapeCsv(expense.notes)
                    ).joinToString(",")
                    writer.write("$row\n")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }
}
