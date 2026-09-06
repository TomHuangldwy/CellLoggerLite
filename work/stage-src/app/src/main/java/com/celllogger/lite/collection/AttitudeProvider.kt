package com.celllogger.lite.collection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.celllogger.lite.data.model.AttitudeReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI

/**
 * Orientation from TYPE_GAME_ROTATION_VECTOR (no magnetometer, no compass
 * calibration requirement). Rotation-matrix orientation gives:
 *  yaw   = rotation around -Z
 *  pitch = rotation around X
 *  roll  = rotation around Y
 * converted from radians to degrees.
 */
class AttitudeProvider(context: Context) {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val _latest = MutableStateFlow<AttitudeReading?>(null)

    val latest: StateFlow<AttitudeReading?> = _latest.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var sensorName: String? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val timestampMs = if (event.timestamp > 0L) event.timestamp / 1_000_000L else null
            _latest.value = AttitudeReading(
                yawDegrees = Math.toDegrees(orientation[0].toDouble()),
                pitchDegrees = Math.toDegrees(orientation[1].toDouble()),
                rollDegrees = Math.toDegrees(orientation[2].toDouble()),
                elapsedRealtimeMs = timestampMs,
                sensorName = sensorName
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    @Volatile
    private var started = false

    fun start() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: return
        sensorName = sensor.name
        sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_GAME
        )
        started = true
    }

    fun stop() {
        if (started) {
            sensorManager.unregisterListener(listener)
            started = false
        }
        _latest.value = null
    }
}
