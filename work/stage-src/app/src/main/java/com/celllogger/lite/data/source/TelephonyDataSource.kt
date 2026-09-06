package com.celllogger.lite.data.source

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.CellInfo
import android.telephony.CellIdentityNr
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthNr
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import com.celllogger.lite.data.model.AccessTechnology
import com.celllogger.lite.data.model.CellMeasurement
import com.celllogger.lite.data.model.CellReading
import com.celllogger.lite.data.model.CollectionConfig
import com.celllogger.lite.data.model.ConnectionState
import com.celllogger.lite.data.model.SamplingTrigger
import com.celllogger.lite.util.asCqiOrNull
import com.celllogger.lite.util.asKnownIntOrNull
import com.celllogger.lite.util.asKnownLongOrNull
import com.celllogger.lite.util.dataNetworkTypeLabel
import com.celllogger.lite.util.registrationStateLabel
import com.celllogger.lite.util.serviceStateLabel
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android public-API implementation backed by:
 *  - TelephonyCallback/PhoneStateListener cell info + service state updates
 *  - a rate-limited requestCellInfoUpdate() only when the user asks or the
 *    cache is stale (>= 10 s without a modem push)
 *
 * Android 10–11 (API 29/30) use the deprecated PhoneStateListener.listen()
 * path; Android 12+ (API 31) uses TelephonyCallback. Manual refresh uses
 * requestCellInfoUpdate(Executor, CellInfoCallback), available since API 29.
 */
