package com.plankton.one102

import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Build
import android.view.Surface
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.doOnAttach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.plankton.one102.data.prefs.AppPreferences
import com.plankton.one102.domain.DisplayRefreshMode
import com.plankton.one102.ui.PlanktonApp
import com.plankton.one102.ui.theme.PlanktonTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var lastRefreshMode: DisplayRefreshMode = DisplayRefreshMode.Adaptive
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            val currentId = window.decorView.display?.displayId ?: return
            if (displayId == currentId) {
                applyRefreshMode(window, lastRefreshMode)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        (getSystemService(DisplayManager::class.java))?.registerDisplayListener(displayListener, null)
        observeRefreshMode()
        observeWindowLifecycle()

        setContent {
            PlanktonTheme {
                PlanktonApp()
            }
        }
    }

    private fun observeRefreshMode() {
        val prefs = AppPreferences(applicationContext)
        var lastMode: DisplayRefreshMode? = null
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                prefs.settings.collect { settings ->
                    val mode = settings.displayRefreshMode
                    if (lastMode != mode) {
                        lastRefreshMode = mode
                        applyRefreshMode(window, mode)
                        lastMode = mode
                    }
                }
            }
        }
    }

    private fun observeWindowLifecycle() {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                applyRefreshMode(window, lastRefreshMode)
            }
        }
        lifecycle.addObserver(observer)
        window.decorView.doOnAttach {
            applyRefreshMode(window, lastRefreshMode)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyRefreshMode(window, lastRefreshMode)
        }
    }

    override fun onDestroy() {
        (getSystemService(DisplayManager::class.java))?.unregisterDisplayListener(displayListener)
        super.onDestroy()
    }
}

private fun applyRefreshMode(window: Window, mode: DisplayRefreshMode) {
    val display = window.decorView.display ?: return
    val attrs = window.attributes
    val targetHz = when (mode) {
        DisplayRefreshMode.Adaptive -> null
        DisplayRefreshMode.Hz60 -> 60f
        DisplayRefreshMode.Hz90 -> 90f
        DisplayRefreshMode.Hz120 -> 120f
    }

    if (targetHz == null) {
        if (attrs.preferredDisplayModeId != 0) {
            attrs.preferredDisplayModeId = 0
        }
        attrs.preferredRefreshRate = 0f
        window.attributes = attrs
        applyFrameRateHint(window, 0f)
        return
    }

    val selectedMode = pickDisplayMode(display.mode, display.supportedModes.toList(), targetHz)
    if (selectedMode == null) {
        attrs.preferredDisplayModeId = 0
        attrs.preferredRefreshRate = targetHz
        window.attributes = attrs
        applyFrameRateHint(window, targetHz)
        return
    }
    attrs.preferredDisplayModeId = selectedMode.modeId
    attrs.preferredRefreshRate = selectedMode.refreshRate
    window.attributes = attrs
    applyFrameRateHint(window, selectedMode.refreshRate)
}

private fun pickDisplayMode(
    currentMode: android.view.Display.Mode,
    modes: List<android.view.Display.Mode>,
    targetHz: Float,
): android.view.Display.Mode? {
    if (modes.isEmpty()) return null
    val sameResolutionModes = modes.filter {
        it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight
    }
    val candidates = if (sameResolutionModes.isNotEmpty()) sameResolutionModes else modes
    val exact = candidates
        .filter { abs(it.refreshRate - targetHz) <= 0.5f }
        .maxByOrNull { it.refreshRate }
    if (exact != null) return exact
    return candidates.minWithOrNull(
        compareBy<android.view.Display.Mode> { abs(it.refreshRate - targetHz) }
            .thenByDescending { it.refreshRate },
    )
}

private fun applyFrameRateHint(window: Window, hz: Float) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val view = window.decorView
    val compatibility = if (hz > 0f) {
        Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
    } else {
        Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
    }
    runCatching {
        val method = view.javaClass.getMethod(
            "setFrameRate",
            Float::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        method.invoke(view, hz, compatibility, Surface.CHANGE_FRAME_RATE_ALWAYS)
    }
}
