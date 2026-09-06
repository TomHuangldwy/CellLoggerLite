package com.celllogger.lite.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One persisted row = one sampling event (the best serving cell plus the
 * location/attitude observed at the same wall-clock moment).
 *
 * Rule enforced by the mapper, not by SQL: an API that did not produce a value
 * is stored as SQL NULL. No zero / -160 / Integer.MAX_VALUE placeholders are
 * ever persisted.
 */
@Entity(
    tableName = "cell_samples",
    indices = [
        Index("sessionId"),
        Index("utcTimestampMs"),
        Index("experimentId")
    ]
)
data class CellSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val experimentId: String?,
    val sourceId: String?,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val androidSdkInt: Int?,
    val appVersion: String?,
    val subscriptionId: Int?,

    /** Always present: this is the moment the row was assembled. */
    val utcTimestampMs: Long,
    val elapsedRealtimeMs: Long,
    val samplingIntervalMs: Int?,
    val trigger: String?,

    /** Request/callback audit. Null when no explicit request was made. */
    val requestUtcMs: Long?,
    val callbackUtcMs: Long?,
    val requestElapsedRealtimeMs: Long?,
    val callbackElapsedRealtimeMs: Long?,
    val dataFreshnessMs: Long?,
    val cellInfoElapsedRealtimeMs: Long?,

    val dataNetworkType: String?,
    val nrState: String?,
    val serviceStateCode: String?,
    val registrationState: String?,

    val accessTechnology: String?,
    val connectionState: String?,
    val isRegistered: Boolean?,
    val mcc: String?,
    val mnc: String?,
    val arfcn: Int?,
    val bandwidthKhz: Int?,
    val pci: Int?,
    val tac: Int?,
    val cellId: Long?,
    val rsrpDbm: Int?,
    val rsrqDb: Int?,
    val sinrDb: Int?,
    val rssiDbm: Int?,
    val cqi: Int?,
    val visibleCellCount: Int?,
    val sourceError: String?,

    val latitude: Double?,
    val longitude: Double?,
    val altitudeMeters: Double?,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val locationProvider: String?,
    val locationElapsedRealtimeMs: Long?,
    val locationUtcMs: Long?,

    val yawDegrees: Double?,
    val pitchDegrees: Double?,
    val rollDegrees: Double?,
    val attitudeElapsedRealtimeMs: Long?,
    val attitudeSensor: String?,

    val note: String?
)
