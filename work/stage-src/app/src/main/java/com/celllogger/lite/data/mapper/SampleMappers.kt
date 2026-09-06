package com.celllogger.lite.data.mapper

import android.os.Build
import com.celllogger.lite.data.local.CellSampleEntity
import com.celllogger.lite.data.model.AttitudeReading
import com.celllogger.lite.data.model.CellReading
import com.celllogger.lite.data.model.CollectionConfig
import com.celllogger.lite.data.model.LocationReading
import kotlin.math.max

/**
 * Merges a radio reading with the location/attitude observed at [nowUtcMs] and
 * maps it to a persistable row.
 *
 * dataFreshnessMs is one of:
 *  1. manual request latency: callback - request
 *  2. age of the cached cell list against the sample time when no explicit
 *     request was made
 */
fun buildCellSample(
    config: CollectionConfig,
    reading: CellReading,
    location: LocationReading?,
    attitude: AttitudeReading?,
    nowUtcMs: Long,
    nowElapsedRealtimeMs: Long,
    appVersion: String? = null
): CellSampleEntity {
    val selected = reading.selected
    val freshness = when {
        reading.requestElapsedRealtimeMs != null &&
            reading.callbackElapsedRealtimeMs != null -> {
            max(0L, reading.callbackElapsedRealtimeMs - reading.requestElapsedRealtimeMs)
        }
        reading.cellInfoElapsedRealtimeMs != null -> {
            max(0L, nowElapsedRealtimeMs - reading.cellInfoElapsedRealtimeMs)
        }
        else -> null
    }

    return CellSampleEntity(
        sessionId = config.sessionId,
        experimentId = config.experimentId.name,
        sourceId = reading.sourceId,
        deviceManufacturer = Build.MANUFACTURER,
        deviceModel = Build.MODEL,
        androidSdkInt = Build.VERSION.SDK_INT,
        appVersion = appVersion,
        subscriptionId = reading.subscriptionId ?: config.subscriptionId,
        utcTimestampMs = nowUtcMs,
        elapsedRealtimeMs = nowElapsedRealtimeMs,
        samplingIntervalMs = config.sampleIntervalMs,
        trigger = reading.trigger.label,
        requestUtcMs = reading.requestUtcMs,
        callbackUtcMs = reading.callbackUtcMs,
        requestElapsedRealtimeMs = reading.requestElapsedRealtimeMs,
        callbackElapsedRealtimeMs = reading.callbackElapsedRealtimeMs,
        dataFreshnessMs = freshness,
        cellInfoElapsedRealtimeMs = reading.cellInfoElapsedRealtimeMs,
        dataNetworkType = reading.dataNetworkType,
        nrState = reading.nrState,
        serviceStateCode = reading.serviceStateCode,
        registrationState = reading.registrationState,
        accessTechnology = selected?.accessTechnology?.label,
        connectionState = selected?.connectionState?.label,
        isRegistered = selected?.isRegistered,
        mcc = selected?.mcc,
        mnc = selected?.mnc,
        arfcn = selected?.arfcn,
        bandwidthKhz = selected?.bandwidthKhz,
        pci = selected?.pci,
        tac = selected?.tac,
        cellId = selected?.cellId,
        rsrpDbm = selected?.rsrpDbm,
        rsrqDb = selected?.rsrqDb,
        sinrDb = selected?.sinrDb,
        rssiDbm = selected?.rssiDbm,
        cqi = selected?.cqi,
        visibleCellCount = reading.visibleCellCount.takeIf { it > 0 },
        sourceError = reading.sourceError,
        latitude = location?.latitude,
        longitude = location?.longitude,
        altitudeMeters = location?.altitudeMeters,
        accuracyMeters = location?.accuracyMeters,
        speedMetersPerSecond = location?.speedMetersPerSecond,
        bearingDegrees = location?.bearingDegrees,
        locationProvider = location?.provider,
        locationElapsedRealtimeMs = location?.elapsedRealtimeMs,
        locationUtcMs = location?.utcMs,
        yawDegrees = attitude?.yawDegrees,
        pitchDegrees = attitude?.pitchDegrees,
        rollDegrees = attitude?.rollDegrees,
        attitudeElapsedRealtimeMs = attitude?.elapsedRealtimeMs,
        attitudeSensor = attitude?.sensorName,
        note = config.note.ifBlank { null }
    )
}
