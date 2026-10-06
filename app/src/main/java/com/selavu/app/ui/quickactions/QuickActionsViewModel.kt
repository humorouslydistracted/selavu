package com.selavu.app.ui.quickactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selavu.app.data.local.entity.QuickActionEntity
import com.selavu.app.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class QuickActionsViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    /** Names of the saved items from the "Item names" page, for the name picker. */
    val savedItemNames = repository.allItems
        .map { items -> items.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun loadQuickActions(): List<QuickActionEntity> = repository.allQuickActions.first()

    /** Persists the given (id, name, amount) rows. Rows with id == 0 are new. */
    fun save(rows: List<Triple<Int, String, Double>>, onDone: () -> Unit) {
        viewModelScope.launch {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            repository.saveQuickActions(
                rows.map { (id, name, amount) ->
                    QuickActionEntity(id = id, name = name, amount = amount, createdAt = now)
                }
            )
            onDone()
        }
    }
}
