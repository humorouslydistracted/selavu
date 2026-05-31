package com.selavu.app.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selavu.app.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _currencySymbol = MutableStateFlow(sharedPreferences.getString("currency_symbol", "₹") ?: "₹")
    val currencySymbol = _currencySymbol.asStateFlow()

    private val _backupEnabled = MutableStateFlow(sharedPreferences.getBoolean("backupEnabled", false))
    val backupEnabled = _backupEnabled.asStateFlow()

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage = _importMessage.asStateFlow()

    fun setCurrencySymbol(symbol: String) {
        sharedPreferences.edit().putString("currency_symbol", symbol).apply()
        _currencySymbol.value = symbol
    }

    fun setBackupEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("backupEnabled", enabled).apply()
        _backupEnabled.value = enabled
        if (enabled) {
            viewModelScope.launch {
                repository.writeCsvBackup()
            }
        }
    }

    fun refreshBackupEnabled() {
        _backupEnabled.value = sharedPreferences.getBoolean("backupEnabled", false)
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }

    fun exportCsv(uri: android.net.Uri) {
        viewModelScope.launch {
            repository.exportCsv(uri)
        }
    }

    suspend fun previewImportCsv(uri: android.net.Uri): Int {
        return repository.previewImportCsv(uri)
    }

    fun importCsv(uri: android.net.Uri) {
        viewModelScope.launch {
            val added = repository.importCsv(uri)
            _importMessage.value = "Import complete. $added expenses added."
        }
    }

    fun clearImportMessage() {
        _importMessage.value = null
    }
}
