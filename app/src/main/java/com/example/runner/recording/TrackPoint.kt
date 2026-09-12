package com.example.runner.recording

import java.time.Instant

/** One recorded sample of the session. */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double, // meters, 0.0 if unavailable
    val timestamp: Instant,
    val heartRate: Int, // BPM, 0 if unavailable
)