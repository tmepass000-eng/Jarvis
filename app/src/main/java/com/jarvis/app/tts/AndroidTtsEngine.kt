package com.jarvis.app.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.jarvis.app.core.TtsEngine
import java.util.Locale

/**
 * Android's built-in TTS. Phase 2b adds offline Piper voices via sherpa-onnx;
 * this stays as the fallback.
 */
class AndroidTtsEngine(context: Context) : TtsEngine {

    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private val pending = mutableListOf<() -> Unit>()
    private val callbacks = HashMap<String, () -> Unit>()
    private var counter = 0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                pending.forEach { it() }
            }
            pending.clear()
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                main.post { utteranceId?.let { callbacks.remove(it) } }
            }
        })
    }

    private fun finish(id: String?) {
        main.post { id?.let { callbacks.remove(it)?.invoke() } }
    }

    private fun whenReady(block: () -> Unit) {
        if (ready) block() else pending.add(block)
    }

    var pitch = 1f
    var rate = 1f

    /** Applies language and (optionally) a specific voice by name. */
    fun configure(languageTag: String, voiceName: String?, pitch: Float, rate: Float) = whenReady {
        this.pitch = pitch
        this.rate = rate
        val locale = Locale.forLanguageTag(languageTag)
        tts.language = locale
        val v = voiceName?.let { name -> runCatching { tts.voices }.getOrNull()?.firstOrNull { it.name == name } }
        if (v != null) tts.voice = v
        tts.setPitch(pitch)
        tts.setSpeechRate(rate)
    }

    /** Installed voices for the language, offline ones first. */
    fun voicesFor(languageTag: String, result: (List<Voice>) -> Unit) = whenReady {
        val lang = Locale.forLanguageTag(languageTag)
        val list = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == lang.language && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy({ it.locale.country != lang.country }, { it.isNetworkConnectionRequired }, { it.name }))
        result(list)
    }

    override fun speak(text: String, onDone: () -> Unit) = whenReady {
        val id = "u${++counter}"
        callbacks[id] = onDone
        val r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (r != TextToSpeech.SUCCESS) {
            callbacks.remove(id)
            onDone()
        }
    }

    override fun stop() {
        callbacks.clear()
        if (ready) tts.stop()
    }

    override fun release() {
        stop()
        tts.shutdown()
    }
}
