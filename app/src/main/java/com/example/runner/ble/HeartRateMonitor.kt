package com.example.runner.ble

import android.annotation.SuppressLint
import android.content.Context
import androidx.bluetooth.BluetoothDevice
import androidx.bluetooth.BluetoothLe
import androidx.bluetooth.ScanResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/** Bluetooth Heart Rate Service UUID (0x180D). */
val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

/** Heart Rate Measurement characteristic UUID (0x2A37). */
val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

/** A BLE device the user can select, together with its display name and address. */
data class DeviceInfo(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
)

/** Maps a scan result to a [DeviceInfo], skipping devices with no name and no heart-rate service. */
fun ScanResult.toDeviceInfo(): DeviceInfo? {
    val name = device.name
    val address = deviceAddress.address
    val hasName = !name.isNullOrBlank()
    val advertisesHeartRate = serviceUuids.any { it == HEART_RATE_SERVICE_UUID }
    if (!hasName && !advertisesHeartRate) return null
    return DeviceInfo(device, name ?: address, address)
}

/**
 * Thin wrapper around the Jetpack Bluetooth LE API for talking to a heart rate monitor.
 */
class HeartRateMonitor(context: Context) {

    private val bluetoothLe = BluetoothLe(context.applicationContext)

    /** Cold flow of BLE scan results (stop scanning by cancelling the collector). */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<ScanResult> = bluetoothLe.scan()

    /**
     * Connects to [device], enables heart-rate notifications and forwards every
     * measurement to [onHeartRate].
     *
     * Returns when the connection ends. Throws [CancellationException] when the caller's
     * coroutine is cancelled (connection closed on leaving the screen) or when the remote
     * device disconnects.
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice, onHeartRate: (Int) -> Unit) {
        bluetoothLe.connectGatt(device) {
            val service = getService(HEART_RATE_SERVICE_UUID)
                ?: throw IllegalStateException("The device does not expose a Heart Rate service")
            val characteristic = service.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
                ?: throw IllegalStateException("The device does not expose Heart Rate measurements")
            subscribeToCharacteristic(characteristic).collect { value ->
                parseHeartRate(value)?.let(onHeartRate)
            }
        }
    }
}

/**
 * Parses a Heart Rate Measurement characteristic value per the Bluetooth GATT spec.
 * Returns null if the payload is malformed.
 */
fun parseHeartRate(data: ByteArray): Int? {
    if (data.isEmpty()) return null
    val flags = data[0].toInt() and 0xFF
    return if (flags and 0x01 != 0) {
        // 16-bit little-endian heart rate value
        if (data.size < 3) null else (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
    } else {
        // 8-bit heart rate value
        if (data.size < 2) null else data[1].toInt() and 0xFF
    }
}