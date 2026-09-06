package com.celllogger.lite.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.celllogger.lite.R
import com.celllogger.lite.data.model.CollectionConfig
import com.celllogger.lite.data.model.ExperimentId
import com.celllogger.lite.di.ServiceLocator

/**
 * Keeps one user-initiated measurement session alive with the screen off.
 * specialUse is the correct foreground-service type for this research use case
 * on Android 14+; the subtype is declared in AndroidManifest.xml.
 */
class CellLoggerService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val config = intent.buildConfig()
                val manager = ServiceLocator.instance.collectionManager
                manager.setAppVersion(appVersion())
                manager.start(config)
                if (!manager.running.value) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_STOP -> {
                ServiceLocator.instance.collectionManager.stop()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (ServiceLocator.instance.collectionManager.running.value) {
            ServiceLocator.instance.collectionManager.stop()
        }
        super.onDestroy()
    }

    private fun appVersion(): String? = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Signal measurement session is running")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Signal measurement",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun Intent.buildConfig(): CollectionConfig {
        val experiment = ExperimentId.entries.firstOrNull {
            it.name == getStringExtra(EXTRA_EXPERIMENT)
        } ?: ExperimentId.CUSTOM
        val interval = getIntExtra(EXTRA_INTERVAL_MS, 2_000)
        val subscriptionId = getIntExtra(EXTRA_SUBSCRIPTION_ID, -1)
        val note = getStringExtra(EXTRA_NOTE).orEmpty()
        return CollectionConfig.create(
            experimentId = experiment,
            sampleIntervalMs = interval,
            subscriptionId = subscriptionId.takeIf { it > 0 },
            note = note
        )
    }

    companion object {
        private const val CHANNEL_ID = "cell_logger_signal"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "com.celllogger.lite.action.START"
        private const val ACTION_STOP = "com.celllogger.lite.action.STOP"
        private const val EXTRA_EXPERIMENT = "experiment"
        private const val EXTRA_INTERVAL_MS = "interval_ms"
        private const val EXTRA_SUBSCRIPTION_ID = "subscription_id"
        private const val EXTRA_NOTE = "note"

        fun start(context: Context, config: CollectionConfig) {
            val intent = Intent(context, CellLoggerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_EXPERIMENT, config.experimentId.name)
                putExtra(EXTRA_INTERVAL_MS, config.sampleIntervalMs)
                putExtra(EXTRA_SUBSCRIPTION_ID, config.subscriptionId ?: -1)
                putExtra(EXTRA_NOTE, config.note)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CellLoggerService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
