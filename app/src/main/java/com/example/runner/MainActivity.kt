package com.example.runner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
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
import com.example.runner.recording.RecordingService
import com.example.runner.recording.SessionRecorder
import com.example.runner.recording.TrackPoint
import com.example.runner.ui.AppScreen
import com.example.runner.ui.ConfigScreen
import com.example.runner.ui.ConnectionState
import com.example.runner.ui.RecordingState
import com.example.runner.ui.SessionScreen
import com.example.runner.ui.theme.RunnerTheme
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A GPS fix older than this is dropped from the track instead of duplicated. */
private const val STALE_FIX_MS = 5_000L

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
    val recorder = remember { SessionRecorder() }
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
    var recordingState by remember { mutableStateOf(RecordingState.WAITING_TO_START) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var currentScreen by remember { mutableStateOf(AppScreen.CONFIG) }

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

    // Sample heart rate + GPS every second while RECORDING; backup to cache every 30 s.
    LaunchedEffect(recordingState) {
        if (recordingState == RecordingState.RECORDING) {
            var elapsed = 0L
            while (isActive) {
                delay(1000)
                elapsed++
                elapsedMs = recorder.elapsedMillis()
                val fresh = gpsState.fixElapsedMs > 0 &&
                    SystemClock.elapsedRealtime() - gpsState.fixElapsedMs < STALE_FIX_MS
                val lat = gpsState.latitude
                val lon = gpsState.longitude
                if (fresh && lat != null && lon != null) {
                    recorder.addPoint(
                        TrackPoint(
                            latitude = lat,
                            longitude = lon,
                            elevation = gpsState.elevation ?: 0.0,
                            timestamp = Instant.now(),
                            heartRate = heartRate ?: 0,
                        )
                    )
                }
                if (elapsed % 30 == 0L) {
                    recorder.flushToBackup(context.cacheDir)
                }
            }
        }
    }

    // Run as a foreground service (location type) while RECORDING so Android keeps
    // delivering GPS fixes when the screen is off or the app is backgrounded.
    DisposableEffect(recordingState) {
        val active = recordingState == RecordingState.RECORDING
        if (active) {
            ContextCompat.startForegroundService(
                context, Intent(context, RecordingService::class.java)
            )
        }
        onDispose {
            if (active) {
                context.stopService(Intent(context, RecordingService::class.java))
            }
        }
    }

    val device = selectedDevice

    // Connect to the selected monitor. Stays active across both screens so the
    // session screen keeps receiving heart rate data.
    LaunchedEffect(device) {
        if (device == null) return@LaunchedEffect
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

    when (currentScreen) {
        AppScreen.CONFIG -> ConfigScreen(
            devices = devices,
            isScanning = isScanning,
            scanError = scanError,
            permissionsGranted = permissionsGranted,
            onRequestPermissions = { permissionLauncher.launch(requiredPermissions) },
            onScan = ::scanDevices,
            onDeviceSelected = { selectedDevice = it },
            device = device,
            connectionState = connectionState,
            heartRate = heartRate,
            gpsState = gpsState,
            onDisconnect = {
                selectedDevice = null
                recordingState = RecordingState.WAITING_TO_START
                recorder.deleteBackup(context.cacheDir)
            },
            onNewSession = { currentScreen = AppScreen.SESSION },
        )

        AppScreen.SESSION -> SessionScreen(
            heartRate = heartRate,
            recordingState = recordingState,
            elapsedMs = elapsedMs,
            onStartRecording = {
                recorder.startNewSession()
                recorder.flushToBackup(context.cacheDir)
                elapsedMs = 0
                recordingState = RecordingState.RECORDING
            },
            onPauseRecording = {
                recorder.onPause()
                elapsedMs = recorder.elapsedMillis()
                recordingState = RecordingState.PAUSE
            },
            onResumeRecording = {
                recorder.onResume()
                recordingState = RecordingState.RECORDING
            },
            onStopRecording = {
                recorder.flushToBackup(context.cacheDir)
                recorder.saveToDownloads(context)
                recorder.deleteBackup(context.cacheDir)
                recordingState = RecordingState.WAITING_TO_START
                currentScreen = AppScreen.CONFIG
            },
        )
    }
}

private fun Context.hasPermissions(permissions: Array<String>): Boolean =
    permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
