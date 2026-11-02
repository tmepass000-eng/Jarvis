package com.jarvis.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single source of truth for JARVIS state, shared by the service and the UI. */
object StateHolder {
    private val _state = MutableStateFlow(JarvisState.IDLE)
    val state: StateFlow<JarvisState> = _state.asStateFlow()

    /** True while the foreground service is running and the mic is open. */
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    /** Short human-readable status, e.g. "Loading model…" or an error. */
    private val _status = MutableStateFlow("Stopped")
    val status: StateFlow<String> = _status.asStateFlow()

    /** Live mic loudness 0..1 while listening, drives the orb. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    /** What the user is saying right now (partial transcript). */
    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    fun setState(newState: JarvisState) { _state.value = newState }
    fun setServiceRunning(running: Boolean) { _serviceRunning.value = running }
    fun setStatus(text: String) { _status.value = text }
    fun setLevel(v: Float) { _level.value = v.coerceIn(0f, 1f) }
    fun setPartial(text: String) { _partial.value = text }
}
