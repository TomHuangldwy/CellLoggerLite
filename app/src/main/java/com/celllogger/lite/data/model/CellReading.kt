package com.celllogger.lite.data.model

/**
 * Radio access technology of one measured cell.
 *
 * Only NR and LTE are fully decoded by TelephonyDataSource. Other technologies
 * are kept for completeness but never zero-filled.
 */
enum class AccessTechnology(val label: String) {
    NR("NR"),
    LTE("LTE"),
    GSM("GSM"),
    UMTS("UMTS"),
    CDMA("CDMA"),
    UNKNOWN("UNKNOWN")
}

/**
 * CellInfo.getCellConnectionStatus() exposed by Android 10+.
 */
enum class ConnectionState(val label: String) {
    PRIMARY_SERVING("PRIMARY_SERVING"),
    SECONDARY_SERVING("SECONDARY_SERVING"),
    UNKNOWN("UNKNOWN")
}

/**
 * What produced a [CellReading]. This is persisted so every CSV row can be
 * audited: system push, an explicit requestCellInfoUpdate(), or a periodic
 * read of the last cached value.
 */
enum class SamplingTrigger(val label: String) {
    SYSTEM_UPDATE("system_update"),
    MANUAL_REQUEST("manual_request"),
    PERIODIC_CACHE("periodic_cache");

    companion object {
        fun fromLabel(label: String?): SamplingTrigger =
            entries.firstOrNull { it.label == label } ?: PERIODIC_CACHE
    }
}

/**
 * Decoded public-API cell identity + signal strength.
 *
 * Every value is null when the platform did not supply it. Unknown sentinels
 * such as Integer.MAX_VALUE are deliberately converted to null.
 */
data class CellMeasurement(
    val accessTechnology: AccessTechnology,
    val connectionState: ConnectionState,
    val isRegistered: Boolean?,
    val mcc: String?,
    val mnc: String?,
    val tac: Int?,
    val cellId: Long?,
    val pci: Int?,
    val arfcn: Int?,
    val bandwidthKhz: Int?,
    val rsrpDbm: Int?,
    val rsrqDb: Int?,
    val sinrDb: Int?,
    val rssiDbm: Int?,
    val cqi: Int?,
    val signalLevel: Int?
)

/**
 * One radio snapshot. It contains all visible decoded cells; [selected] is the
 * best serving cell used for the "current cell card" and for the single-cell
 * columns in the CSV.
 */
data class CellReading(
    val sourceId: String,
    val subscriptionId: Int?,
    val dataNetworkType: String?,
    val nrState: String?,
    val serviceStateCode: String?,
    val registrationState: String?,
    val cells: List<CellMeasurement>,
    val trigger: SamplingTrigger,
    val requestUtcMs: Long? = null,
    val callbackUtcMs: Long? = null,
    val requestElapsedRealtimeMs: Long? = null,
    val callbackElapsedRealtimeMs: Long? = null,
    val cellInfoElapsedRealtimeMs: Long? = null,
    val sourceError: String? = null
) {
    val visibleCellCount: Int get() = cells.size

    val selected: CellMeasurement?
        get() = cells
            .filter { it.isRegistered == true || it.connectionState != ConnectionState.UNKNOWN }
            .maxWithOrNull(
                compareBy<CellMeasurement> { if (it.isRegistered == true) 0 else 1 }
                    .thenBy { connectionStateRank(it.connectionState) }
            )
            ?: cells.firstOrNull()

    companion object {
        fun empty(
            sourceId: String,
            trigger: SamplingTrigger = SamplingTrigger.PERIODIC_CACHE,
            requestUtcMs: Long? = null,
            callbackUtcMs: Long? = null,
            requestElapsedRealtimeMs: Long? = null,
            callbackElapsedRealtimeMs: Long? = null,
            error: String? = null
        ): CellReading = CellReading(
            sourceId = sourceId,
            subscriptionId = null,
            dataNetworkType = null,
            nrState = null,
            serviceStateCode = null,
            registrationState = null,
            cells = emptyList(),
            trigger = trigger,
            requestUtcMs = requestUtcMs,
            callbackUtcMs = callbackUtcMs,
            requestElapsedRealtimeMs = requestElapsedRealtimeMs,
            callbackElapsedRealtimeMs = callbackElapsedRealtimeMs,
            sourceError = error
        )

        private fun connectionStateRank(state: ConnectionState): Int = when (state) {
            ConnectionState.PRIMARY_SERVING -> 0
            ConnectionState.SECONDARY_SERVING -> 1
            ConnectionState.UNKNOWN -> 2
        }
    }
}
