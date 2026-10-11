package com.example.runner.ui

import java.time.Duration
import java.util.Locale

enum class RecordingState {
    WAITING_TO_START,
    RECORDING,
    PAUSE,
}

/** What the virtual trainer tells the user to do right now. */
enum class TrainerState { RUN, WALK }

/** Formats [ms] as HH:MM:SS. */
internal fun formatElapsed(ms: Long): String {
    val d = Duration.ofMillis(ms)
    return "%02d:%02d:%02d".format(Locale.US, d.toHours(), d.toMinutesPart(), d.toSecondsPart())
}
