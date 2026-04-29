package com.shadaeiou.stitchcounter.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun light() = vibrate(durationMs = 18, amplitude = 90)
    fun medium() = vibrate(durationMs = 28, amplitude = 160)
    fun heavy() = vibrate(durationMs = 45, amplitude = 255)
    fun pull() = vibrate(durationMs = 22, amplitude = 140)

    fun shake() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val pattern = longArrayOf(0, 30, 40, 30, 40, 30)
        val amplitudes = intArrayOf(0, 200, 0, 200, 0, 200)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, -1)
        }
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val a = if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(VibrationEffect.createOneShot(durationMs, a))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }
}
