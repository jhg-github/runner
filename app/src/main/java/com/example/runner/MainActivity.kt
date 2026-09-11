package com.example.runner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.runner.ble.DeviceInfo
import com.example.runner.ble.HeartRateMonitor
import com.example.runner.ble.toDeviceInfo
import com.example.runner.gps.GpsState
import com.example.runner.gps.GpsTracker
import com.example.runner.ui.ConnectionState
import com.example.runner.ui.DeviceScanScreen
import com.example.runner.ui.HeartRateScreen
import com.example.runner.ui.theme.RunnerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RunnerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RunnerApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun RunnerApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val monitor = remember { HeartRateMonitor(context) }
    val gpsTracker = remember { GpsTracker(context) }
    var gpsState by remember { mutableStateOf(GpsState()) }

    // Runtime BLE permissions.
    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
    var permissionsGranted by remember { mutableStateOf(context.hasPermissions(requiredPermissions)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.values.all { it }
    }

    // Scan state.
    var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val scanScope = rememberCoroutineScope()

    // Connection state.
    var selectedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var heartRate by remember { mutableStateOf<Int?>(null) }
    var connectionState by remember { mutableStateOf<ConnectionState>(ConnectionState.Connecting) }

    fun scanDevices() {
        scanScope.launch {
            isScanning = true
            scanError = null
            devices = emptyList()
            try {
                monitor.scan().collect { result ->
                    val info = result.toDeviceInfo() ?: return@collect
                    if (devices.none { it.address == info.address }) {
                        devices = devices + info
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                scanError = e.message ?: "Scan failed"
            } finally {
                isScanning = false
            }
        }
    }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted && selectedDevice == null) {
            scanDevices()
        }
    }

    // Start GPS updates when permissions change; stop when the activity leaves composition.
    LaunchedEffect(permissionsGranted) {
        gpsTracker.onUpdate = { gpsState = it }
        if (permissionsGranted) gpsTracker.start()
    }
    DisposableEffect(Unit) {
        onDispose { gpsTracker.stop() }
    }

    val device = selectedDevice
    if (device == null) {
        DeviceScanScreen(
            devices = devices,
            isScanning = isScanning,
            error = scanError,
            permissionsGranted = permissionsGranted,
            onRequestPermissions = { permissionLauncher.launch(requiredPermissions) },
            onScan = ::scanDevices,
            onDeviceSelected = { selectedDevice = it },
        )
    } else {
        LaunchedEffect(device) {
            heartRate = null
            connectionState = ConnectionState.Connecting
            try {
                monitor.connect(device.device) { rate ->
                    heartRate = rate
                    connectionState = ConnectionState.Connected
                }
                // Connection closed without an exception (e.g. remote device stopped).
                connectionState = ConnectionState.Disconnected
            } catch (e: CancellationException) {
                // Canceled by leaving this screen or by the remote device disconnecting.
                connectionState = ConnectionState.Disconnected
                throw e
            } catch (e: Exception) {
                connectionState = ConnectionState.Failed(e.message ?: "Connection failed")
            }
        }
        HeartRateScreen(
            device = device,
            connectionState = connectionState,
            heartRate = heartRate,
            gpsState = gpsState,
            onDisconnect = { selectedDevice = null },
        )
    }
}

private fun Context.hasPermissions(permissions: Array<String>): Boolean =
    permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
