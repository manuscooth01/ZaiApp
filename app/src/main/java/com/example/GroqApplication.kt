package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Process
import com.example.data.database.AppDatabase

class GroqApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            android.util.Log.e("GroqApp", "Crash: ${throwable.message}")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                AppDatabase.getInstance(this).clearAllTables()
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                System.gc()
            }
        }
    }
}
