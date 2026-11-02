package com.jarvis.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.jarvis.app.JarvisApp
import com.jarvis.app.MainActivity
import com.jarvis.app.R
import com.jarvis.app.core.JarvisState
import com.jarvis.app.core.StateHolder
import com.jarvis.app.conversation.MicHost
import com.jarvis.app.data.WakeEvent
import com.jarvis.app.settings.SettingsRepository
import com.jarvis.app.wakeword.VoskWakeWordEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Foreground service (type microphone) that owns the wake word engine. */
class JarvisService : LifecycleService(), MicHost {

    private lateinit var engine: VoskWakeWordEngine
    private lateinit var settings: SettingsRepository
    private var watchdogJob: Job? = null
    private var pausedForCall = false
    private var tone: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository.get(this)
        engine = VoskWakeWordEngine(this)
        tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()
        lifecycleScope.launch {
            settings.sensitivity.collect { engine.setSensitivity(it) }
        }
        conversation.host = this
    }

    private val conversation get() = (application as JarvisApp).conversation

    // MicHost: hand the mic to speech recognition and take it back.
    override fun releaseWakeMic() { engine.stop() }
    override fun resumeWakeMic() {
        if (!pausedForCall && StateHolder.serviceRunning.value && !engine.isRunning) startEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            )
        } catch (e: Exception) {
            // Android 14 refuses a mic service started from the background (e.g. a sticky restart).
            StateHolder.setStatus("Open JARVIS and turn listening on again")
            stopSelf()
            return START_NOT_STICKY
        }
        StateHolder.setServiceRunning(true)
        startEngine()
        startWatchdog()
        return START_STICKY
    }

    private fun startEngine() {
        StateHolder.setStatus("Loading voice model…")
        engine.start(
            onWake = ::onWakeWord,
            onError = { msg -> StateHolder.setStatus(msg) }
        )
        if (engine.isRunning) StateHolder.setStatus("Listening for \"Jarvis\"")
        lifecycleScope.launch {
            // Model unpack is async on first run; update status once it is live.
            repeat(60) {
                if (engine.isRunning) { StateHolder.setStatus("Listening for \"Jarvis\""); return@launch }
                delay(500)
            }
        }
    }

    /**
     * Phone calls take the mic away from background apps. Poll the audio mode: pause during a call,
     * restart once the call ends, and restart if the recognizer died for any other reason.
     */
    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        val audio = getSystemService(AudioManager::class.java)
        watchdogJob = lifecycleScope.launch {
            while (isActive) {
                delay(2000)
                val inCall = audio.mode == AudioManager.MODE_IN_CALL ||
                        audio.mode == AudioManager.MODE_IN_COMMUNICATION ||
                        audio.mode == AudioManager.MODE_RINGTONE
                if (inCall && !pausedForCall) {
                    pausedForCall = true
                    conversation.stopAll()
                    engine.stop()
                    StateHolder.setStatus("Paused during call")
                } else if (!inCall && pausedForCall) {
                    pausedForCall = false
                    startEngine()
                } else if (!inCall && !engine.isRunning && !engine.isLoading &&
                    StateHolder.state.value == JarvisState.IDLE) {
                    startEngine()
                }
            }
        }
    }

    private fun onWakeWord() {
        val conf = engine.lastConfidence
        lifecycleScope.launch {
            val dao = (application as JarvisApp).database.wakeEventDao()
            dao.insert(WakeEvent(timestamp = System.currentTimeMillis(), confidence = conf))
            dao.trim()
        }
        when (StateHolder.state.value) {
            JarvisState.SPEAKING -> conversation.bargeIn()
            JarvisState.IDLE -> {
                tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
                bringAppToFront()
                // Let the chime finish before the recognizer opens the mic.
                lifecycleScope.launch { delay(200); conversation.startListening() }
            }
            else -> Unit
        }
    }

    private fun bringAppToFront() {
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        // Wake the screen briefly so the activity is visible.
        val pm = getSystemService(PowerManager::class.java)
        @Suppress("DEPRECATION")
        runCatching {
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "jarvis:wake"
            ).acquire(3000)
        }
        // Android blocks background activity starts unless "Display over other apps" is allowed.
        if (Settings.canDrawOverlays(this)) {
            runCatching { startActivity(open) }
        }
        // Fallback that always works: a high-priority notification with a full-screen intent.
        val pi = PendingIntent.getActivity(
            this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, JarvisApp.CHANNEL_WAKE)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("JARVIS")
            .setContentText("Listening…")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setTimeoutAfter(8000)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(WAKE_NOTIF_ID, n) }
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            this, 2, Intent(this, JarvisService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, JarvisApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("JARVIS is listening")
            .setContentText("Say \"Jarvis\"")
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(0, "Stop", stopPi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun stopEverything() {
        watchdogJob?.cancel()
        conversation.host = null
        conversation.stopAll()
        engine.stop() // releases AudioRecord, so the mic indicator turns off
        StateHolder.setState(JarvisState.IDLE)
        StateHolder.setServiceRunning(false)
        StateHolder.setStatus("Stopped")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        if (conversation.host === this) conversation.host = null
        engine.release()
        tone?.release()
        StateHolder.setServiceRunning(false)
        StateHolder.setState(JarvisState.IDLE)
        StateHolder.setStatus("Stopped")
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 42
        private const val WAKE_NOTIF_ID = 43
        const val ACTION_STOP = "com.jarvis.app.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, JarvisService::class.java))
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, JarvisService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}
