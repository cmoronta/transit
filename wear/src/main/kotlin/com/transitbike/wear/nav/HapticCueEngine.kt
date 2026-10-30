package com.transitbike.wear.nav

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.transitbike.shared.model.CueEvent
import com.transitbike.shared.model.CueThreshold

class HapticCueEngine(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Triggers distinct tactical haptic vibration patterns per distance cue.
     */
    fun playCueHaptic(cue: CueEvent) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        when (cue.threshold) {
            CueThreshold.APPROACH_200M -> {
                // Gentle double tap
                val timings = longArrayOf(0, 80, 120, 80)
                val amplitudes = intArrayOf(0, 140, 0, 140)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
            CueThreshold.PREPARE_50M -> {
                // Firm ramping pulse
                val timings = longArrayOf(0, 120, 80, 180)
                val amplitudes = intArrayOf(0, 180, 0, 255)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
            CueThreshold.NOW_15M -> {
                // Sharp triple alert (Turn now!)
                val timings = longArrayOf(0, 100, 50, 100, 50, 180)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        }
    }
}
