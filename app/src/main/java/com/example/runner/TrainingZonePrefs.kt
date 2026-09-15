package com.example.runner

import android.content.SharedPreferences

/** Thin wrapper around SharedPreferences for the heartbeat training zone. */
class TrainingZonePrefs(private val prefs: SharedPreferences) {
    fun read(): Pair<Int, Int> =
        prefs.getInt(KEY_MIN, DEFAULT_MIN) to prefs.getInt(KEY_MAX, DEFAULT_MAX)

    fun write(min: Int = read().first, max: Int = read().second) {
        prefs.edit().putInt(KEY_MIN, min).putInt(KEY_MAX, max).apply()
    }

    companion object {
        const val KEY_MIN = "zone_min_bpm"
        const val KEY_MAX = "zone_max_bpm"
        const val DEFAULT_MIN = 100
        const val DEFAULT_MAX = 180
        const val PREFS_NAME = "runner_settings"
    }
}