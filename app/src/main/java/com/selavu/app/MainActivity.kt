package com.selavu.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.selavu.app.data.repository.ExpenseRepository
import com.selavu.app.ui.home.HomeScreen
import com.selavu.app.ui.items.ItemNamesScreen
import com.selavu.app.ui.ledger.LedgerScreen
import com.selavu.app.ui.settings.SettingsScreen
import com.selavu.app.ui.theme.SelavuTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var expenseRepository: ExpenseRepository

    private var onPermissionDeniedCallback: (() -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val prefs = getSharedPreferences("selavu_prefs", MODE_PRIVATE)
        if (isGranted) {
            prefs.edit().putBoolean("backupEnabled", true).apply()
            lifecycleScope.launch { expenseRepository.writeCsvBackup() }
        } else {
            prefs.edit().putBoolean("backupEnabled", false).apply()
            onPermissionDeniedCallback?.invoke()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SelavuTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }

                val prefs = remember { getSharedPreferences("selavu_prefs", MODE_PRIVATE) }
                var currencySymbol by remember { mutableStateOf(prefs.getString("currency_symbol", "₹") ?: "₹") }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route?.substringBefore("?")

                LaunchedEffect(currentRoute) {
                    currencySymbol = prefs.getString("currency_symbol", "₹") ?: "₹"
                }

                var showPermissionDeniedSnackbar by remember { mutableStateOf(false) }
                LaunchedEffect(showPermissionDeniedSnackbar) {
                    if (showPermissionDeniedSnackbar) {
                        snackbarHostState.showSnackbar("Backup disabled. You can enable it in Settings.")
                        showPermissionDeniedSnackbar = false
                    }
                }

                var showPermissionDialog by remember { mutableStateOf(false) }
                var showPermanentlyDeniedDialog by remember { mutableStateOf(false) }

                onPermissionDeniedCallback = { showPermissionDeniedSnackbar = true }

                val requestBackupPermission: () -> Unit = {
                    requestStoragePermission(
                        onDenied = { showPermissionDeniedSnackbar = true },
                        onPermanentlyDenied = { showPermanentlyDeniedDialog = true }
                    )
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "Selavu",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.clickable {
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        Icons.Default.Menu,
                                        contentDescription = "Menu",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                ) { innerPadding ->
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = true,
                        drawerContent = {
                            ModalDrawerSheet {
                                DrawerContent(navController = navController) {
                                    scope.launch { drawerState.close() }
                                }
                            }
                        }
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("home") {
                                HomeScreen(
                                    currencySymbol = currencySymbol,
                                    onNavigateToLedger = {
                                        val today = LocalDate.now()
                                        val thirtyDaysAgo = today.minusDays(30)
                                        navController.navigate("ledger?fromDate=$thirtyDaysAgo&toDate=$today")
                                    },
                                    onRequestStoragePermission = requestBackupPermission
                                )
                            }
                            composable(
                                route = "ledger?fromDate={fromDate}&toDate={toDate}",
                                arguments = listOf(
                                    navArgument("fromDate") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    },
                                    navArgument("toDate") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) {
                                LedgerScreen(currencySymbol = currencySymbol)
                            }
                            composable("items") {
                                ItemNamesScreen(currencySymbol = currencySymbol)
                            }
                            composable("settings") {
                                SettingsScreen(
                                    onRequestBackupPermission = requestBackupPermission
                                )
                            }
                        }
                    }
                }

                if (showPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionDialog = false },
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
                            Button(onClick = {
                                showPermissionDialog = false
                                requestBackupPermission()
                            }) {
                                Text("Allow")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = {
                                showPermissionDialog = false
                                prefs.edit().putBoolean("backupEnabled", false).apply()
                            }) {
                                Text("Not now")
                            }
                        }
                    )
                }

                if (showPermanentlyDeniedDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermanentlyDeniedDialog = false },
                        title = { Text("Storage permission required") },
                        text = {
                            Text("Backup needs storage access. Please enable Storage permission in system Settings → Apps → Selavu → Permissions.")
                        },
                        confirmButton = {
                            Button(onClick = {
                                showPermanentlyDeniedDialog = false
                                startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", packageName, null)
                                    )
                                )
                            }) {
                                Text("Open Settings")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPermanentlyDeniedDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun requestStoragePermission(
        onDenied: () -> Unit,
        onPermanentlyDenied: () -> Unit = {}
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val prefs = getSharedPreferences("selavu_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("backupEnabled", true).apply()
            lifecycleScope.launch { expenseRepository.writeCsvBackup() }
        } else {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    val prefs = getSharedPreferences("selavu_prefs", MODE_PRIVATE)
                    prefs.edit().putBoolean("backupEnabled", true).apply()
                    lifecycleScope.launch { expenseRepository.writeCsvBackup() }
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                else -> {
                    val prefs = getSharedPreferences("selavu_prefs", MODE_PRIVATE)
                    val askedBefore = prefs.getBoolean("storagePermissionAsked", false)
                    if (askedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        onPermanentlyDenied()
                    } else {
                        prefs.edit().putBoolean("storagePermissionAsked", true).apply()
                        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerContent(
    navController: NavController,
    onClose: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore("?")

    Column(modifier = Modifier.fillMaxHeight()) {
        Text(
            "Selavu",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        HorizontalDivider()
        Spacer(Modifier.weight(1f))

        NavigationDrawerItem(
            label = { Text("Item names") },
            selected = currentRoute == "items",
            onClick = {
                navController.navigate("items")
                onClose()
            },
            icon = { Icon(Icons.Default.Tag, contentDescription = null) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text("Ledger") },
            selected = currentRoute == "ledger",
            onClick = {
                navController.navigate("ledger")
                onClose()
            },
            icon = { Icon(Icons.Default.Book, contentDescription = null) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = currentRoute == "settings",
            onClick = {
                navController.navigate("settings")
                onClose()
            },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        Spacer(Modifier.height(16.dp))
    }
}
