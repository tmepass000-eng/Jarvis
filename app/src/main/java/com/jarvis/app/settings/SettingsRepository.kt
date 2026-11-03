package com.jarvis.app.settings

import android.content.Context
import com.jarvis.app.brain.GeminiBrain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Small key-value settings. The Gemini key lives only here, on the phone, never in code. */
class SettingsRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)

    private fun <T> flow(value: T) = MutableStateFlow(value)

    private val _sensitivity = flow(prefs.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY))
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    private val _geminiKey = flow(prefs.getString(KEY_GEMINI, "").orEmpty())
    val geminiKey: StateFlow<String> = _geminiKey.asStateFlow()

    private val _model = flow(prefs.getString(KEY_MODEL, GeminiBrain.DEFAULT_MODEL).orEmpty())
    val model: StateFlow<String> = _model.asStateFlow()

    private val _language = flow(prefs.getString(KEY_LANG, "en-IN").orEmpty())
    /** Speech language tag: en-IN, hi-IN or en-US. */
    val language: StateFlow<String> = _language.asStateFlow()

    private val _voice = flow(prefs.getString(KEY_VOICE, null))
    val voice: StateFlow<String?> = _voice.asStateFlow()

    private val _piperVoice = flow(prefs.getString(KEY_PIPER, null))
    /** Id of the offline Piper voice in use, or null for the Android voice. */
    val piperVoice: StateFlow<String?> = _piperVoice.asStateFlow()

    private val _pitch = flow(prefs.getFloat(KEY_PITCH, 0.85f))
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _rate = flow(prefs.getFloat(KEY_RATE, 0.95f))
    val rate: StateFlow<Float> = _rate.asStateFlow()

    private val _followUp = flow(prefs.getBoolean(KEY_FOLLOWUP, true))
    /** Keep listening for a reply after JARVIS speaks. */
    val followUp: StateFlow<Boolean> = _followUp.asStateFlow()

    private val _tavilyKey = flow(prefs.getString(KEY_TAVILY, "").orEmpty())
    /** Optional Tavily key (1,000 free searches a month). Without it, Gemini's built-in Google Search is used. */
    val tavilyKey: StateFlow<String> = _tavilyKey.asStateFlow()

    private val _leadTarget = flow(prefs.getInt(KEY_LEAD_TARGET, 25))
    val leadTarget: StateFlow<Int> = _leadTarget.asStateFlow()

    private val _requestsToday = flow(currentCount())
    val requestsToday: StateFlow<Int> = _requestsToday.asStateFlow()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(v) { prefs.edit().putBoolean(KEY_ONBOARDING, v).apply() }

    var batteryGuideShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY, false)
        set(v) { prefs.edit().putBoolean(KEY_BATTERY, v).apply() }

    fun setSensitivity(value: Float) {
        val v = value.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_SENSITIVITY, v).apply(); _sensitivity.value = v
    }
    fun setGeminiKey(v: String) { prefs.edit().putString(KEY_GEMINI, v.trim()).apply(); _geminiKey.value = v.trim() }
    fun setModel(v: String) { prefs.edit().putString(KEY_MODEL, v.trim()).apply(); _model.value = v.trim() }
    fun setLanguage(v: String) {
        prefs.edit().putString(KEY_LANG, v).remove(KEY_VOICE).apply(); _language.value = v; _voice.value = null
    }
    fun setVoice(v: String?) { prefs.edit().putString(KEY_VOICE, v).apply(); _voice.value = v }
    fun setTavilyKey(v: String) { prefs.edit().putString(KEY_TAVILY, v.trim()).apply(); _tavilyKey.value = v.trim() }
    fun setLeadTarget(v: Int) { prefs.edit().putInt(KEY_LEAD_TARGET, v).apply(); _leadTarget.value = v }
    fun setPiperVoice(v: String?) { prefs.edit().putString(KEY_PIPER, v).apply(); _piperVoice.value = v }
    fun setPitch(v: Float) { prefs.edit().putFloat(KEY_PITCH, v).apply(); _pitch.value = v }
    fun setRate(v: Float) { prefs.edit().putFloat(KEY_RATE, v).apply(); _rate.value = v }
    fun setFollowUp(v: Boolean) { prefs.edit().putBoolean(KEY_FOLLOWUP, v).apply(); _followUp.value = v }

    /** Gemini's free daily quota resets at midnight Pacific, so count by the Pacific date. */
    private fun quotaDay(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("America/Los_Angeles") }.format(Date())

    private fun currentCount(): Int =
        if (prefs.getString(KEY_REQ_DAY, "") == quotaDay()) prefs.getInt(KEY_REQ_COUNT, 0) else 0

    fun countRequest() {
        val n = currentCount() + 1
        prefs.edit().putString(KEY_REQ_DAY, quotaDay()).putInt(KEY_REQ_COUNT, n).apply()
        _requestsToday.value = n
    }

    fun refreshCount() { _requestsToday.value = currentCount() }

    companion object {
        const val DEFAULT_SENSITIVITY = 0.5f
        private const val KEY_SENSITIVITY = "wake_sensitivity"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_BATTERY = "battery_guide_shown"
        private const val KEY_GEMINI = "gemini_key"
        private const val KEY_MODEL = "gemini_model"
        private const val KEY_LANG = "speech_lang"
        private const val KEY_VOICE = "tts_voice"
        private const val KEY_PIPER = "piper_voice"
        private const val KEY_TAVILY = "tavily_key"
        private const val KEY_LEAD_TARGET = "lead_target"
        private const val KEY_PITCH = "tts_pitch"
        private const val KEY_RATE = "tts_rate"
        private const val KEY_FOLLOWUP = "follow_up"
        private const val KEY_REQ_DAY = "req_day"
        private const val KEY_REQ_COUNT = "req_count"

        @Volatile private var instance: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
