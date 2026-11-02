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

/** Speech to text. Implemented in phase 2. */
interface SttEngine

/** The AI brain (Gemini first, Groq later). Implemented in phase 2. */
interface Brain

/** Text to speech. Implemented in phase 2. */
interface TtsEngine

/** Free-tier web search for lead finding. Implemented in phase 3. */
interface WebSearch
