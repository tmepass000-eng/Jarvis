package com.jarvis.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.jarvis.app.data.JarvisDatabase

class JarvisApp : Application() {
    val database: JarvisDatabase by lazy { JarvisDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Listening", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shows while JARVIS listens for its wake word" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WAKE, "Wake alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Opens JARVIS when you say Jarvis" }
        )
    }

    companion object {
        const val CHANNEL_SERVICE = "jarvis_service"
        const val CHANNEL_WAKE = "jarvis_wake"
    }
}
