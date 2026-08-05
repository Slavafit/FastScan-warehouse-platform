package com.ocr.fastscan

import android.app.Application
import com.ocr.fastscan.utils.AppDependencies

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize dependencies once for the app lifetime
        AppDependencies.init(this)
    }
}