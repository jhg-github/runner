package com.example.runner.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.runner.R
import kotlinx.coroutines.delay

private const val BEEPS_PER_CUE = 3
private const val BEEP_GAP_MS = 400L

class TrainerSoundPlayer(context: Context) {

    private var loaded = false
    private var beepId = 0
    private var runId = 0
    private var walkId = 0

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
        .apply {
            setOnLoadCompleteListener { _, _, _ -> loaded = true }
            beepId = load(context, R.raw.beep, 1)
            runId = load(context, R.raw.run, 1)
            walkId = load(context, R.raw.walk, 1)
        }

    suspend fun playCue(isRun: Boolean) {
        while (!loaded) delay(50)
        for (i in 0 until BEEPS_PER_CUE) {
            soundPool.play(beepId, 1f, 1f, 1, 0, 1f)
            delay(BEEP_GAP_MS)
        }
        val clipId = if (isRun) runId else walkId
        soundPool.play(clipId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
