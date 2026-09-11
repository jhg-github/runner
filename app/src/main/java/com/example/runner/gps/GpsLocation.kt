package com.example.runner.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat

/** GPS signal strength shown to the user. */
enum class SignalStrength { NONE, WEAK, GOOD }

/** Latest GPS fix. All fields null until the first fix arrives. */
data class GpsState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val signal: SignalStrength = SignalStrength.NONE,
)

/**
 * Thin wrapper around [LocationManager] that pushes GPS updates roughly once per second
 * (minTime 1000 ms, minDistance 0 m). Mirrors the callback style of [com.example.runner.ble.HeartRateMonitor].
 */
class GpsTracker(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = update(location)
    }

    var onUpdate: ((GpsState) -> Unit)? = null

    fun start() {
        val granted = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { update(it) }
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper()
        )
    }

    fun stop() {
        locationManager.removeUpdates(listener)
    }

    private fun update(location: Location) {
        val signal = when {
            location.accuracy <= 10f -> SignalStrength.GOOD
            location.accuracy <= 50f -> SignalStrength.WEAK
            else -> SignalStrength.NONE
        }
        onUpdate?.invoke(
            GpsState(location.latitude, location.longitude, location.accuracy, signal)
        )
    }
}