package com.selavu.app.ui.ledger

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.selavu.app.data.local.entity.ExpenseEntity
import com.selavu.app.data.local.entity.ItemEntity
import com.selavu.app.ui.theme.*
import com.selavu.app.util.AmountValidationResult
import com.selavu.app.util.isItemNameValid
import com.selavu.app.util.validateAmount
import com.selavu.app.util.validateNotes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

private val displayDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    currencySymbol: String,
    viewModel: LedgerViewModel = hiltViewModel()
) {
    val expenses by viewModel.filteredExpenses.collectAsState()
    val savedItems by viewModel.savedItems.collectAsState()
    val selectedItemIds by viewModel.selectedItemIdsFilter.collectAsState()
    val groupMode by viewModel.groupMode.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val dateMode by viewModel.dateMode.collectAsState()
    val singleDate by viewModel.singleDate.collectAsState()
    val fromDate by viewModel.fromDate.collectAsState()
    val toDate by viewModel.toDate.collectAsState()

    val context = LocalContext.current
    var editingExpenseId by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    var dateFiltersExpanded by remember { mutableStateOf(false) }
    var groupSortExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(editingExpenseId) {
        if (editingExpenseId != null) {
            val index = expenses.indexOfFirst { it.id == editingExpenseId }
            if (index >= 0) {
                listState.animateScrollToItem(index.coerceAtLeast(0))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // Date Filters Collapsible Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dateFiltersExpanded = !dateFiltersExpanded }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Date Filters",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            if (dateFiltersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (dateFiltersExpanded) "Collapse" else "Expand"
                        )
                    }
                    AnimatedVisibility(
                        visible = dateFiltersExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                            // Row 1: Date mode
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SegmentedButton(
                                    selected = dateMode == DateMode.SINGLE,
                                    onClick = { viewModel.setDateMode(DateMode.SINGLE) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) {
                                    Text("Single date", style = MaterialTheme.typography.labelSmall)
                                }
                                SegmentedButton(
                                    selected = dateMode == DateMode.RANGE,
                                    onClick = { viewModel.setDateMode(DateMode.RANGE) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) {
                                    Text("Date range", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            // Row 2: Date selection bar
                            when (dateMode) {
                                DateMode.SINGLE -> {
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = singleDate?.format(displayDateFormatter) ?: "",
                                            onValueChange = {} ,
                                            placeholder = { Text("Select date") },
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    showDatePicker(context, singleDate ?: LocalDate.now()) {
                                                        viewModel.setSingleDate(it)
                                                    }
                                                },
                                            enabled = false,
                                            shape = RoundedCornerShape(8.dp),
                                            trailingIcon = { Icon(Icons.Default.DateRange, null) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline
                                            )
                                        )
                                        if (singleDate != null) {
                                            IconButton(onClick = { viewModel.setSingleDate(null) }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear")
                                            }
                                        }
                                    }
                                }
                                DateMode.RANGE -> {
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = fromDate?.format(displayDateFormatter) ?: "",
                                            onValueChange = {} ,
                                            placeholder = { Text("From") },
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    showDatePicker(context, fromDate ?: LocalDate.now()) {
                                                        viewModel.setFromDate(it)
                                                    }
                                                },
                                            enabled = false,
                                            shape = RoundedCornerShape(8.dp),
                                            trailingIcon = {
                                                if (fromDate != null) {
                                                    IconButton(onClick = { viewModel.setFromDate(null) }) {
                                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                                    }
                                                } else {
                                                    Icon(Icons.Default.DateRange, contentDescription = null)
                                                }
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline
                                            )
                                        )
                                        OutlinedTextField(
                                            value = toDate?.format(displayDateFormatter) ?: "",
                                            onValueChange = {} ,
                                            placeholder = { Text("To") },
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    showDatePicker(context, toDate ?: fromDate ?: LocalDate.now()) {
                                                        viewModel.setToDate(it)
                                                    }
                                                },
                                            enabled = false,
                                            shape = RoundedCornerShape(8.dp),
                                            trailingIcon = {
                                                if (toDate != null) {
                                                    IconButton(onClick = { viewModel.setToDate(null) }) {
                                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                                    }
                                                } else {
                                                    Icon(Icons.Default.DateRange, contentDescription = null)
                                                }
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                disabledBorderColor = MaterialTheme.colorScheme.outline
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Group & Sort Collapsible Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { groupSortExpanded = !groupSortExpanded }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Group & Sort",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            if (groupSortExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (groupSortExpanded) "Collapse" else "Expand"
                        )
                    }
                    AnimatedVisibility(
                        visible = groupSortExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                            // Row 3: Group controls + category dropdown
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = groupMode == GroupMode.BY_NAME,
                                    onClick = { viewModel.setGroupMode(GroupMode.BY_NAME) },
                                    label = { Text("By name", style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                )
                                FilterChip(
                                    selected = groupMode == GroupMode.BY_DATE,
                                    onClick = { viewModel.setGroupMode(GroupMode.BY_DATE) },
                                    label = { Text("By date", style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                )
                                FilterChip(
                                    selected = groupMode == GroupMode.NONE,
                                    onClick = { viewModel.setGroupMode(GroupMode.NONE) },
                                    label = { Text("None", style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                )
                                CategoryFilterDropdown(
                                    savedItems = savedItems,
                                    selectedIds = selectedItemIds,
                                    onToggle = { viewModel.toggleItemFilter(it) },
                                    onClearAll = { viewModel.clearItemFilters() },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Row 4: Sort toggles
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SortToggleButton(
                                    label = "Date",
                                    isActive = sortMode == SortMode.DATE_DESC || sortMode == SortMode.DATE_ASC,
                                    ascending = sortMode == SortMode.DATE_ASC,
                                    onClick = { viewModel.toggleDateSort() },
                                    modifier = Modifier.weight(1f)
                                )
                                SortToggleButton(
                                    label = "Amount",
                                    isActive = sortMode == SortMode.AMOUNT_DESC || sortMode == SortMode.AMOUNT_ASC,
                                    ascending = sortMode == SortMode.AMOUNT_ASC,
                                    onClick = { viewModel.toggleAmountSort() },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            val hasDateFilter = singleDate != null || fromDate != null || toDate != null
            if (hasDateFilter) {
                val filteredTotal = expenses.sumOf { it.amount }
                val dateLabel = when {
                    dateMode == DateMode.SINGLE && singleDate != null ->
                        singleDate!!.format(displayDateFormatter)
                    fromDate != null && toDate != null ->
                        "${fromDate!!.format(DateTimeFormatter.ofPattern("dd MMM"))} – ${toDate!!.format(displayDateFormatter)}"
                    fromDate != null -> "From ${fromDate!!.format(displayDateFormatter)}"
                    toDate != null -> "Until ${toDate!!.format(displayDateFormatter)}"
                    else -> ""
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(dateLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${expenses.size} entries",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "$currencySymbol ${String.format("%,.0f", filteredTotal)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val grandTotal = expenses.sumOf { it.amount }
            val showDateOnRows = groupMode != GroupMode.BY_DATE

            Box(modifier = Modifier.weight(1f)) {
                if (expenses.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No entries found")
                    }
                } else {
                    ExpenseGroupedList(
                        expenses = expenses,
                        groupMode = groupMode,
                        currencySymbol = currencySymbol,
                        filteredTotal = grandTotal,
                        showDateOnRows = showDateOnRows,
                        editingExpenseId = editingExpenseId,
                        savedItems = savedItems,
                        listState = listState,
                        onEditExpense = { editingExpenseId = it },
                        onUpdate = { viewModel.updateExpense(it) },
                        onDelete = { viewModel.deleteExpense(it) }
                    )
                }
            }

            HorizontalDivider()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$currencySymbol ${String.format("%,.0f", grandTotal)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
    }
}

@Composable
private fun CategoryFilterDropdown(
    savedItems: List<ItemEntity>,
    selectedIds: Set<Int>,
    onToggle: (Int) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (selectedIds.isEmpty()) {
        "All"
    } else {
        "${selectedIds.size} selected"
    }

    Box(modifier = modifier) {
        FilterChip(
            selected = selectedIds.isNotEmpty(),
            onClick = { expanded = true },
            label = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            modifier = Modifier
                .height(32.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            savedItems.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(item.id),
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(item.name)
                        }
                    },
                    onClick = { onToggle(item.id) }
                )
            }
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = selectedIds.contains(-1),
                            onCheckedChange = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Others")
                    }
                },
                onClick = { onToggle(-1) }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        "Clear all",
                        color = if (selectedIds.isEmpty()) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                },
                onClick = {
                    if (selectedIds.isNotEmpty()) {
                        onClearAll()
                    }
                    expanded = false
                },
                enabled = selectedIds.isNotEmpty()
            )
        }
    }
}

@Composable
private fun SortToggleButton(
    label: String,
    isActive: Boolean,
    ascending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val arrowIcon = if (ascending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    FilterChip(
        selected = isActive,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(label)
                if (isActive) {
                    Icon(
                        arrowIcon,
                        contentDescription = if (ascending) "Ascending" else "Descending",
                        modifier = Modifier.size(18.dp),
                        tint = contentColor
                    )
                }
            }
        },
        modifier = modifier,
        shape = RoundedCornerShape(8.dp)
    )
}

private fun showDatePicker(
    context: android.content.Context,
    initial: LocalDate,
    onSelected: (LocalDate) -> Unit
) {
    DatePickerDialog(
        context,
        { _, y, m, d -> onSelected(LocalDate.of(y, m + 1, d)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth
    ).show()
}

@Composable
fun ExpenseGroupedList(
    expenses: List<ExpenseEntity>,
    groupMode: GroupMode,
    currencySymbol: String,
    filteredTotal: Double,
    showDateOnRows: Boolean,
    editingExpenseId: Int?,
    savedItems: List<ItemEntity>,
    listState: LazyListState,
    onEditExpense: (Int?) -> Unit,
    onUpdate: (ExpenseEntity) -> Unit,
    onDelete: (ExpenseEntity) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
        when (groupMode) {
            GroupMode.BY_NAME -> {
                val savedExpenses = expenses.filter { it.itemId != null }
                val othersExpenses = expenses.filter { it.itemId == null }

                savedExpenses.groupBy { it.itemName }.forEach { (name, group) ->
                    val subtotal = group.sumOf { it.amount }
                    val pct = if (filteredTotal > 0) (subtotal / filteredTotal * 100) else 0.0
                    item(key = "header_saved_$name") {
                        GroupHeader(
                            title = name,
                            subtitle = "$currencySymbol ${String.format("%,.0f", subtotal)} · ${String.format("%.0f", pct)}%",
                            isOthers = false
                        )
                    }
                    items(group, key = { it.id }) { expense ->
                        ExpenseRow(
                            expense = expense,
                            currencySymbol = currencySymbol,
                            filteredTotal = filteredTotal,
                            showDate = showDateOnRows,
                            isEditing = editingExpenseId == expense.id,
                            savedItems = savedItems,
                            onStartEdit = { onEditExpense(expense.id) },
                            onCancelEdit = { onEditExpense(null) },
                            onUpdate = onUpdate,
                            onDelete = onDelete
                        )
                    }
                }

                if (othersExpenses.isNotEmpty()) {
                    val othersTotal = othersExpenses.sumOf { it.amount }
                    val othersPct = if (filteredTotal > 0) (othersTotal / filteredTotal * 100) else 0.0
                    item(key = "header_others_parent") {
                        GroupHeader(
                            title = "Others",
                            subtitle = "$currencySymbol ${String.format("%,.0f", othersTotal)} · ${String.format("%.0f", othersPct)}%",
                            isOthers = true
                        )
                    }
                    othersExpenses.groupBy { it.itemName }.forEach { (name, group) ->
                        val subtotal = group.sumOf { it.amount }
                        val pct = if (filteredTotal > 0) (subtotal / filteredTotal * 100) else 0.0
                        item(key = "header_others_$name") {
                            SubGroupHeader(
                                title = name,
                                subtitle = "$currencySymbol ${String.format("%,.0f", subtotal)} · ${String.format("%.0f", pct)}%"
                            )
                        }
                        items(group, key = { it.id }) { expense ->
                            ExpenseRow(
                                expense = expense,
                                currencySymbol = currencySymbol,
                                filteredTotal = filteredTotal,
                                showDate = showDateOnRows,
                                isEditing = editingExpenseId == expense.id,
                                savedItems = savedItems,
                                onStartEdit = { onEditExpense(expense.id) },
                                onCancelEdit = { onEditExpense(null) },
                                onUpdate = onUpdate,
                                onDelete = onDelete
                            )
                        }
                    }
                }
            }
            GroupMode.BY_DATE -> {
                expenses.groupBy { it.date }.forEach { (date, group) ->
                    val subtotal = group.sumOf { it.amount }
                    val pct = if (filteredTotal > 0) (subtotal / filteredTotal * 100) else 0.0
                    item(key = "header_$date") {
                        GroupHeader(
                            title = LocalDate.parse(date).format(displayDateFormatter),
                            subtitle = "$currencySymbol ${String.format("%,.0f", subtotal)} · ${String.format("%.0f", pct)}%",
                            isOthers = false
                        )
                    }
                    items(group, key = { it.id }) { expense ->
                        ExpenseRow(
                            expense = expense,
                            currencySymbol = currencySymbol,
                            filteredTotal = filteredTotal,
                            showDate = false,
                            isEditing = editingExpenseId == expense.id,
                            savedItems = savedItems,
                            onStartEdit = { onEditExpense(expense.id) },
                            onCancelEdit = { onEditExpense(null) },
                            onUpdate = onUpdate,
                            onDelete = onDelete
                        )
                    }
                }
            }
            GroupMode.NONE -> {
                items(expenses, key = { it.id }) { expense ->
                    ExpenseRow(
                        expense = expense,
                        currencySymbol = currencySymbol,
                        filteredTotal = filteredTotal,
                        showDate = showDateOnRows,
                        isEditing = editingExpenseId == expense.id,
                        savedItems = savedItems,
                        onStartEdit = { onEditExpense(expense.id) },
                        onCancelEdit = { onEditExpense(null) },
                        onUpdate = onUpdate,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
fun GroupHeader(title: String, subtitle: String, isOthers: Boolean) {
    val bgColor = if (isOthers) TagOthersBg else MaterialTheme.colorScheme.surfaceVariant
    Surface(modifier = Modifier.fillMaxWidth(), color = bgColor) {
        Row(
            modifier = Modifier.padding(16.dp, 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider()
}

@Composable
fun SubGroupHeader(title: String, subtitle: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExpenseRow(
    expense: ExpenseEntity,
    currencySymbol: String,
    filteredTotal: Double,
    showDate: Boolean,
    isEditing: Boolean,
    savedItems: List<ItemEntity>,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onUpdate: (ExpenseEntity) -> Unit,
    onDelete: (ExpenseEntity) -> Unit
) {
    var isDeleting by remember { mutableStateOf(false) }
    var rowVisible by remember(expense.id) { mutableStateOf(true) }

    var editAmount by remember(expense.id) { mutableStateOf(expense.amount.toString()) }
    var editItemName by remember(expense.id) { mutableStateOf(expense.itemName) }
    var editDate by remember(expense.id) { mutableStateOf(LocalDate.parse(expense.date)) }
    var editNotes by remember(expense.id) { mutableStateOf(expense.notes) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var itemNameError by remember { mutableStateOf<String?>(null) }
    var notesError by remember { mutableStateOf<String?>(null) }
    var hasAmountFocused by remember { mutableStateOf(false) }
    val amountFocusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val allowedItemNameChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,'-_"

    val isSavedItem = expense.itemId != null
    val tagBg = if (isSavedItem) TagSavedItemBg else TagOthersBg
    val tagText = if (isSavedItem) TagSavedItemText else TagOthersText
    val pct = if (filteredTotal > 0) (expense.amount / filteredTotal * 100) else 0.0
    val formattedDate = remember(expense.date) {
        LocalDate.parse(expense.date).format(displayDateFormatter)
    }

    LaunchedEffect(expense.amount, expense.date, expense.itemName, expense.notes) {
        editAmount = expense.amount.toString()
        editItemName = expense.itemName
        editDate = LocalDate.parse(expense.date)
        editNotes = expense.notes
    }

    fun validateEditItemName(name: String): String? {
        if (name.isBlank()) return "Item name cannot be empty"
        if (!isItemNameValid(name)) return "Invalid characters"
        if (name.equals("Others", ignoreCase = true)) return "'Others' is a reserved name"
        return null
    }

    AnimatedVisibility(
        visible = rowVisible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(
            animationSpec = tween(200)
        ) { fullHeight -> -fullHeight / 2 }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            if (!isEditing && !isDeleting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = tagBg, shape = RoundedCornerShape(20.dp)) {
                        Text(
                            expense.itemName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = tagText,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (expense.notes.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            expense.notes,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (showDate) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formattedDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "$currencySymbol ${String.format("%,.0f", expense.amount)}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${String.format("%.0f", pct)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = onStartEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { isDeleting = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(16.dp))
                    }
                }
            } else if (isEditing) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .imePadding(),
                    color = EditActiveBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, EditActiveBorder)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        OutlinedTextField(
                            value = editItemName,
                            onValueChange = { newValue ->
                                if (newValue.all { it in allowedItemNameChars }) {
                                    editItemName = newValue
                                    itemNameError = validateEditItemName(newValue)
                                }
                            },
                            label = { Text("Item name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            isError = itemNameError != null
                        )
                        if (itemNameError != null) {
                            Text(
                                itemNameError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            OutlinedTextField(
                                value = editAmount,
                                onValueChange = { editAmount = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(amountFocusRequester)
                                    .onFocusChanged { focusState ->
                                        if (hasAmountFocused && !focusState.isFocused) {
                                            val result = validateAmount(editAmount)
                                            amountError = if (result is AmountValidationResult.Error) result.message else null
                                        }
                                        if (focusState.isFocused) {
                                            hasAmountFocused = true
                                            scope.launch { bringIntoViewRequester.bringIntoView() }
                                        }
                                    },
                                label = { Text("Amount") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(8.dp),
                                isError = amountError != null,
                                singleLine = true
                            )
                        }

                        LaunchedEffect(isEditing) {
                            itemNameError = validateEditItemName(editItemName)
                            try { amountFocusRequester.requestFocus() } catch (_: Exception) {}
                        }

                        if (amountError != null) {
                            Text(
                                amountError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        OutlinedTextField(
                            value = editNotes,
                            onValueChange = {
                                if (it.length <= 50) {
                                    editNotes = it
                                    notesError = validateNotes(it)
                                }
                            },
                            label = { Text("Notes (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            isError = notesError != null
                        )

                        if (notesError != null) {
                            Text(
                                notesError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showDatePicker(context, editDate) { editDate = it }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Date: ${editDate.format(displayDateFormatter)}")
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {
                                val nameValidation = validateEditItemName(editItemName.trim())
                                if (nameValidation != null) {
                                    itemNameError = nameValidation
                                    return@IconButton
                                }
                                val notesValidation = validateNotes(editNotes)
                                if (notesValidation != null) {
                                    notesError = notesValidation
                                    return@IconButton
                                }
                                val validation = validateAmount(editAmount)
                                if (validation is AmountValidationResult.Error) {
                                    amountError = validation.message
                                } else {
                                    val trimmedName = editItemName.trim()
                                    val matchedItem = savedItems.find {
                                        it.name.equals(trimmedName, ignoreCase = true)
                                    }
                                    val amt = editAmount.toDoubleOrNull() ?: 0.0
                                    onUpdate(
                                        expense.copy(
                                            itemName = matchedItem?.name ?: trimmedName,
                                            itemId = matchedItem?.id,
                                            amount = amt,
                                            date = editDate.toString(),
                                            notes = editNotes
                                        )
                                    )
                                    onCancelEdit()
                                    amountError = null
                                    itemNameError = null
                                    notesError = null
                                    hasAmountFocused = false
                                }
                            }) {
                                Icon(Icons.Default.Check, "Save", tint = PrimaryTeal)
                            }
                            IconButton(onClick = {
                                onCancelEdit()
                                amountError = null
                                itemNameError = null
                                notesError = null
                                hasAmountFocused = false
                            }) {
                                Icon(Icons.Default.Close, "Cancel")
                            }
                        }
                    }
                }
            } else if (isDeleting) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = DeleteDangerBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DeleteDangerBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Delete ${expense.itemName} $currencySymbol${String.format("%,.0f", expense.amount)}?",
                            modifier = Modifier.weight(1f),
                            color = DeleteDangerText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = {
                            isDeleting = false
                            rowVisible = false
                            scope.launch {
                                delay(200)
                                onDelete(expense)
                            }
                        }) {
                            Icon(Icons.Default.Check, "Confirm", tint = PrimaryTeal)
                        }
                        IconButton(onClick = { isDeleting = false }) {
                            Icon(Icons.Default.Close, "Cancel")
                        }
                    }
                }
            }
        }
    }
}
