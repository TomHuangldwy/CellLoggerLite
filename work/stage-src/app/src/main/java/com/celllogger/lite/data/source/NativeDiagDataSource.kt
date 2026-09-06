package com.celllogger.lite.data.source

import com.celllogger.lite.data.model.CellReading
import com.celllogger.lite.data.model.CollectionConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Placeholder for a future vendor/rooted DIAG or ML1 data source.
 *
 * Purposefully not functional: Cell Logger Lite ships with no root and no
 * vendor-specific code. Keeping the class proves the DataSource seam so a
 * per-SSB / PHY-layer source can later be plugged in without touching the
 * collection manager, Room schema or CSV exporter.
 */
class NativeDiagDataSource : CellDataSource {
    private val _availability = MutableStateFlow(
        DataSourceAvailability(
            available = false,
            message = "Native DIAG/ML1 is intentionally disabled. " +
                "Requires vendor privileges; this build uses public APIs only.",
            type = DataSourceType.NATIVE_DIAG
        )
    )
    private val _current = MutableStateFlow<CellReading?>(null)

    override val sourceType: DataSourceType = DataSourceType.NATIVE_DIAG
    override val displayName: String = "Native DIAG (future / not enabled)"
    override val availability: StateFlow<DataSourceAvailability> = _availability.asStateFlow()
    override val current: StateFlow<CellReading?> = _current.asStateFlow()

    override fun start(config: CollectionConfig) {
        _availability.value = DataSourceAvailability(false, "Not implemented.", sourceType)
    }

    override fun stop() {
        _current.value = null
    }

    override suspend fun requestCellInfoUpdate(): CellReading =
        CellReading.empty(sourceType.id, error = "DataSource disabled")

    override suspend fun readCached(nowElapsedRealtimeMs: Long): CellReading =
        CellReading.empty(sourceType.id)
}
