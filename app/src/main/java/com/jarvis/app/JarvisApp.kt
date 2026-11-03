package com.jarvis.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.jarvis.app.conversation.Conversation
import com.jarvis.app.data.JarvisDatabase
import com.jarvis.app.leads.LeadFinder

class JarvisApp : Application() {
    val database: JarvisDatabase by lazy { JarvisDatabase.get(this) }
    val leadFinder: LeadFinder by lazy { LeadFinder(this, database) }
    val conversation: Conversation by lazy { Conversation(this, database, leadFinder) }

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
