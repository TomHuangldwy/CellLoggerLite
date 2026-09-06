package com.celllogger.lite.collection

import android.os.SystemClock
import com.celllogger.lite.data.mapper.buildCellSample
import com.celllogger.lite.data.model.CellReading
import com.celllogger.lite.data.model.CollectionConfig
import com.celllogger.lite.data.model.SamplingTrigger
import com.celllogger.lite.data.source.CellDataSource
import com.celllogger.lite.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Session lifecycle + low-rate sampling policy.
 *
 *  1. The modem/system pushes (TelephonyCallback / PhoneStateListener) keep
 *     [CellDataSource.current] and an internal cache up to date.
 *  2. A timer (1–5 s, user configured) reads only the cached values and stores
 *     one Room row.
 *  3. requestCellInfoUpdate() is called once when a session starts, again when
 *     the user presses the manual button, and as a stale-cache fallback at most
 *     every 10 s. It is never part of the per-second hot loop.
 *
 * The whole manager runs on the application scope, so starting collection
 * through the foreground service keeps logging alive when the screen is off.
 */
class CollectionManager(private val deps: ServiceLocator) {

    private val _running = MutableStateFlow(false)
    private val _session = MutableStateFlow<CollectionConfig?>(null)
    private val _error = MutableStateFlow<String?>(null)

    val running: StateFlow<Boolean> = _running.asStateFlow()
    val session: StateFlow<CollectionConfig?> = _session.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()
    val currentReading: StateFlow<CellReading?> = deps.telephonyDataSource.current

    @Volatile
    private var job: Job? = null

    @Volatile
    private var activeSource: CellDataSource? = null

    @Volatile
    private var appVersion: String? = null

    fun setAppVersion(version: String?) {
        appVersion = version
    }

    @Synchronized
    fun start(config: CollectionConfig) {
        if (_running.value) return
        _running.value = true
        _session.value = config
        _error.value = null

        val source = deps.source(config.sourceType)
        activeSource = source

        source.start(config)
        val availability = source.availability.value
        if (!availability.available) {
            _error.value = availability.message
            stopInternal()
            return
        }

        deps.locationProvider.start(config.minLocationIntervalMs)
        deps.attitudeProvider.start()

        job = deps.collectionScope.launch {
            // Prime the cache and produce a first row with request/callback times.
            val primeReading = source.requestCellInfoUpdate()
            persist(primeReading)

            var lastRequestElapsed = SystemClock.elapsedRealtime()
            while (deps.collectionScope.isActive && _running.value) {
                delay(config.sampleIntervalMs.toLong())
                if (!_running.value) break

                val now = SystemClock.elapsedRealtime()
                val cachedReading = source.readCached(now)
                persist(cachedReading)

                val cacheElapsed = cachedReading.cellInfoElapsedRealtimeMs ?: 0L
                if (now - cacheElapsed >= 10_000L && now - lastRequestElapsed >= 10_000L) {
                    lastRequestElapsed = now
                    val fresh = source.requestCellInfoUpdate()
                    persist(fresh)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        _running.value = false
        job?.cancel()
        job = null
        activeSource?.stop()
        deps.locationProvider.stop()
        deps.attitudeProvider.stop()
        activeSource = null
        _session.value = null
    }

    fun requestManualSample() {
        val source = activeSource ?: return
        if (!_running.value) return
        deps.collectionScope.launch {
            val reading = source.requestCellInfoUpdate()
            persist(reading)
        }
    }

    private suspend fun persist(reading: CellReading) {
        val config = _session.value ?: return
        val nowUtc = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val location = deps.locationProvider.currentReading(nowElapsed)
        val attitude = deps.attitudeProvider.latest.value?.let { current ->
            val age = current.elapsedRealtimeMs?.let { nowElapsed - it }
            current.takeIf { age == null || age < 15_000L }
        }
        val entity = buildCellSample(
            config = config,
            reading = reading,
            location = location,
            attitude = attitude,
            nowUtcMs = nowUtc,
            nowElapsedRealtimeMs = nowElapsed,
            appVersion = appVersion
        )
        deps.repository.insert(entity)
    }
}
