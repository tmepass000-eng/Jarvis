@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jarvis.app.ui

import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.JarvisApp
import com.jarvis.app.brain.GeminiBrain
import com.jarvis.app.core.BrainException
import com.jarvis.app.settings.SettingsRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(settings: SettingsRepository, onBack: () -> Unit, onOpenBatteryGuide: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as JarvisApp
    val conversation = app.conversation
    val scope = rememberCoroutineScope()

    val sensitivity by settings.sensitivity.collectAsStateWithLifecycle()
    val key by settings.geminiKey.collectAsStateWithLifecycle()
    val model by settings.model.collectAsStateWithLifecycle()
    val language by settings.language.collectAsStateWithLifecycle()
    val voice by settings.voice.collectAsStateWithLifecycle()
    val pitch by settings.pitch.collectAsStateWithLifecycle()
    val rate by settings.rate.collectAsStateWithLifecycle()
    val followUp by settings.followUp.collectAsStateWithLifecycle()
    val requests by settings.requestsToday.collectAsStateWithLifecycle()
    val events by app.database.wakeEventDao().lastTen().collectAsStateWithLifecycle(initialValue = emptyList())

    var keyDraft by remember { mutableStateOf(key) }
    var showKey by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var modelDraft by remember { mutableStateOf(model) }
    var voices by remember { mutableStateOf<List<Voice>>(emptyList()) }

    LaunchedEffect(language) { conversation.tts.voicesFor(language) { voices = it.take(15) } }
    LaunchedEffect(Unit) { settings.refreshCount() }

    Column(
        Modifier
            .fillMaxSize()
            .background(JarvisColors.Charcoal)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = JarvisColors.SoftGold)
            }
            Text("Settings", color = JarvisColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        // ---- Gemini ----
        Card("Gemini brain") {
            Hint("Paste your free key from aistudio.google.com. It stays on this phone only.")
            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                label = { Text("Gemini API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show", color = JarvisColors.SoftGold) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        settings.setGeminiKey(keyDraft)
                        testResult = "Testing…"
                        scope.launch {
                            testResult = try {
                                settings.countRequest()
                                val r = GeminiBrain({ keyDraft }, { model }, { "Reply in English." })
                                    .reply(emptyList(), "Say: key works.") {}
                                "✓ Working: ${r.spoken}"
                            } catch (e: BrainException) { "✗ ${e.userMessage}" }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.Saffron, contentColor = JarvisColors.Charcoal)
                ) { Text("Save & test") }
            }
            if (testResult.isNotBlank()) {
                Text(testResult, color = if (testResult.startsWith("✗")) JarvisColors.Danger else JarvisColors.SoftGold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Label("Model")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GeminiBrain.MODELS.forEach { m ->
                    Chip(m.removePrefix("gemini-"), model == m) { settings.setModel(m); modelDraft = m }
                }
            }
            OutlinedTextField(
                value = modelDraft,
                onValueChange = { modelDraft = it },
                label = { Text("Or type a model name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
                trailingIcon = {
                    TextButton(onClick = { settings.setModel(modelDraft) }) { Text("Use", color = JarvisColors.Saffron) }
                }
            )
            Hint("Requests today: $requests. Flash-Lite allows about 500 a day free, Flash about 20. Resets 1:30 PM India time.")
        }

        // ---- Voice ----
        Card("Voice") {
            Label("Language you speak")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("English (India)", language == "en-IN") { settings.setLanguage("en-IN") }
                Chip("Hindi", language == "hi-IN") { settings.setLanguage("hi-IN") }
                Chip("English (US)", language == "en-US") { settings.setLanguage("en-US") }
            }
            Label("Style")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Deep", pitch < 0.8f) { settings.setPitch(0.72f); settings.setRate(0.95f) }
                Chip("Calm", pitch in 0.8f..0.95f) { settings.setPitch(0.88f); settings.setRate(0.85f) }
                Chip("Bright", pitch > 0.95f) { settings.setPitch(1.12f); settings.setRate(1.0f) }
            }
            Label("Pitch ${"%.2f".format(pitch)}")
            Slider(pitch, { settings.setPitch(it) }, valueRange = 0.5f..1.5f, colors = sliderColors())
            Label("Speed ${"%.2f".format(rate)}")
            Slider(rate, { settings.setRate(it) }, valueRange = 0.6f..1.4f, colors = sliderColors())
            Label("Voice (tap one, then Play sample)")
            if (voices.isEmpty()) Hint("No voices found for this language. Install voices in Android Settings > Text-to-speech.")
            voices.forEachIndexed { i, v ->
                val selected = voice == v.name || (voice == null && i == 0)
                Text(
                    (if (selected) "● " else "○ ") + "Voice ${i + 1}  ·  ${v.locale.displayCountry}" +
                            (if (v.isNetworkConnectionRequired) "  ·  online" else ""),
                    color = if (selected) JarvisColors.SoftGold else JarvisColors.Text,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clickable { settings.setVoice(v.name) }.padding(vertical = 6.dp)
                )
            }
            Button(
                onClick = { conversation.playSample() },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.Saffron, contentColor = JarvisColors.Charcoal)
            ) { Text("▶  Play sample") }
            Hint("Offline studio voices (deep male, calm male, female) arrive in the next update.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep listening after reply", color = JarvisColors.Text)
                    Hint("Lets you answer back without saying \"Jarvis\" again.")
                }
                Switch(followUp, { settings.setFollowUp(it) }, colors = SwitchDefaults.colors(
                    checkedThumbColor = JarvisColors.Charcoal, checkedTrackColor = JarvisColors.Saffron))
            }
        }

        // ---- Wake word ----
        Card("Wake word sensitivity") {
            Hint("Lower = fewer false triggers. Higher = catches quieter voices.")
            Slider(sensitivity, { settings.setSensitivity(it) }, valueRange = 0f..1f, colors = sliderColors())
            Row(Modifier.fillMaxWidth()) {
                Text("Strict", color = JarvisColors.Muted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("${(sensitivity * 100).toInt()}%", color = JarvisColors.Text, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text("Loose", color = JarvisColors.Muted, fontSize = 12.sp)
            }
            TextButton(onClick = { settings.setSensitivity(SettingsRepository.DEFAULT_SENSITIVITY) }) {
                Text("Reset to default (50%)", color = JarvisColors.Saffron)
            }
            Label("Last 10 wake events")
            val fmt = remember { SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()) }
            if (events.isEmpty()) Hint("No wake events yet.")
            events.forEach { e ->
                Row(Modifier.fillMaxWidth()) {
                    Text(fmt.format(Date(e.timestamp)), color = JarvisColors.Text, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text("conf ${"%.2f".format(e.confidence)}", color = JarvisColors.Muted, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
        }

        Card("Other") {
            TextButton(onClick = onOpenBatteryGuide) { Text("Open battery guide again", color = JarvisColors.Saffron) }
            TextButton(onClick = { scope.launch { app.database.chatDao().clear() } }) {
                Text("Clear chat history", color = JarvisColors.Danger)
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(JarvisColors.Surface, RoundedCornerShape(16.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = JarvisColors.SoftGold, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        content()
    }
}

@Composable private fun Hint(text: String) = Text(text, color = JarvisColors.Muted, fontSize = 13.sp)
@Composable private fun Label(text: String) = Text(text, color = JarvisColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) = FilterChip(
    selected = selected, onClick = onClick, label = { Text(label) },
    colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = JarvisColors.Saffron, selectedLabelColor = JarvisColors.Charcoal,
        labelColor = JarvisColors.Text
    )
)

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = JarvisColors.Saffron, activeTrackColor = JarvisColors.Saffron, inactiveTrackColor = JarvisColors.Charcoal
)

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = JarvisColors.Saffron, unfocusedBorderColor = JarvisColors.Muted,
    focusedTextColor = JarvisColors.Text, unfocusedTextColor = JarvisColors.Text,
    focusedLabelColor = JarvisColors.SoftGold, unfocusedLabelColor = JarvisColors.Muted, cursorColor = JarvisColors.Saffron
)
