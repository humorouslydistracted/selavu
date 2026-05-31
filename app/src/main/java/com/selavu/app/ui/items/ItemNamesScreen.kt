package com.selavu.app.ui.items

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.selavu.app.data.local.entity.ItemEntity
import com.selavu.app.ui.theme.TagSavedItemBg
import com.selavu.app.ui.theme.TagSavedItemText
import com.selavu.app.util.validateItemName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemNamesScreen(
    currencySymbol: String,
    viewModel: ItemNamesViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    var showAddInline by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<ItemEntity?>(null) }
    var itemStats by remember { mutableStateOf<Pair<Int, Double>?>(null) }

    val existingNames = items.map { it.name }
    val addError = validateItemName(newItemName, existingNames)

    LaunchedEffect(itemToDelete) {
        if (itemToDelete != null) {
            itemStats = viewModel.getItemStats(itemToDelete!!.id)
        }
    }

    fun saveNewItem() {
        if (newItemName.isNotBlank() && addError == null) {
            viewModel.addItem(newItemName)
            newItemName = ""
            showAddInline = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
            OutlinedButton(
                onClick = {
                    showAddInline = true
                    newItemName = ""
                },
                modifier = Modifier.padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add item")
            }

            if (showAddInline) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newValue ->
                            val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,'-_"
                            if (newValue.all { it in allowed }) {
                                newItemName = newValue
                            }
                        },
                        placeholder = { Text("Item name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = addError != null,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveNewItem() })
                    )
                    Button(
                        onClick = { saveNewItem() },
                        enabled = newItemName.isNotBlank() && addError == null,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save")
                    }
                }
                if (addError != null) {
                    Text(
                        addError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        onDelete = { itemToDelete = item }
                    )
                }
            }
        }

    if (itemToDelete != null && itemStats != null) {
        DeleteItemDialog(
            itemName = itemToDelete!!.name,
            count = itemStats!!.first,
            total = itemStats!!.second,
            currencySymbol = currencySymbol,
            onDismiss = {
                itemToDelete = null
                itemStats = null
            },
            onConfirm = {
                viewModel.deleteItem(itemToDelete!!)
                itemToDelete = null
                itemStats = null
            }
        )
    }
}

@Composable
fun ItemRow(
    item: ItemEntity,
    onDelete: () -> Unit
) {
    Surface(
        color = TagSavedItemBg,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.name,
                color = TagSavedItemText,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = TagSavedItemText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun DeleteItemDialog(
    itemName: String,
    count: Int,
    total: Double,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$itemName\"?") },
        text = {
            Column {
                if (count == 0) {
                    Text("This item has no recorded expenses.")
                } else {
                    Text("\"$itemName\" has $count entries totalling")
                    Text(
                        "$currencySymbol ${String.format("%,.0f", total)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Text("These entries will move to Others. Their names are preserved.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
