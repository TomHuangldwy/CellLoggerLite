package com.celllogger.lite.di

import android.content.Context
import androidx.room.Room
import com.celllogger.lite.collection.AttitudeProvider
import com.celllogger.lite.collection.CollectionManager
import com.celllogger.lite.collection.LocationProvider
import com.celllogger.lite.data.local.CellDatabase
import com.celllogger.lite.data.repository.CellLogRepository
import com.celllogger.lite.data.source.CellDataSource
import com.celllogger.lite.data.source.DataSourceType
import com.celllogger.lite.data.source.NativeDiagDataSource
import com.celllogger.lite.data.source.TelephonyDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Small manual service locator. A research app does not need a DI framework;
 * every component is constructed once with the application context.
 */
class ServiceLocator private constructor(appContext: Context) {

    val applicationContext: Context = appContext.applicationContext

    val collectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: CellDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            CellDatabase::class.java,
            "cell_logger.db"
        ).build()
    }

    val repository: CellLogRepository by lazy {
        CellLogRepository(database.cellSampleDao())
    }

    val telephonyDataSource: TelephonyDataSource by lazy {
        TelephonyDataSource(applicationContext)
    }

    val nativeDiagDataSource: NativeDiagDataSource by lazy {
        NativeDiagDataSource()
    }

    val locationProvider: LocationProvider by lazy {
        LocationProvider(applicationContext)
    }

    val attitudeProvider: AttitudeProvider by lazy {
        AttitudeProvider(applicationContext)
    }

    val collectionManager: CollectionManager by lazy {
        CollectionManager(this)
    }

    fun source(type: DataSourceType): CellDataSource = when (type) {
        DataSourceType.TELEPHONY -> telephonyDataSource
        DataSourceType.NATIVE_DIAG -> nativeDiagDataSource
    }

    companion object {
        lateinit var instance: ServiceLocator
            private set

        fun init(context: Context) {
            if (!::instance.isInitialized) {
                instance = ServiceLocator(context)
            }
        }
    }
}
