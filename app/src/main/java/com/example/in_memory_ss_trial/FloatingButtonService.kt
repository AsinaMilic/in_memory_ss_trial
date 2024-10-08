package com.example.in_memory_ss_trial

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat

class FloatingButtonService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: View

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingButton = inflater.inflate(R.layout.floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        windowManager.addView(floatingButton, params)

        floatingButton.findViewById<Button>(R.id.captureButton).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_CAPTURE_SCREEN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("FloatingButtonService", "onStartCommand called")

        startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(floatingButton)
    }

    private fun createNotification(): Notification {
        val channelId = createNotificationChannel("floating_button", "Floating Button Service")

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen Capture")
            .setContentText("Floating button is active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        const val ACTION_SCREEN_CAPTURE_READY = "com.example.in_memory_ss_trial.ACTION_SCREEN_CAPTURE_READY"
    }
}
