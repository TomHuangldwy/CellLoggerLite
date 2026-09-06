package com.celllogger.lite.export

import com.celllogger.lite.data.local.CellSampleEntity
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lossless CSV export. Missing values stay empty; strings are escaped and the
 * file starts with a UTF-8 BOM so Excel on Windows renders CJK notes correctly.
 */
object CsvExporter {

    private val header = listOf(
        "utc_epoch_ms",
        "utc_iso8601",
        "elapsed_realtime_ms",
        "session_id",
        "experiment",
        "source",
        "device_manufacturer",
        "device_model",
        "android_sdk",
        "app_version",
        "subscription_id",
        "sampling_interval_ms",
        "trigger",
        "request_utc_ms",
        "callback_utc_ms",
        "request_elapsed_realtime_ms",
        "callback_elapsed_realtime_ms",
        "data_freshness_ms",
        "cell_info_elapsed_realtime_ms",
        "data_network_type",
        "nr_state",
        "service_state",
        "registration_state",
        "access_technology",
        "connection_state",
        "is_registered",
        "mcc",
        "mnc",
        "arfcn",
        "bandwidth_khz",
        "pci",
        "tac",
        "cell_id",
        "rsrp_dbm",
        "rsrq_db",
        "sinr_db",
        "rssi_dbm",
        "cqi",
        "visible_cell_count",
        "source_error",
        "latitude",
        "longitude",
        "altitude_meters",
        "accuracy_meters",
        "speed_mps",
        "bearing_degrees",
        "location_provider",
        "location_elapsed_realtime_ms",
        "location_utc_ms",
        "yaw_degrees",
        "pitch_degrees",
        "roll_degrees",
        "attitude_elapsed_realtime_ms",
        "attitude_sensor",
        "note"
    )

    suspend fun write(samples: List<CellSampleEntity>, output: OutputStream) {
        withContext(Dispatchers.IO) {
            BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { writer ->
                writer.write("\uFEFF")
                writer.write(encodeRow(header))
                samples.forEach { sample ->
                    writer.write(encodeRow(rowOf(sample)))
                }
            }
        }
    }

    private fun rowOf(s: CellSampleEntity): List<Any?> = listOf(
        s.utcTimestampMs,
        Instant.ofEpochMilli(s.utcTimestampMs).toString(),
        s.elapsedRealtimeMs,
        s.sessionId,
        s.experimentId,
        s.sourceId,
        s.deviceManufacturer,
        s.deviceModel,
        s.androidSdkInt,
        s.appVersion,
        s.subscriptionId,
        s.samplingIntervalMs,
        s.trigger,
        s.requestUtcMs,
        s.callbackUtcMs,
        s.requestElapsedRealtimeMs,
        s.callbackElapsedRealtimeMs,
        s.dataFreshnessMs,
        s.cellInfoElapsedRealtimeMs,
        s.dataNetworkType,
        s.nrState,
        s.serviceStateCode,
        s.registrationState,
        s.accessTechnology,
        s.connectionState,
        s.isRegistered,
        s.mcc,
        s.mnc,
        s.arfcn,
        s.bandwidthKhz,
        s.pci,
        s.tac,
        s.cellId,
        s.rsrpDbm,
        s.rsrqDb,
        s.sinrDb,
        s.rssiDbm,
        s.cqi,
        s.visibleCellCount,
        s.sourceError,
        s.latitude,
        s.longitude,
        s.altitudeMeters,
        s.accuracyMeters,
        s.speedMetersPerSecond,
        s.bearingDegrees,
        s.locationProvider,
        s.locationElapsedRealtimeMs,
        s.locationUtcMs,
        s.yawDegrees,
        s.pitchDegrees,
        s.rollDegrees,
        s.attitudeElapsedRealtimeMs,
        s.attitudeSensor,
        s.note
    )

    private fun encodeRow(values: List<Any?>): String =
        values.joinToString(separator = ",", postfix = "\r\n") { value ->
            when (value) {
                null -> ""
                is Float -> String.format(Locale.US, "%.4f", value)
                is Double -> String.format(Locale.US, "%.6f", value)
                is Number, is Boolean -> value.toString()
                else -> escape(value.toString())
            }
        }

    private fun escape(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""
}
