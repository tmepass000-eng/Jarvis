package com.jarvis.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.settings.SettingsRepository

@Composable
fun SettingsScreen(settings: SettingsRepository, onBack: () -> Unit, onOpenBatteryGuide: () -> Unit) {
    val sensitivity by settings.sensitivity.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .background(JarvisColors.Charcoal)
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = JarvisColors.SoftGold)
            }
            Text("Settings", color = JarvisColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .background(JarvisColors.Surface, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Text("Wake word sensitivity", color = JarvisColors.SoftGold, fontWeight = FontWeight.SemiBold)
            Text(
                "Lower = fewer false triggers. Higher = catches quieter voices.",
                color = JarvisColors.Muted, fontSize = 13.sp
            )
            Slider(
                value = sensitivity,
                onValueChange = { settings.setSensitivity(it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = JarvisColors.Saffron,
                    activeTrackColor = JarvisColors.Saffron,
                    inactiveTrackColor = JarvisColors.Charcoal
                )
            )
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
        }

        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onOpenBatteryGuide) {
            Text("Open battery guide again", color = JarvisColors.Saffron)
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Gemini key, voice and daily limits arrive in later phases.",
            color = JarvisColors.Muted, fontSize = 12.sp
        )
    }
}
