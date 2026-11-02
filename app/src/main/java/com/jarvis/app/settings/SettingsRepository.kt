package com.jarvis.app.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small key-value settings. API keys will live here too (phase 2), never in code. */
class SettingsRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)

    private val _sensitivity = MutableStateFlow(prefs.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY))
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(v) { prefs.edit().putBoolean(KEY_ONBOARDING, v).apply() }

    var batteryGuideShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY, false)
        set(v) { prefs.edit().putBoolean(KEY_BATTERY, v).apply() }

    fun setSensitivity(value: Float) {
        val v = value.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_SENSITIVITY, v).apply()
        _sensitivity.value = v
    }

    companion object {
        const val DEFAULT_SENSITIVITY = 0.5f
        private const val KEY_SENSITIVITY = "wake_sensitivity"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_BATTERY = "battery_guide_shown"

        @Volatile private var instance: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
