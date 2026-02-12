package com.plankton.one102.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.plankton.one102.domain.Settings
import kotlin.math.roundToInt

enum class HapticKind {
    Click,
    Success,
}

fun performHaptic(context: Context, settings: Settings, kind: HapticKind = HapticKind.Click) {
    if (!settings.hapticsEnabled) return

    val strength = settings.hapticsStrength.coerceIn(0f, 1f)
    val amplitude = (1 + (strength * 254f)).roundToInt().coerceIn(1, 255)
    val durationMs = when (kind) {
        HapticKind.Click -> (10 + strength * 18f).roundToInt()
        HapticKind.Success -> (16 + strength * 28f).roundToInt()
    }.coerceIn(8, 60)

    val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        ?: context.getSystemService(Vibrator::class.java)
        ?: return
    if (!vibrator.hasVibrator()) return

    vibrator.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), amplitude))
}

