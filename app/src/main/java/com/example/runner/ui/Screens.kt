package com.example.runner.ui

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
 * Shows the live heart rate of the connected monitor, or the connection status.
 */
@Composable
fun HeartRateScreen(
    device: DeviceInfo,
    connectionState: ConnectionState,
    heartRate: Int?,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = device.name,
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

        Spacer(Modifier.height(48.dp))

        Button(onClick = onDisconnect) {
            Text("Disconnect")
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