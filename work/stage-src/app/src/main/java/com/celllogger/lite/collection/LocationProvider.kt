package com.celllogger.lite.collection

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.celllogger.lite.data.model.LocationReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Minimal LocationManager-based provider. No Google Play Services dependency,
 * so the same APK works on devices without GMS.
 */
@SuppressLint("MissingPermission")
class LocationProvider(private val context: Context) {

    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _latest = MutableStateFlow<LocationReading?>(null)

    val latest: StateFlow<LocationReading?> = _latest.asStateFlow()

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _latest.value = location.toReading()
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    @Volatile
    private var activeProvider: String? = null

    fun start(minTimeMs: Long) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) return
        activeProvider = provider
        locationManager.requestLocationUpdates(
            provider,
            minTimeMs.coerceAtLeast(500L),
            0f,
            listener,
            Looper.getMainLooper()
        )
        locationManager.getLastKnownLocation(provider)?.let { _latest.value = it.toReading() }
    }

    fun stop() {
        locationManager.removeUpdates(listener)
        activeProvider = null
        _latest.value = null
    }

    fun currentReading(nowElapsedRealtimeMs: Long): LocationReading? {
        val current = _latest.value ?: return null
        // Only report a location newer than a few seconds; otherwise the row is
        // marked with a null location rather than an outdated fix.
        val age = current.elapsedRealtimeMs?.let { nowElapsedRealtimeMs - it }
        return if (age == null || age < 15_000L) current else null
    }

    private fun Location.toReading(): LocationReading {
        val elapsedNs = if (elapsedRealtimeNanos > 0L) elapsedRealtimeNanos else null
        return LocationReading(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else null,
            speedMetersPerSecond = if (hasSpeed()) speed else null,
            bearingDegrees = if (hasBearing()) bearing else null,
            altitudeMeters = if (hasAltitude()) altitude else null,
            provider = provider,
            elapsedRealtimeMs = elapsedNs?.div(1_000_000L),
            utcMs = time.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }
}
