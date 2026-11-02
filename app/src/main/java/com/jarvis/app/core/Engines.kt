package com.jarvis.app.core

/** Always-on wake word detector. Implemented in phase 1 (Vosk). */
interface WakeWordEngine {
    /** Loads the model if needed and starts listening. [onWake] fires once per detection. */
    fun start(onWake: () -> Unit, onError: (String) -> Unit)
    fun stop()
    fun release()
    val isRunning: Boolean
    /** 0.0 = strict (fewer false triggers), 1.0 = loose (catches quiet voices). */
    fun setSensitivity(value: Float)
}

/** Callbacks from one speech-to-text session. All arrive on the main thread. */
interface SttCallback {
    fun onPartial(text: String) {}
    fun onLevel(level: Float) {}
    fun onResult(text: String)
    /** [noSpeech] is true when the user simply said nothing. */
    fun onError(message: String, noSpeech: Boolean)
}

/** Speech to text. Phase 2a: Android SpeechRecognizer. Phase 2b adds sherpa-onnx offline. */
interface SttEngine {
    fun listen(languageTag: String, callback: SttCallback)
    fun cancel()
    fun release()
}

data class ChatMessage(val fromUser: Boolean, val text: String)

/** SPOKEN is what JARVIS says aloud (max 2 sentences). DETAIL is the longer answer, spoken only on request. */
data class BrainReply(val spoken: String, val detail: String)

class BrainException(val userMessage: String) : Exception(userMessage)

/** The AI brain (Gemini first, Groq later). */
interface Brain {
    /** [onSpoken] fires as soon as the SPOKEN part has streamed in, so speech can start early. */
    suspend fun reply(history: List<ChatMessage>, userText: String, onSpoken: (String) -> Unit): BrainReply
}

/** Text to speech. */
interface TtsEngine {
    fun speak(text: String, onDone: () -> Unit)
    fun stop()
    fun release()
}

/** Free-tier web search for lead finding. Implemented in phase 3. */
interface WebSearch
