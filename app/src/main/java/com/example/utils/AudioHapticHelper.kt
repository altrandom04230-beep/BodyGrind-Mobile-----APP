package com.example.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class AudioHapticHelper(private val context: Context) {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (_: Exception) {
            toneGenerator = null
        }
    }

    fun playCountdownBeep(soundEnabled: Boolean = true, vibrationEnabled: Boolean = true) {
        if (soundEnabled) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            } catch (_: Exception) {}
        }
        if (vibrationEnabled) {
            vibrate(80)
        }
    }

    fun playGoChime(soundEnabled: Boolean = true, vibrationEnabled: Boolean = true) {
        if (soundEnabled) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 300)
            } catch (_: Exception) {}
        }
        if (vibrationEnabled) {
            vibrate(200)
        }
    }

    fun playRestFinishedChime(soundEnabled: Boolean = true, vibrationEnabled: Boolean = true) {
        if (soundEnabled) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350)
            } catch (_: Exception) {}
        }
        if (vibrationEnabled) {
            vibrate(300)
        }
    }

    fun playCompletedFanfare(soundEnabled: Boolean = true, vibrationEnabled: Boolean = true) {
        if (soundEnabled) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 500)
            } catch (_: Exception) {}
        }
        if (vibrationEnabled) {
            vibrate(400)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
    }
}
