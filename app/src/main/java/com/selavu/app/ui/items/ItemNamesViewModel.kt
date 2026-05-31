package com.selavu.app.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selavu.app.data.local.entity.ItemEntity
import com.selavu.app.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ItemNamesViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    val items = repository.allItems.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    init {
        viewModelScope.launch {
            repository.deleteItemByName("Others")
        }
    }

    fun addItem(name: String) {
        viewModelScope.launch {
            val item = ItemEntity(
                name = name,
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
            repository.insertItem(item)
        }
    }

    fun deleteItem(item: ItemEntity) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    suspend fun getItemStats(itemId: Int): Pair<Int, Double> {
        val count = repository.getExpenseCountForItem(itemId)
        val total = repository.getTotalAmountForItem(itemId) ?: 0.0
        return count to total
    }
}
