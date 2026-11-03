package com.jarvis.app.tts

import android.content.Context
import com.jarvis.app.core.TtsEngine
import com.jarvis.app.settings.SettingsRepository

/** Uses the chosen offline Piper voice when it is downloaded, otherwise Android's TTS. */
class HybridTts(private val context: Context, val android: AndroidTtsEngine) : TtsEngine {
    private val settings = SettingsRepository.get(context)
    private val piper by lazy { PiperTtsEngine(context) }
    private var piperUsed = false

    override fun speak(text: String, onDone: () -> Unit) {
        val v = PiperVoices.byId(settings.piperVoice.value)
        if (v != null && PiperVoices.isInstalled(context, v)) {
            piperUsed = true
            piper.speak(v, text, settings.rate.value.coerceIn(0.6f, 1.4f), onDone,
                onFail = { android.speak(text, onDone) })
        } else {
            android.speak(text, onDone)
        }
    }

    override fun stop() {
        android.stop()
        if (piperUsed) piper.stop()
    }

    override fun release() {
        android.release()
        if (piperUsed) piper.release()
    }
}
