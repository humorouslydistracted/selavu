package com.selavu.app.ui.quickactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.selavu.app.util.AmountValidationResult
import com.selavu.app.util.validateAmount
import com.selavu.app.util.validateItemName
import kotlinx.coroutines.launch

private data class QuickActionDraft(
    val key: Int,
    val id: Int,
    val name: String,
    val amountText: String
) {
    val isBlank: Boolean get() = name.isBlank() && amountText.isBlank()
    val trimmedName: String get() = name.trim()
    val nameError: String? get() = if (name.isBlank()) null else validateItemName(trimmedName, emptyList())
    val amountError: String?
        get() = if (amountText.isBlank()) null
        else (validateAmount(amountText) as? AmountValidationResult.Error)?.message

    /** Complete and individually valid (ignoring duplicates). */
    val isValid: Boolean
        get() = name.isNotBlank() && amountText.isNotBlank() &&
            validateItemName(trimmedName, emptyList()) == null &&
            validateAmount(amountText) is AmountValidationResult.Valid

    /** Case-insensitive name + numeric value, or null when the row is not valid. */
    val duplicateKey: Pair<String, Double>?
        get() = if (isValid) trimmedName.lowercase() to amountText.toDouble() else null
}

@Composable
fun QuickActionsScreen(
    currencySymbol: String,
    viewModel: QuickActionsViewModel = hiltViewModel()
) {
    val savedItemNames by viewModel.savedItemNames.collectAsState()
    val drafts = remember { mutableStateListOf<QuickActionDraft>() }
    var loaded by remember { mutableStateOf(false) }
    var nextKey by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    suspend fun reloadFromDb() {
        val existing = viewModel.loadQuickActions()
        drafts.clear()
        existing.forEach {
            drafts.add(
                QuickActionDraft(
                    key = nextKey++,
                    id = it.id,
                    name = it.name,
                    amountText = trimAmount(it.amount)
                )
            )
        }
        loaded = true
    }

    LaunchedEffect(Unit) { reloadFromDb() }

    val duplicateKeys: Set<Int> = remember(drafts.toList()) {
        drafts.filter { it.duplicateKey != null }
            .groupBy { it.duplicateKey }
            .values
            .filter { it.size > 1 }
            .flatten()
            .map { it.key }
            .toSet()
    }

    val activeDrafts = drafts.filter { !it.isBlank }
    val canSave = loaded && duplicateKeys.isEmpty() && activeDrafts.all { it.isValid }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(16.dp)
        ) {
            Text(
                "Create tap-to-log shortcuts. Each one appears on Home as \"Name: value\" and logs " +
                    "an expense for today when tapped. Same name with a different value is fine; " +
                    "the same name and value twice is not.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    drafts.add(QuickActionDraft(key = nextKey++, id = 0, name = "", amountText = ""))
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add quick action")
            }
            Spacer(Modifier.height(12.dp))

            if (loaded && drafts.isEmpty()) {
                Text(
                    "No quick actions yet. Tap \"Add quick action\" to create one, e.g. Tea with 15.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(drafts, key = { _, d -> d.key }) { index, draft ->
                    QuickActionRow(
                        draft = draft,
                        currencySymbol = currencySymbol,
                        savedItemNames = savedItemNames,
                        isDuplicate = draft.key in duplicateKeys,
                        onNameChange = { newValue ->
                            val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,'-_"
                            if (newValue.all { it in allowed }) {
                                drafts[index] = draft.copy(name = newValue)
                            }
                        },
                        onAmountChange = { drafts[index] = draft.copy(amountText = it) },
                        onDelete = { drafts.removeAt(index) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            if (duplicateKeys.isNotEmpty()) {
                Text(
                    "Fix the highlighted duplicates before saving.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Button(
                onClick = {
                    val rows = activeDrafts.map { Triple(it.id, it.trimmedName, it.amountText.toDouble()) }
                    viewModel.save(rows) {
                        scope.launch {
                            // Reload so new rows pick up their ids (avoids duplicate inserts on re-save).
                            reloadFromDb()
                            snackbarHostState.showSnackbar("Quick actions saved")
                        }
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save")
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionRow(
    draft: QuickActionDraft,
    currencySymbol: String,
    savedItemNames: List<String>,
    isDuplicate: Boolean,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val nameError = draft.nameError
    val amountError = draft.amountError

    var menuExpanded by remember { mutableStateOf(false) }
    val typed = draft.name.trim()
    val isSavedItem = savedItemNames.any { it.equals(typed, ignoreCase = true) }
    // Show everything when blank or already an exact pick; otherwise filter by what's typed.
    val suggestions = if (typed.isEmpty() || isSavedItem) {
        savedItemNames
    } else {
        savedItemNames.filter { it.contains(typed, ignoreCase = true) }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = menuExpanded && suggestions.isNotEmpty(),
                onExpandedChange = { menuExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = {
                        onNameChange(it)
                        menuExpanded = true
                    },
                    placeholder = { Text("Item name") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = menuExpanded && suggestions.isNotEmpty()
                        )
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    singleLine = true,
                    isError = nameError != null || isDuplicate,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                ExposedDropdownMenu(
                    expanded = menuExpanded && suggestions.isNotEmpty(),
                    onDismissRequest = { menuExpanded = false }
                ) {
                    suggestions.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onNameChange(name)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = draft.amountText,
                onValueChange = onAmountChange,
                placeholder = { Text("Value") },
                prefix = { Text("$currencySymbol ") },
                modifier = Modifier.width(120.dp),
                singleLine = true,
                isError = amountError != null || isDuplicate,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                )
            )
            IconButton(onClick = onDelete, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Delete quick action")
            }
        }
        val message = when {
            isDuplicate -> "Duplicate: same name and value"
            nameError != null -> nameError
            amountError != null -> amountError
            else -> null
        }
        if (message != null) {
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        } else if (typed.isNotEmpty() && !isSavedItem && draft.nameError == null) {
            Text(
                "Not a saved item. Will be logged under Others.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

/** 15.0 -> "15", 12.5 -> "12.5" */
private fun trimAmount(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
