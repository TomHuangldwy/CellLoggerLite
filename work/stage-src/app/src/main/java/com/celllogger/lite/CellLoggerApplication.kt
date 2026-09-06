package com.celllogger.lite

import android.app.Application
import com.celllogger.lite.di.ServiceLocator

class CellLoggerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
