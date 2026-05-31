package com.selavu.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onRequestBackupPermission: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val backupEnabled by viewModel.backupEnabled.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()

    var showClearConfirm by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var importPreviewCount by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBackupEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(importMessage) {
        importMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearImportMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) viewModel.exportCsv(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importPreviewCount = viewModel.previewImportCsv(uri)
                pendingImportUri = uri
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection("Backup") {
                SettingsRow(
                    title = "CSV backup",
                    subtitle = if (backupEnabled) "Auto backup on every change" else "Backup is disabled. Tap to enable.",
                    onClick = if (!backupEnabled) {
                        { onRequestBackupPermission() }
                    } else null,
                    action = {
                        Switch(
                            checked = backupEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    onRequestBackupPermission()
                                } else {
                                    viewModel.setBackupEnabled(false)
                                }
                            }
                        )
                    }
                )
                SettingsRow(
                    title = "Export CSV",
                    subtitle = "Save a manual backup",
                    onClick = {
                        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                        exportLauncher.launch("selavu_backup_$today.csv")
                    },
                    action = { Icon(Icons.Default.Download, contentDescription = null) }
                )
                SettingsRow(
                    title = "Import CSV",
                    subtitle = "Restore data from file",
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "application/csv",
                                "application/vnd.ms-excel",
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    action = { Icon(Icons.Default.Upload, contentDescription = null) }
                )
            }

            SettingsSection("Display") {
                SettingsRow(
                    title = "Currency symbol",
                    subtitle = "Current: $currencySymbol",
                    onClick = { showCurrencyDialog = true },
                    action = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                )
            }

            SettingsSection("Data") {
                SettingsRow(
                    title = "Clear all data",
                    subtitle = "Permanently delete everything",
                    onClick = { showClearConfirm = true },
                    action = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }

            Spacer(Modifier.weight(1f))
            Text(
                "Selavu v1.0",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Clear all data?") },
                text = {
                    Text("This will permanently delete all your expenses and saved item names. This cannot be undone.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllData()
                            showClearConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Delete everything")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showCurrencyDialog) {
            CurrencyDialog(
                current = currencySymbol,
                onDismiss = { showCurrencyDialog = false },
                onSelect = {
                    viewModel.setCurrencySymbol(it)
                    showCurrencyDialog = false
                }
            )
        }

        if (pendingImportUri != null) {
            AlertDialog(
                onDismissRequest = { pendingImportUri = null },
                title = { Text("Import CSV?") },
                text = {
                    Text("This will add $importPreviewCount expenses to your ledger. Continue?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.importCsv(pendingImportUri!!)
                            pendingImportUri = null
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { pendingImportUri = null },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        action()
    }
}

@Composable
fun CurrencyDialog(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val presetOptions = listOf("₹", "$", "€", "£")
    var customText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select currency") },
        text = {
            Column {
                presetOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == current, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(option)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Custom:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                OutlinedTextField(
                    value = customText,
                    onValueChange = { if (it.length <= 3) customText = it },
                    placeholder = { Text("e.g. ¥") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                if (customText.isNotBlank()) {
                    TextButton(
                        onClick = { onSelect(customText) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Use custom")
                    }
                }
            }
        },
        confirmButton = {}
    )
}
