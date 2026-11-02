package com.jarvis.app.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jarvis.app.core.SttCallback
import com.jarvis.app.core.SttEngine

/** Android's built-in recognizer. Must be used from the main thread. */
class AndroidSttEngine(private val context: Context) : SttEngine {

    private var recognizer: SpeechRecognizer? = null
    private var callback: SttCallback? = null
    private var session = 0

    override fun listen(languageTag: String, callback: SttCallback) {
        cancel()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onError("Speech recognition is not available. Install or update the Google app.", false)
            return
        }
        this.callback = callback
        val mySession = ++session
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            private fun live() = mySession == session
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // rms is roughly -2..10 dB
                if (live()) callback.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (!live()) return
                session++
                val noSpeech = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                callback.onError(describe(error), noSpeech)
                destroy()
            }
            override fun onResults(results: Bundle?) {
                if (!live()) return
                session++
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) callback.onError("Didn't catch that", true) else callback.onResult(text)
                destroy()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (!live()) return
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) callback.onPartial(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // End of speech after ~1.5 s of silence (some phones ignore these hints).
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching { rec.startListening(intent) }.onFailure {
            session++
            callback.onError("Microphone busy: ${it.message}", false)
            destroy()
        }
    }

    private fun destroy() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
    }

    override fun cancel() {
        session++
        recognizer?.let { runCatching { it.cancel() } }
        destroy()
    }

    override fun release() = cancel()

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No internet for speech recognition"
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy, try again"
        else -> "Speech error ($error)"
    }
}
