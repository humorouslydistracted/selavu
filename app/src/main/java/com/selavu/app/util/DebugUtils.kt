package com.selavu.app.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

fun showCrashToast(context: Context, message: String) {
    Toast.makeText(context, "Crash: $message", Toast.LENGTH_LONG).show()
}
