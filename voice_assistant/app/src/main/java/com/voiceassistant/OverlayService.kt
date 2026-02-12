package com.voiceassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class OverlayService : Service() {
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var overlayView: View? = null

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
            setPadding(24, 12, 24, 12)
            setBackgroundColor(0xAA000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                val intent = Intent(this@OverlayService, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            setOnLongClickListener {
                android.widget.Toast.makeText(this@OverlayService, "请进入主界面录音/转写", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
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
        windowManager.addView(textView, params)
        overlayView = textView
    }

    companion object {
        private const val NOTIFY_ID = 1001
    }
}
