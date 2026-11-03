package com.jarvis.app.conversation

import android.content.Context
import com.jarvis.app.brain.GeminiBrain
import com.jarvis.app.core.BrainException
import com.jarvis.app.core.ChatMessage
import com.jarvis.app.core.JarvisState
import com.jarvis.app.core.StateHolder
import com.jarvis.app.core.SttCallback
import com.jarvis.app.data.ChatTurn
import com.jarvis.app.data.JarvisDatabase
import com.jarvis.app.settings.SettingsRepository
import com.jarvis.app.stt.AndroidSttEngine
import com.jarvis.app.tts.AndroidTtsEngine
import com.jarvis.app.tts.HybridTts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The service implements this so the wake-word mic can be handed over to speech recognition. */
interface MicHost {
    /** Release the wake-word mic so speech recognition can use it. */
    fun releaseWakeMic()
    /** Listen for "Jarvis" again (also used for barge-in while speaking). */
    fun resumeWakeMic()
}

/**
 * Runs one voice turn: LISTENING -> THINKING -> SPEAKING, then optionally listens for a follow-up.
 * Lives for the whole app so the UI (typed messages) and the service (wake word) share it.
 */
class Conversation(context: Context, private val db: JarvisDatabase) {

    private val settings = SettingsRepository.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stt = AndroidSttEngine(context)
    val androidTts = AndroidTtsEngine(context)
    private val tts = HybridTts(context, androidTts)
    private val brain = GeminiBrain(
        apiKey = { settings.geminiKey.value },
        model = { settings.model.value },
        replyLanguage = { languageRule(settings.language.value) }
    )

    var host: MicHost? = null
    private var thinkJob: Job? = null
    private var lastDetail = ""
    private var turn = 0

    init {
        scope.launch {
            combine(settings.language, settings.voice, settings.pitch, settings.rate) { l, v, p, r -> listOf(l, v, p, r) }
                .collect { (l, v, p, r) -> androidTts.configure(l as String, v as String?, p as Float, r as Float) }
        }
    }

    val state get() = StateHolder.state.value

    /** Called by the wake word or the mic button. */
    fun startListening() {
        val myTurn = ++turn
        tts.stop()
        thinkJob?.cancel()
        host?.releaseWakeMic()
        StateHolder.setState(JarvisState.LISTENING)
        StateHolder.setPartial("")
        scope.launch {
            delay(250) // give the wake-word recorder time to let go of the mic
            if (myTurn != turn) return@launch
            stt.listen(settings.language.value, object : SttCallback {
                override fun onPartial(text: String) { StateHolder.setPartial(text) }
                override fun onLevel(level: Float) { StateHolder.setLevel(level) }
                override fun onResult(text: String) {
                    StateHolder.setLevel(0f)
                    StateHolder.setPartial("")
                    if (myTurn == turn) handleUserText(text, byVoice = true)
                }
                override fun onError(message: String, noSpeech: Boolean) {
                    StateHolder.setLevel(0f)
                    StateHolder.setPartial("")
                    if (myTurn != turn) return
                    if (!noSpeech) StateHolder.setStatus(message)
                    end()
                }
            })
        }
    }

    /** "Jarvis" heard while speaking: stop talking and listen. */
    fun bargeIn() = startListening()

    /** Typed message from the chat box. */
    fun submitText(text: String) {
        if (text.isBlank()) return
        ++turn
        stt.cancel()
        tts.stop()
        handleUserText(text.trim(), byVoice = false)
    }

    fun stopAll() {
        ++turn
        stt.cancel()
        tts.stop()
        thinkJob?.cancel()
        end()
    }

    private fun handleUserText(text: String, byVoice: Boolean) {
        val myTurn = turn
        StateHolder.setState(JarvisState.THINKING)
        thinkJob = scope.launch {
            val dao = db.chatDao()
            val history = dao.lastN(10).reversed().map { ChatMessage(it.fromUser, it.text) }
            dao.insert(ChatTurn(timestamp = System.currentTimeMillis(), fromUser = true, text = text))

            if (DETAILS.containsMatchIn(text) && lastDetail.isNotBlank() && text.split(" ").size <= 6) {
                val d = lastDetail
                lastDetail = ""
                dao.insert(ChatTurn(timestamp = System.currentTimeMillis(), fromUser = false, text = d))
                speak(d, myTurn, byVoice)
                return@launch
            }
            try {
                settings.countRequest()
                var spokenStarted = false
                val reply = brain.reply(history, text) { spoken ->
                    if (myTurn == turn) { spokenStarted = true; speak(spoken, myTurn, byVoice) }
                }
                lastDetail = reply.detail
                dao.insert(ChatTurn(timestamp = System.currentTimeMillis(), fromUser = false,
                    text = reply.spoken, detail = reply.detail))
                if (!spokenStarted && myTurn == turn) speak(reply.spoken, myTurn, byVoice)
            } catch (e: BrainException) {
                dao.insert(ChatTurn(timestamp = System.currentTimeMillis(), fromUser = false,
                    text = e.userMessage, isError = true))
                if (myTurn == turn) speak(e.userMessage, myTurn, allowFollowUp = false)
            }
        }
    }

    private fun speak(text: String, myTurn: Int, allowFollowUp: Boolean = true) {
        StateHolder.setState(JarvisState.SPEAKING)
        host?.resumeWakeMic() // barge-in: saying "Jarvis" stops the speech
        tts.speak(text) {
            if (myTurn != turn) return@speak
            if (allowFollowUp && settings.followUp.value && host != null) startListening() else end()
        }
    }

    /** Plays a short sample with the current voice settings. */
    fun playSample() {
        tts.speak(SAMPLE) {}
    }

    private fun end() {
        StateHolder.setState(JarvisState.IDLE)
        StateHolder.setLevel(0f)
        host?.resumeWakeMic()
    }

    private fun languageRule(tag: String) = when (tag) {
        "hi-IN" -> "Reply in simple Hindi written in Devanagari script."
        "en-US" -> "Reply in clear English."
        else -> "Reply in the user's language. If they use Hindi or Hinglish, reply in Hinglish written in Roman script; otherwise reply in simple Indian English."
    }

    companion object {
        private val DETAILS = Regex("\\b(details?|explain|elaborate|vistar|samjhao|samjha|aur batao)\\b", RegexOption.IGNORE_CASE)
        const val SAMPLE = "Hello, I am JARVIS. Ready when you are."
    }
}
