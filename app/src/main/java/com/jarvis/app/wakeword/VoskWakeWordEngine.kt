package com.jarvis.app.wakeword

import android.content.Context
import android.util.Log
import com.jarvis.app.core.WakeWordEngine
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Offline wake word using Vosk with a restricted grammar: the recognizer can only output
 * "jarvis" or [unk], which keeps CPU low and false matches rare.
 */
class VoskWakeWordEngine(private val context: Context) : WakeWordEngine, RecognitionListener {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    @Volatile var isLoading = false
        private set
    private var onWake: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    @Volatile private var sensitivity = 0.5f
    @Volatile private var running = false
    @Volatile private var wanted = false
    private var lastWakeAt = 0L

    /** Confidence of the last accepted detection, for the event log. */
    @Volatile var lastConfidence = 0f
        private set

    override val isRunning: Boolean get() = running

    override fun setSensitivity(value: Float) { sensitivity = value.coerceIn(0f, 1f) }

    override fun start(onWake: () -> Unit, onError: (String) -> Unit) {
        this.onWake = onWake
        this.onError = onError
        wanted = true
        if (running || isLoading) return
        val loaded = model
        if (loaded != null) {
            startListening(loaded)
            return
        }
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        isLoading = true
        // Copies the bundled model from assets to internal storage once (skipped if uuid matches).
        StorageService.unpack(context, "model-en-us", "model",
            { m ->
                isLoading = false
                model = m
                if (wanted) startListening(m)
            },
            { e ->
                isLoading = false
                Log.e(TAG, "Model unpack failed", e)
                onError("Could not load voice model: ${e.message}")
            })
    }

    private fun startListening(m: Model) {
        try {
            val rec = Recognizer(m, SAMPLE_RATE, GRAMMAR).apply { setWords(true) }
            recognizer = rec
            speechService = SpeechService(rec, SAMPLE_RATE).also { it.startListening(this) }
            running = true
        } catch (e: Exception) {
            Log.e(TAG, "Start failed", e)
            running = false
            onError?.invoke("Microphone could not start: ${e.message}")
        }
    }

    override fun stop() {
        wanted = false
        running = false
        speechService?.let {
            it.stop()
            it.shutdown()
        }
        speechService = null
        recognizer?.close()
        recognizer = null
    }

    override fun release() {
        stop()
        model?.close()
        model = null
    }

    // Partial results have no confidence, so only very loose settings act on them for speed.
    override fun onPartialResult(hypothesis: String?) {
        if (sensitivity < PARTIAL_MIN_SENSITIVITY || hypothesis == null) return
        val partial = runCatching { JSONObject(hypothesis).optString("partial") }.getOrDefault("")
        if (partial.split(" ").contains(WORD)) fire(0.5f)
    }

    override fun onResult(hypothesis: String?) = handleFinal(hypothesis)
    override fun onFinalResult(hypothesis: String?) = handleFinal(hypothesis)

    private fun handleFinal(hypothesis: String?) {
        if (hypothesis == null) return
        val json = runCatching { JSONObject(hypothesis) }.getOrNull() ?: return
        val words = json.optJSONArray("result") ?: return
        // sensitivity 0 -> need conf 0.95, 0.5 -> 0.70, 1 -> 0.45
        val threshold = 0.95f - 0.5f * sensitivity
        for (i in 0 until words.length()) {
            val w = words.optJSONObject(i) ?: continue
            val conf = w.optDouble("conf", 0.0).toFloat()
            if (w.optString("word") == WORD && conf >= threshold) {
                fire(conf)
                return
            }
        }
    }

    private fun fire(conf: Float) {
        val now = System.currentTimeMillis()
        if (now - lastWakeAt < DEBOUNCE_MS) return
        lastWakeAt = now
        lastConfidence = conf
        onWake?.invoke()
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Recognizer error", exception)
        running = false
        onError?.invoke("Listening stopped: ${exception?.message}")
    }

    override fun onTimeout() {
        running = false
    }

    companion object {
        private const val TAG = "VoskWake"
        private const val WORD = "jarvis"
        private const val GRAMMAR = "[\"jarvis\", \"[unk]\"]"
        private const val SAMPLE_RATE = 16000f
        private const val DEBOUNCE_MS = 2000L
        private const val PARTIAL_MIN_SENSITIVITY = 0.8f
    }
}
