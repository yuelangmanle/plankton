package com.voiceassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.IBinder
import android.provider.Settings
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.voiceassistant.audio.AudioShare
import com.voiceassistant.audio.RecordFormat
import com.voiceassistant.audio.VoiceRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayService : Service() {
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recorder by lazy { VoiceRecorder(this) }
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var recording = false

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        startForeground(NOTIFY_ID, buildNotification())
        showOverlay()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        scope.cancel()
        if (recorder.isRecording()) scope.launch { recorder.stop() }
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = ensureChannel()
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = android.app.PendingIntent.getActivity(
            this,
            0,
            openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("语音识别助手")
            .setContentText("悬浮助手运行中")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel(): String {
        val channelId = "voice_assistant_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "语音助手",
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        return channelId
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val textView = TextView(this).apply {
            text = "语音"
            contentDescription = "语音助手悬浮录音"
            setPadding(24, 12, 24, 12)
            setBackgroundColor(0xAA000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 24
        params.y = 240
        overlayParams = params
        installTouchHandler(textView)
        windowManager.addView(textView, params)
        overlayView = textView
    }

    private fun installTouchHandler(view: TextView) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var lastX = 0f
        var lastY = 0f
        var moved = false
        var longPressStarted = false
        var downAt = 0L
        val longPress = Runnable {
            if (overlayView !== view || moved || recording) return@Runnable
            if (!recorder.start(RecordFormat.M4A)) {
                view.text = "无录音权限"
                view.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                return@Runnable
            }
            recording = true
            longPressStarted = true
            view.text = "录音中"
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
        view.setOnTouchListener { _, event ->
            val params = overlayParams ?: return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    lastX = downX
                    lastY = downY
                    downAt = SystemClock.elapsedRealtime()
                    moved = false
                    longPressStarted = false
                    mainHandler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                        moved = true
                        mainHandler.removeCallbacks(longPress)
                    }
                    if (!longPressStarted) {
                        params.x = (params.x - (event.rawX - lastX).toInt()).coerceAtLeast(0)
                        params.y = (params.y + (event.rawY - lastY).toInt()).coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPress)
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (recording) {
                        when {
                            dy > touchSlop * 2 -> finishRecording(view, discard = true)
                            else -> finishRecording(view, discard = false)
                        }
                    } else if (!moved && SystemClock.elapsedRealtime() - downAt < ViewConfiguration.getLongPressTimeout()) {
                        openMainActivity()
                    } else if (moved && kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > touchSlop * 2) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun finishRecording(view: TextView, discard: Boolean) {
        scope.launch {
            val result = recorder.stop()
            recording = false
            if (discard) {
                result.file?.delete()
                view.text = "已丢弃"
                view.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
            } else if (result.error == null && result.file != null) {
                view.text = "已录音"
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                val uri = AudioShare.toShareUri(this@OverlayService, result.file).toString()
                android.widget.Toast.makeText(this@OverlayService, "录音已保存，可在语音助手主界面导入\n$uri", android.widget.Toast.LENGTH_LONG).show()
                openMainActivity()
            } else {
                view.text = "录音失败"
            }
        }
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    companion object {
        private const val NOTIFY_ID = 1001
    }
}
