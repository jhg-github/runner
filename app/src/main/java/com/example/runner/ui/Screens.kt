package com.example.runner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.runner.ble.DeviceInfo
import com.example.runner.gps.GpsState
import com.example.runner.gps.SignalStrength
import java.time.Duration
import java.util.Locale

/** Top-level navigation between the two app screens. */
enum class AppScreen { CONFIG, SESSION }

/**
 * Lets the user scan for nearby BLE devices and pick a heart rate monitor.
 */
@Composable
fun DeviceScanScreen(
    devices: List<DeviceInfo>,
    isScanning: Boolean,
    error: String?,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onScan: () -> Unit,
    onDeviceSelected: (DeviceInfo) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Heart rate monitor",
            style = MaterialTheme.typography.headlineMedium,
        )

        if (!permissionsGranted) {
            Text("Bluetooth permission is required to find and connect to your heart rate monitor.")
            Button(onClick = onRequestPermissions) {
                Text("Grant permission")
            }
            return@Column
        }

        Button(onClick = onScan, enabled = !isScanning) {
            if (isScanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("Scanning…")
                }
            } else {
                Text("Scan for devices")
            }
        }

        if (error != null) {
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }

        if (isScanning && devices.isEmpty()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices, key = { it.address }) { device ->
                DeviceCard(device = device, onClick = { onDeviceSelected(device) })
            }
        }

        if (devices.isEmpty() && !isScanning && error == null) {
            Text(
                text = "No devices found. Make sure your heart rate monitor is on, nearby and not already connected.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Text(text = device.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * First screen: heart rate monitor connection, GPS status, and a button to start a session.
 * Shows the device scan when no monitor is connected yet.
 */
@Composable
fun ConfigScreen(
    devices: List<DeviceInfo>,
    isScanning: Boolean,
    scanError: String?,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onScan: () -> Unit,
    onDeviceSelected: (DeviceInfo) -> Unit,
    device: DeviceInfo?,
    connectionState: ConnectionState,
    heartRate: Int?,
    gpsState: GpsState,
    onDisconnect: () -> Unit,
    onNewSession: () -> Unit,
) {
    val selectedDevice = device
    if (selectedDevice == null) {
        DeviceScanScreen(
            devices = devices,
            isScanning = isScanning,
            error = scanError,
            permissionsGranted = permissionsGranted,
            onRequestPermissions = onRequestPermissions,
            onScan = onScan,
            onDeviceSelected = onDeviceSelected,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = selectedDevice.name,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        when (connectionState) {
            ConnectionState.Connecting -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Connecting…")
            }

            ConnectionState.Connected -> HeartRateDisplay(heartRate)

            ConnectionState.Disconnected -> Text(
                text = "Disconnected",
                style = MaterialTheme.typography.titleMedium,
            )

            is ConnectionState.Failed -> Text(
                text = connectionState.message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(onClick = onDisconnect) {
            Text("Disconnect")
        }

        Spacer(Modifier.height(24.dp))

        GpsPanel(gpsState = gpsState)

        Spacer(Modifier.height(24.dp))

        Button(onClick = onNewSession, enabled = connectionState == ConnectionState.Connected) {
            Text("New Session")
        }
    }
}

/**
 * Second screen: live heart rate value, total elapsed session time and recording controls.
 */
@Composable
fun SessionScreen(
    heartRate: Int?,
    recordingState: RecordingState,
    elapsedMs: Long,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = heartRate?.toString() ?: "--",
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = formatElapsed(elapsedMs),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "elapsed",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        RecordingControls(
            recordingState = recordingState,
            onStart = onStartRecording,
            onPause = onPauseRecording,
            onResume = onResumeRecording,
            onStop = onStopRecording,
        )
    }
}

/** Formats [ms] as HH:MM:SS. */
private fun formatElapsed(ms: Long): String {
    val d = Duration.ofMillis(ms)
    return "%02d:%02d:%02d".format(Locale.US, d.toHours(), d.toMinutesPart(), d.toSecondsPart())
}

/**
 * Shows the current GPS signal strength and coordinates.
 */
@Composable
fun GpsPanel(gpsState: GpsState, modifier: Modifier = Modifier) {
    val (signalColor, signalText) = when (gpsState.signal) {
        SignalStrength.GOOD -> MaterialTheme.colorScheme.primary to "Good signal"
        SignalStrength.WEAK -> MaterialTheme.colorScheme.tertiary to "Weak signal"
        SignalStrength.NONE -> MaterialTheme.colorScheme.error to "No signal"
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "GPS", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(signalColor, CircleShape),
                )
                Spacer(Modifier.size(8.dp))
                Text(text = signalText, style = MaterialTheme.typography.bodyLarge)
            }
            val lat = gpsState.latitude?.let { "%.6f".format(it) } ?: "—"
            val lon = gpsState.longitude?.let { "%.6f".format(it) } ?: "—"
            Text(text = "Lat: $lat   Lon: $lon", style = MaterialTheme.typography.bodyMedium)
            gpsState.accuracy?.let {
                Text(
                    text = "Accuracy: ${"%.1f".format(it)} m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeartRateDisplay(heartRate: Int?) {
    val value = heartRate?.toString() ?: "--"
    Text(
        text = value,
        style = MaterialTheme.typography.displayLarge,
        textAlign = TextAlign.Center,
    )
    Text(
        text = "beats per minute",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Shows the session recording controls, driven by the recording state machine.
 */
@Composable
private fun RecordingControls(
    recordingState: RecordingState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    when (recordingState) {
        RecordingState.WAITING_TO_START -> {
            Button(onClick = onStart) {
                Text("START")
            }
        }

        RecordingState.RECORDING -> {
            Button(onClick = onPause) {
                Text("PAUSE")
            }
        }

        RecordingState.PAUSE -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onResume) {
                    Text("RESUME")
                }
                Button(onClick = onStop) {
                    Text("STOP")
                }
            }
        }
    }
}