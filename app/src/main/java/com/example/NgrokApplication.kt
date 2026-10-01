package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class NgrokApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    try {
      com.ngrok.Runtime.load()
      android.util.Log.i("NgrokApplication", "Eagerly initialized ngrok native JNI runtime")
    } catch (t: Throwable) {
      android.util.Log.w("NgrokApplication", "Early native runtime initialization warning: ${t.message}")
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = "Ngrok Tunnel Service"
      val descriptionText = "Notifications for active Ngrok background tunnel service"
      val importance = NotificationManager.IMPORTANCE_LOW
      val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
        description = descriptionText
      }
      val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  companion object {
    const val CHANNEL_ID = "ngrok_tunnel_channel"
  }
}