@SuppressLint("MissingPermission")
class TelephonyDataSource(private val context: Context) : CellDataSource {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "cell-logger-telephony").apply { isDaemon = true }
    }

    private val _availability = MutableStateFlow(
        DataSourceAvailability(false, "Not started.", DataSourceType.TELEPHONY)
    )
    private val _current = MutableStateFlow<CellReading?>(null)

    private val cacheLock = Any()
    private var cellCache: CachedCells? = null

    @Volatile
    private var serviceState: ServiceState? = null

    @Volatile
    private var dataNetworkType: Int? = null

    @Volatile
    private var telephonyManager: TelephonyManager? = null

    @Volatile
    private var running = false

    @Volatile
    private var subscriptionId: Int? = null

    private var modernCallback: ModernTelephonyCallback? = null
    private var legacyListener: LegacyPhoneStateListener? = null

    override val sourceType: DataSourceType = DataSourceType.TELEPHONY
    override val displayName: String = "Android Telephony API"
    override val availability: StateFlow<DataSourceAvailability> = _availability.asStateFlow()
    override val current: StateFlow<CellReading?> = _current.asStateFlow()

    private data class CachedCells(
        val cells: List<CellMeasurement>,
        val receivedElapsedRealtimeMs: Long,
        val sourceElapsedRealtimeMs: Long?
    )

    override fun start(config: CollectionConfig) {
        stop()

        if (!hasRequiredPermissions()) {
            _availability.value = DataSourceAvailability(
                false,
                "Missing READ_PHONE_STATE and/or ACCESS_FINE_LOCATION.",
                sourceType
            )
            return
        }

        val resolvedSubId = resolveSubscriptionId(config.subscriptionId)
        val tm = resolvedSubId?.let { subId ->
            appContext.getSystemService(TelephonyManager::class.java)
                .createForSubscriptionId(subId)
        } ?: appContext.getSystemService(TelephonyManager::class.java)

        if (tm.phoneType == TelephonyManager.PHONE_TYPE_NONE) {
            _availability.value = DataSourceAvailability(
                false,
                "No active cellular radio/subscription found on this device.",
                sourceType
            )
            return
        }

        running = true
        subscriptionId = resolvedSubId ?: defaultDataSubscriptionIdOrNull()
        telephonyManager = tm
        dataNetworkType = try {
            tm.dataNetworkType
        } catch (_: SecurityException) {
            null
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = ModernTelephonyCallback()
                modernCallback = callback
                tm.registerTelephonyCallback(callbackExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                mainHandler.post {
                    if (running) {
                        @Suppress("DEPRECATION")
                        val listener = LegacyPhoneStateListener()
                        legacyListener = listener
                        @Suppress("DEPRECATION")
                        tm.listen(
                            listener,
                            PhoneStateListener.LISTEN_CELL_INFO or
                                PhoneStateListener.LISTEN_SERVICE_STATE or
                                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        )
                    }
                }
            }
            _availability.value = DataSourceAvailability(true, "Listening.", sourceType)
        } catch (e: SecurityException) {
            running = false
            _availability.value = DataSourceAvailability(
                false,
                "Telephony callback registration denied: ${e.message}",
                sourceType
            )
        } catch (e: Exception) {
            running = false
            _availability.value = DataSourceAvailability(
                false,
                "Telephony callback error: ${e.message}",
                sourceType
            )
        }
    }

    override fun stop() {
        running = false
        val tm = telephonyManager
        modernCallback?.let { cb ->
            runCatching { tm?.unregisterTelephonyCallback(cb) }
        }
        legacyListener?.let { listener ->
            @Suppress("DEPRECATION")
            mainHandler.post { runCatching { tm?.listen(listener, PhoneStateListener.LISTEN_NONE) } }
        }
        modernCallback = null
        legacyListener = null
        telephonyManager = null
        _current.value = null
        _availability.value = DataSourceAvailability(false, "Stopped.", sourceType)
    }

    override suspend fun requestCellInfoUpdate(): CellReading {
        if (!running) {
            return CellReading.empty(sourceType.id, error = "Source is not running.")
        }
        return requestCellInfoUpdateWithCallback()
    }

    override suspend fun readCached(nowElapsedRealtimeMs: Long): CellReading {
        val tm = telephonyManager
        if (tm != null && running) {
            dataNetworkType = try {
                tm.dataNetworkType
            } catch (_: SecurityException) {
                null
            }
        }
        return buildReading(SamplingTrigger.PERIODIC_CACHE)
    }

    private suspend fun requestCellInfoUpdateWithCallback(): CellReading =
        suspendCancellableCoroutine { continuation ->
            val tm = telephonyManager
            if (tm == null || !running) {
                continuation.resume(CellReading.empty(sourceType.id, error = "Not running."))
                return@suspendCancellableCoroutine
            }
            val requestElapsed = SystemClock.elapsedRealtime()
            val requestUtc = System.currentTimeMillis()
            val callback = object : TelephonyManager.CellInfoCallback() {
                override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                    val now = SystemClock.elapsedRealtime()
                    val nowUtc = System.currentTimeMillis()
                    val reading = onFreshCellInfo(cellInfo, now)
                        .copy(
                            trigger = SamplingTrigger.MANUAL_REQUEST,
                            requestUtcMs = requestUtc,
                            callbackUtcMs = nowUtc,
                            requestElapsedRealtimeMs = requestElapsed,
                            callbackElapsedRealtimeMs = now
                        )
                    _current.value = reading
                    if (continuation.isActive) continuation.resume(reading)
                }

                override fun onError(errorCode: Int, detail: Throwable?) {
                    val now = SystemClock.elapsedRealtime()
                    val nowUtc = System.currentTimeMillis()
                    val reading = CellReading.empty(
                        sourceId = sourceType.id,
                        trigger = SamplingTrigger.MANUAL_REQUEST,
                        requestUtcMs = requestUtc,
                        callbackUtcMs = nowUtc,
                        requestElapsedRealtimeMs = requestElapsed,
                        callbackElapsedRealtimeMs = now,
                        error = "requestCellInfoUpdate error=$errorCode ${detail?.message}"
                    )
                    _current.value = reading
                    if (continuation.isActive) continuation.resume(reading)
                }
            }
            try {
                tm.requestCellInfoUpdate(callbackExecutor, callback)
            } catch (e: Exception) {
                val nowElapsed = SystemClock.elapsedRealtime()
                val nowUtc = System.currentTimeMillis()
                if (continuation.isActive) {
                    continuation.resume(
                        CellReading.empty(
                            sourceId = sourceType.id,
                            trigger = SamplingTrigger.MANUAL_REQUEST,
                            requestUtcMs = requestUtc,
                            callbackUtcMs = nowUtc,
                            requestElapsedRealtimeMs = requestElapsed,
                            callbackElapsedRealtimeMs = nowElapsed,
                            error = e.message
                        )
                    )
                }
            }
        }

    private fun onFreshCellInfo(cellInfo: List<CellInfo>, now: Long): CellReading {
        val parsed = cellInfo.mapNotNull { it.toCellMeasurement() }
        val sourceElapsed = cellInfo.sourceElapsedRealtimeMs()
        synchronized(cacheLock) {
            cellCache = CachedCells(
                cells = parsed,
                receivedElapsedRealtimeMs = now,
                sourceElapsedRealtimeMs = sourceElapsed
            )
        }
        return buildReading(
            trigger = SamplingTrigger.SYSTEM_UPDATE,
            callbackElapsedRealtimeMs = now
        )
    }

    private fun buildReading(
        trigger: SamplingTrigger,
        requestElapsedRealtimeMs: Long? = null,
        callbackElapsedRealtimeMs: Long? = null
    ): CellReading {
        val cached = synchronized(cacheLock) { cellCache }
        val ss = serviceState
        return CellReading(
            sourceId = sourceType.id,
            subscriptionId = subscriptionId,
            dataNetworkType = dataNetworkType?.let(::dataNetworkTypeLabel),
            // NR_STATE comes from NetworkRegistrationInfo#getNrState(), a
            // hidden/system API missing from the public android.jar. The
            // public-API source cannot obtain it, so it must stay null instead
            // of being inferred from dataNetworkType or guessed.
            nrState = null,
            serviceStateCode = ss?.state?.let(::serviceStateLabel),
            registrationState = registrationStateLabel(ss),
            cells = cached?.cells.orEmpty(),
            trigger = trigger,
            requestElapsedRealtimeMs = requestElapsedRealtimeMs,
            callbackElapsedRealtimeMs = callbackElapsedRealtimeMs ?: cached?.receivedElapsedRealtimeMs,
            cellInfoElapsedRealtimeMs = cached?.sourceElapsedRealtimeMs
                ?: cached?.receivedElapsedRealtimeMs
        )
    }

    private fun hasRequiredPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val phone = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        return fine && phone
    }

    private fun resolveSubscriptionId(requested: Int?): Int? {
        if (requested != null && requested > 0) return requested
        return defaultDataSubscriptionIdOrNull()
    }

    private fun defaultDataSubscriptionIdOrNull(): Int? {
        return runCatching {
            val sm = appContext.getSystemService(SubscriptionManager::class.java)
            sm.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private inner class LegacyPhoneStateListener :
        PhoneStateListener() {
        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
            val now = SystemClock.elapsedRealtime()
            val reading = onFreshCellInfo(cellInfo, now)
            _current.value = reading
        }

        override fun onServiceStateChanged(serviceState: ServiceState) {
            this@TelephonyDataSource.serviceState = serviceState
            _current.value = buildReading(SamplingTrigger.SYSTEM_UPDATE)
        }

        @Deprecated("Deprecated in Java")
        override fun onSignalStrengthsChanged(signalStrength: android.telephony.SignalStrength) {
            // Signal strengths are carried by CellInfo; no extra state needed.
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class ModernTelephonyCallback :
        TelephonyCallback(),
        TelephonyCallback.CellInfoListener,
        TelephonyCallback.ServiceStateListener {

        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
            val now = SystemClock.elapsedRealtime()
            val reading = onFreshCellInfo(cellInfo, now)
            _current.value = reading
        }

        override fun onServiceStateChanged(serviceState: ServiceState) {
            this@TelephonyDataSource.serviceState = serviceState
            _current.value = buildReading(SamplingTrigger.SYSTEM_UPDATE)
        }
    }
}

@Suppress("DEPRECATION")
private fun List<CellInfo>.sourceElapsedRealtimeMs(): Long? {
    val info = firstOrNull() ?: return null
    val tsMillis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // CellInfo#getTimestampMillis() returns elapsedRealtime milliseconds.
        runCatching { info.timestampMillis }.getOrDefault(0L)
    } else {
        // CellInfo#getTimeStamp() is deprecated but available since API 17 and
        // reports elapsedRealtime nanoseconds on Android 10-13.
        runCatching { info.timeStamp / 1_000_000L }.getOrDefault(0L)
    }
    return if (tsMillis > 0L) tsMillis else null
}

