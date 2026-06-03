package com.selavu.app.ui.home

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.selavu.app.data.local.entity.ItemEntity
import com.selavu.app.ui.theme.TagSavedItemBg
import com.selavu.app.ui.theme.TagSavedItemText
import com.selavu.app.util.AmountValidationResult
import com.selavu.app.util.validateAmount
import com.selavu.app.util.validateNotes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    currencySymbol: String,
    onNavigateToLedger: () -> Unit,
    onRequestStoragePermission: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val savedItems by viewModel.savedItems.collectAsState()
    val monthTotal by viewModel.monthTotal.collectAsState()
    val todayTotal by viewModel.todayTotal.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showItemPopup by remember { mutableStateOf<ItemEntity?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshTotals()
        val prefs = context.getSharedPreferences("selavu_prefs", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("permissionDialogShown", false)) {
            showPermissionDialog = true
            prefs.edit().putBoolean("permissionDialogShown", true).apply()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTotals()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MonthlySummaryBanner(
                monthTotal = monthTotal,
                todayTotal = todayTotal,
                currencySymbol = currencySymbol,
                onClick = onNavigateToLedger
            )

            if (savedItems.isNotEmpty()) {
                Text(
                    "QUICK ADD",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    savedItems.forEach { item ->
                        SuggestionChip(
                            onClick = { showItemPopup = item },
                            label = { Text(item.name) },
                            shape = RoundedCornerShape(20.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = TagSavedItemBg,
                                labelColor = TagSavedItemText
                            )
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            Text(
                "MANUAL ENTRY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ManualEntryForm(
                currencySymbol = currencySymbol,
                onSave = { name, amount, date, itemId, notes, include ->
                    viewModel.saveExpense(name, amount, date, itemId, notes, include)
                    scope.launch {
                        snackbarHostState.showSnackbar("Saved")
                    }
                },
                findMatchingItem = { viewModel.findMatchingItem(it) },
                serverItems = savedItems
            )
        }

        if (showItemPopup != null) {
            ItemPopup(
                item = showItemPopup!!,
                currencySymbol = currencySymbol,
                onDismiss = { showItemPopup = null },
                onSave = { amount, date, notes, include ->
                    viewModel.saveExpense(showItemPopup!!.name, amount, date, showItemPopup!!.id, notes, include)
                    showItemPopup = null
                    scope.launch {
                        snackbarHostState.showSnackbar("Saved")
                    }
                }
            )
        }

        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { },
                icon = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                title = { Text("Allow storage access?") },
                text = {
                    Text(
                        """Selavu would like to save an automatic backup of your expenses to your device storage.

This is used only for backup. No data is read from other folders, and nothing is uploaded anywhere."""
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionDialog = false
                            onRequestStoragePermission()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Allow")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showPermissionDialog = false
                            val prefs = context.getSharedPreferences("selavu_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putBoolean("backupEnabled", false).apply()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Not now")
                    }
                }
            )
        }
    }
}

@Composable
fun MonthlySummaryBanner(
    monthTotal: Double,
    todayTotal: Double,
    currencySymbol: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Spent this month",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$currencySymbol ${String.format("%,.0f", monthTotal)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$currencySymbol ${String.format("%,.0f", todayTotal)}",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

@Composable
fun ManualEntryForm(
    currencySymbol: String,
    onSave: (String, Double, LocalDate?, Int?, String, Boolean) -> Unit,
    findMatchingItem: suspend (String) -> ItemEntity?,
    serverItems: List<ItemEntity>
) {
    var itemName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var matchedItem by remember { mutableStateOf<ItemEntity?>(null) }
    var selectedItemId by remember { mutableStateOf<Int?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var hasAmountFocused by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var notesError by remember { mutableStateOf<String?>(null) }
    var include by remember { mutableStateOf(true) }

    val context = LocalContext.current

    LaunchedEffect(itemName) {
        delay(300)
        if (itemName.isBlank()) {
            matchedItem = null
            selectedItemId = null
        } else {
            matchedItem = findMatchingItem(itemName)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.imePadding()
    ) {
        OutlinedTextField(
            value = itemName,
            onValueChange = { newValue ->
                val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,'-_"
                if (newValue.all { it in allowed }) {
                    itemName = newValue
                }
            },
            placeholder = { Text("Item name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            isError = itemName.equals("Others", ignoreCase = true)
        )

        if (itemName.equals("Others", ignoreCase = true)) {
            Text(
                "'Others' is a reserved name",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }

        matchedItem?.let { item ->
            if (selectedItemId == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Match found: ${item.name}", modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            itemName = item.name
                            selectedItemId = item.id
                            matchedItem = null
                        }) {
                            Text("Use it")
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            placeholder = { Text("Amount") },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (hasAmountFocused && !focusState.isFocused) {
                        val result = validateAmount(amountText)
                        amountError = if (result is AmountValidationResult.Error) result.message else null
                    }
                    if (focusState.isFocused) {
                        hasAmountFocused = true
                    }
                },
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            isError = amountError != null
        )

        if (amountError != null) {
            Text(
                amountError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }

        OutlinedTextField(
            value = notes,
            onValueChange = {
                if (it.length <= 50) {
                    notes = it
                    notesError = validateNotes(it)
                }
            },
            placeholder = { Text("Notes (optional)") },
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

        OutlinedTextField(
            value = selectedDate?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "",
            onValueChange = {},
            placeholder = { Text("Date (optional — defaults to today)") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val today = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> selectedDate = LocalDate.of(y, m + 1, d) },
                        today.get(Calendar.YEAR),
                        today.get(Calendar.MONTH),
                        today.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
            enabled = false,
            shape = RoundedCornerShape(8.dp),
            trailingIcon = {
                Row {
                    if (selectedDate != null) {
                        IconButton(onClick = { selectedDate = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear date")
                        }
                    }
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = include,
                onCheckedChange = { include = it }
            )
            Text("Include in total", style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = {
                val validation = validateAmount(amountText)
                if (validation is AmountValidationResult.Error) {
                    amountError = validation.message
                } else {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    onSave(itemName, amount, selectedDate, selectedItemId, notes, include)
                    itemName = ""
                    amountText = ""
                    selectedDate = null
                    selectedItemId = null
                    amountError = null
                    hasAmountFocused = false
                    notes = ""
                    notesError = null
                    include = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = itemName.isNotBlank() &&
                    !itemName.equals("Others", ignoreCase = true) &&
                    validateAmount(amountText) is AmountValidationResult.Valid &&
                    notesError == null,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Save")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemPopup(
    item: ItemEntity,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (Double, LocalDate?, String, Boolean) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var notes by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }
    var notesError by remember { mutableStateOf<String?>(null) }
    var hasAmountFocused by remember { mutableStateOf(false) }
    var include by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val amountFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        try { amountFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Add amount · ${item.name}", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                prefix = { Text("$currencySymbol ", fontWeight = FontWeight.Bold) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocusRequester)
                    .onFocusChanged { focusState ->
                        if (hasAmountFocused && !focusState.isFocused) {
                            val result = validateAmount(amountText)
                            amountError = if (result is AmountValidationResult.Error) result.message else null
                        }
                        if (focusState.isFocused) hasAmountFocused = true
                    },
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val validation = validateAmount(amountText)
                        if (validation is AmountValidationResult.Valid) {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            onSave(amount, selectedDate, notes, include)
                        } else {
                            amountError = (validation as AmountValidationResult.Error).message
                        }
                    }
                ),
                shape = RoundedCornerShape(8.dp),
                isError = amountError != null,
                singleLine = true
            )

            if (amountError != null) {
                Text(
                    amountError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = {
                    if (it.length <= 50) {
                        notes = it
                        notesError = validateNotes(it)
                    }
                },
                placeholder = { Text("Notes (optional)") },
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

            OutlinedTextField(
                value = selectedDate?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "",
                onValueChange = {},
                placeholder = { Text("Date (optional — defaults to today)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val today = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, y, m, d -> selectedDate = LocalDate.of(y, m + 1, d) },
                            today.get(Calendar.YEAR),
                            today.get(Calendar.MONTH),
                            today.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                enabled = false,
                shape = RoundedCornerShape(8.dp),
                trailingIcon = {
                    Row {
                        if (selectedDate != null) {
                            IconButton(onClick = { selectedDate = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear date")
                            }
                        }
                        Icon(Icons.Default.DateRange, contentDescription = null)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = include,
                    onCheckedChange = { include = it }
                )
                Text("Include in total", style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    val validation = validateAmount(amountText)
                    if (validation is AmountValidationResult.Error) {
                        amountError = validation.message
                    } else {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        onSave(amount, selectedDate, notes, include)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = validateAmount(amountText) is AmountValidationResult.Valid,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save")
            }
        }
    }
}
