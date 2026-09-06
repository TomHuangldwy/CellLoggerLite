package com.celllogger.lite.data.source

import com.celllogger.lite.data.model.CellReading
import com.celllogger.lite.data.model.CollectionConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Pluggable source of radio measurements.
 *
 * Current implementation: [TelephonyDataSource] (public Android Telephony API).
 * [NativeDiagDataSource] is intentionally reserved for a future non-public
 * DIAG/ML1 implementation; it is not wired into the app today.
 */
interface CellDataSource {
    val sourceType: DataSourceType
    val displayName: String

    /**
     * Runtime availability (permission, radio/subscription present, ...).
     * Reading is true only when start() succeeded.
     */
    val availability: StateFlow<DataSourceAvailability>

    /** Latest known reading, updated by system callbacks and manual requests. */
    val current: StateFlow<CellReading?>

    fun start(config: CollectionConfig)
    fun stop()

    /**
     * Actively asks the modem for a fresh cell list via
     * requestCellInfoUpdate(Executor, CellInfoCallback), available since API 29.
     */
    suspend fun requestCellInfoUpdate(): CellReading

    /**
     * Build a reading from the cached values pushed by the modem. Never issues
     * an extra radio request by itself.
     */
    suspend fun readCached(nowElapsedRealtimeMs: Long): CellReading
}

enum class DataSourceType(val id: String, val label: String) {
    TELEPHONY("telephony", "Android Telephony API"),
    NATIVE_DIAG("native_diag", "Native DIAG (future)")
}

data class DataSourceAvailability(
    val available: Boolean,
    val message: String,
    val type: DataSourceType
)