@Suppress("DEPRECATION")
private fun CellInfo.toCellMeasurement(): CellMeasurement? {
    val connectionState = when (cellConnectionStatus) {
        CellInfo.CONNECTION_PRIMARY_SERVING -> ConnectionState.PRIMARY_SERVING
        CellInfo.CONNECTION_SECONDARY_SERVING -> ConnectionState.SECONDARY_SERVING
        else -> ConnectionState.UNKNOWN
    }
    return when (this) {
        is CellInfoNr -> {
            // The SDK stub declares CellInfoNr#getCellIdentity() /
            // getCellSignalStrength() with the base types. Runtime objects are
            // CellIdentityNr / CellSignalStrengthNr (both exist since API 29),
            // so an explicit downcast is required before touching NR fields.
            val id = cellIdentity as? CellIdentityNr ?: return null
            val sig = cellSignalStrength as? CellSignalStrengthNr ?: return null
            CellMeasurement(
                accessTechnology = AccessTechnology.NR,
                connectionState = connectionState,
                isRegistered = isRegistered,
                mcc = id.mccString,
                mnc = id.mncString,
                tac = id.tac.asKnownIntOrNull(),
                cellId = id.nci.asKnownLongOrNull(),
                pci = id.pci.asKnownIntOrNull(),
                arfcn = id.nrarfcn.asKnownIntOrNull(),
                bandwidthKhz = null,
                rsrpDbm = sig.ssRsrp.asKnownIntOrNull(),
                rsrqDb = sig.ssRsrq.asKnownIntOrNull(),
                sinrDb = sig.ssSinr.asKnownIntOrNull(),
                rssiDbm = null,
                cqi = null,
                signalLevel = sig.level.asKnownIntOrNull()
            )
        }
        is CellInfoLte -> {
            val id = cellIdentity
            val sig = cellSignalStrength
            CellMeasurement(
                accessTechnology = AccessTechnology.LTE,
                connectionState = connectionState,
                isRegistered = isRegistered,
                mcc = id.mccString,
                mnc = id.mncString,
                tac = id.tac.asKnownIntOrNull(),
                cellId = if (id.ci == Integer.MAX_VALUE) null else id.ci.toLong(),
                pci = id.pci.asKnownIntOrNull(),
                arfcn = id.earfcn.asKnownIntOrNull(),
                bandwidthKhz = id.bandwidth.asKnownIntOrNull(),
                rsrpDbm = sig.rsrp.asKnownIntOrNull(),
                rsrqDb = sig.rsrq.asKnownIntOrNull(),
                sinrDb = sig.rssnr.asKnownIntOrNull(),
                rssiDbm = sig.rssi.asKnownIntOrNull(),
                cqi = sig.cqi.asCqiOrNull(),
                signalLevel = sig.level.asKnownIntOrNull()
            )
        }
        else -> null
    }
}
