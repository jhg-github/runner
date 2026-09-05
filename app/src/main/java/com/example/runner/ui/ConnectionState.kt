package com.example.runner.ui

/**
 * State of the BLE connection to the selected heart rate monitor.
 */
sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data object Disconnected : ConnectionState
    data class Failed(val message: String) : ConnectionState
}