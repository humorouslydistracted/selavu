package com.selavu.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selavu.app.data.local.entity.ExpenseEntity
import com.selavu.app.data.local.entity.ItemEntity
import com.selavu.app.data.repository.ExpenseRepository
import com.selavu.app.util.levenshteinDistance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    val savedItems = repository.allItems.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _monthTotal = MutableStateFlow(0.0)
    val monthTotal = _monthTotal.asStateFlow()

    private val _todayTotal = MutableStateFlow(0.0)
    val todayTotal = _todayTotal.asStateFlow()

    init {
        refreshTotals()
    }

    fun refreshTotals() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val monthStart = today.withDayOfMonth(1).toString()
            val monthEnd = today.withDayOfMonth(today.lengthOfMonth()).toString()
            val todayStr = today.toString()

            _monthTotal.value = repository.getMonthTotal(monthStart, monthEnd) ?: 0.0
            _todayTotal.value = repository.getTodayTotal(todayStr) ?: 0.0
        }
    }

    fun saveExpense(
        itemName: String,
        amount: Double,
        date: LocalDate?,
        selectedItemId: Int?,
        notes: String
    ) {
        viewModelScope.launch {
            val expense = ExpenseEntity(
                itemName = itemName,
                itemId = selectedItemId,
                amount = amount,
                date = (date ?: LocalDate.now()).toString(),
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                notes = notes
            )
            repository.insertExpense(expense)
            refreshTotals()
        }
    }

    /**
     * Find a matching saved item using case-insensitive exact match OR
     * Levenshtein distance ≤ 2. Returns the best match or null.
     */
    suspend fun findMatchingItem(name: String): ItemEntity? {
        if (name.isBlank()) return null

        val items = repository.allItems.first()

        // 1. Try exact case-insensitive match first
        val exactMatch = items.find { it.name.equals(name, ignoreCase = true) }
        if (exactMatch != null) return exactMatch

        // 2. Try Levenshtein distance ≤ 2
        val closeMatch = items
            .filter { levenshteinDistance(it.name.lowercase(), name.lowercase()) <= 2 }
            .minByOrNull { levenshteinDistance(it.name.lowercase(), name.lowercase()) }

        return closeMatch
    }
}
