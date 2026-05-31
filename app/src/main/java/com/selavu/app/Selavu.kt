package com.selavu.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import android.util.Log

@HiltAndroidApp
class Selavu : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("SelavuInit", "Application onCreate called")
    }
}
