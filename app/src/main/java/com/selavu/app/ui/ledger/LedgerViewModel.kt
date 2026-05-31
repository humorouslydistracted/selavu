package com.selavu.app.ui.ledger

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selavu.app.data.local.entity.ExpenseEntity
import com.selavu.app.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class DateMode { SINGLE, RANGE }
enum class SortMode { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }
enum class GroupMode { BY_NAME, BY_DATE, NONE }

private data class LedgerFilterState(
    val itemIds: Set<Int> = emptySet(),
    val dateMode: DateMode = DateMode.RANGE,
    val singleDate: LocalDate? = null,
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val sortMode: SortMode = SortMode.DATE_DESC
)

private data class DateFilterState(
    val dateMode: DateMode,
    val singleDate: LocalDate?,
    val fromDate: LocalDate?,
    val toDate: LocalDate?
)

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val savedItems = repository.allItems.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _selectedItemIdsFilter = MutableStateFlow<Set<Int>>(emptySet())
    val selectedItemIdsFilter = _selectedItemIdsFilter.asStateFlow()

    private val _dateMode = MutableStateFlow(DateMode.RANGE)
    val dateMode = _dateMode.asStateFlow()

    private val _singleDate = MutableStateFlow<LocalDate?>(null)
    val singleDate = _singleDate.asStateFlow()

    private val _fromDate = MutableStateFlow<LocalDate?>(null)
    val fromDate = _fromDate.asStateFlow()

    private val _toDate = MutableStateFlow<LocalDate?>(null)
    val toDate = _toDate.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE_DESC)
    val sortMode = _sortMode.asStateFlow()

    private val _groupMode = MutableStateFlow(GroupMode.BY_DATE)
    val groupMode = _groupMode.asStateFlow()

    init {
        savedStateHandle.get<String>("fromDate")?.let { date ->
            _fromDate.value = LocalDate.parse(date)
            _dateMode.value = DateMode.RANGE
        }
        savedStateHandle.get<String>("toDate")?.let { date ->
            _toDate.value = LocalDate.parse(date)
            _dateMode.value = DateMode.RANGE
        }
        // Default to last 30 days if no dates provided
        if (_fromDate.value == null && _toDate.value == null) {
            val today = LocalDate.now()
            _fromDate.value = today.minusDays(30)
            _toDate.value = today
            _dateMode.value = DateMode.RANGE
        }
    }

    private val dateFilterState: Flow<DateFilterState> = combine(
        _dateMode,
        _singleDate,
        _fromDate,
        _toDate
    ) { dateMode, single, from, to ->
        DateFilterState(dateMode, single, from, to)
    }

    private val filterState: Flow<LedgerFilterState> = combine(
        _selectedItemIdsFilter,
        dateFilterState,
        _sortMode
    ) { itemIds, dates, sort ->
        LedgerFilterState(
            itemIds = itemIds,
            dateMode = dates.dateMode,
            singleDate = dates.singleDate,
            fromDate = dates.fromDate,
            toDate = dates.toDate,
            sortMode = sort
        )
    }

    val filteredExpenses: StateFlow<List<ExpenseEntity>> = combine(
        repository.allExpenses,
        filterState
    ) { expenses, filters ->
        applyFilters(expenses, filters)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun applyFilters(
        expenses: List<ExpenseEntity>,
        filters: LedgerFilterState
    ): List<ExpenseEntity> {
        var filtered = expenses

        if (filters.itemIds.isNotEmpty()) {
            filtered = filtered.filter { expense ->
                val isOthersSelected = filters.itemIds.contains(-1)
                val isItemMatch = expense.itemId != null && filters.itemIds.contains(expense.itemId!!)
                val isOthersMatch = isOthersSelected && expense.itemId == null
                isItemMatch || isOthersMatch
            }
        }

        filtered = when (filters.dateMode) {
            DateMode.SINGLE -> {
                if (filters.singleDate != null) {
                    filtered.filter { it.date == filters.singleDate.toString() }
                } else {
                    filtered
                }
            }
            DateMode.RANGE -> {
                filtered.filter { expense ->
                    val date = LocalDate.parse(expense.date)
                    val afterFrom = filters.fromDate == null || !date.isBefore(filters.fromDate)
                    val beforeTo = filters.toDate == null || !date.isAfter(filters.toDate)
                    afterFrom && beforeTo
                }
            }
        }

        return when (filters.sortMode) {
            SortMode.DATE_DESC -> filtered.sortedWith(
                compareByDescending<ExpenseEntity> { it.date }.thenByDescending { it.createdAt }
            )
            SortMode.DATE_ASC -> filtered.sortedWith(
                compareBy<ExpenseEntity> { it.date }.thenBy { it.createdAt }
            )
            SortMode.AMOUNT_DESC -> filtered.sortedByDescending { it.amount }
            SortMode.AMOUNT_ASC -> filtered.sortedBy { it.amount }
        }
    }

    fun toggleItemFilter(id: Int) {
        _selectedItemIdsFilter.update { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun clearItemFilters() {
        _selectedItemIdsFilter.value = emptySet()
    }

    fun toggleDateSort() {
        _sortMode.value = when (_sortMode.value) {
            SortMode.DATE_DESC -> SortMode.DATE_ASC
            SortMode.DATE_ASC -> SortMode.DATE_DESC
            else -> SortMode.DATE_DESC
        }
    }

    fun toggleAmountSort() {
        _sortMode.value = when (_sortMode.value) {
            SortMode.AMOUNT_DESC -> SortMode.AMOUNT_ASC
            SortMode.AMOUNT_ASC -> SortMode.AMOUNT_DESC
            else -> SortMode.AMOUNT_DESC
        }
    }

    fun setDateMode(mode: DateMode) {
        _dateMode.value = mode
        _singleDate.value = null
        _fromDate.value = null
        _toDate.value = null
    }

    fun setSingleDate(date: LocalDate?) { _singleDate.value = date }
    fun setFromDate(date: LocalDate?) { _fromDate.value = date }
    fun setToDate(date: LocalDate?) { _toDate.value = date }
    fun setGroupMode(mode: GroupMode) { _groupMode.value = mode }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.updateExpense(expense)
        }
    }
}
