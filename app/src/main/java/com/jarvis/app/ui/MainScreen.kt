package com.jarvis.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.JarvisApp
import com.jarvis.app.core.JarvisState
import com.jarvis.app.core.StateHolder
import com.jarvis.app.data.ChatTurn
import com.jarvis.app.service.JarvisService

@Composable
fun MainScreen(onOpenSettings: () -> Unit, onOpenLeads: () -> Unit, onOpenPipeline: () -> Unit, onOpenBrief: () -> Unit, onOpenPortfolio: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as JarvisApp
    val conversation = app.conversation
    val state by StateHolder.state.collectAsStateWithLifecycle()
    val running by StateHolder.serviceRunning.collectAsStateWithLifecycle()
    val status by StateHolder.status.collectAsStateWithLifecycle()
    val level by StateHolder.level.collectAsStateWithLifecycle()
    val partial by StateHolder.partial.collectAsStateWithLifecycle()
    val turns by app.database.chatDao().recent().collectAsStateWithLifecycle(initialValue = emptyList())
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(turns.size) { if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1) }

    Column(
        Modifier
            .fillMaxSize()
            .background(JarvisColors.Charcoal)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("JARVIS", color = JarvisColors.Saffron, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = running,
                onCheckedChange = { on -> if (on) JarvisService.start(context) else JarvisService.stop(context) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = JarvisColors.Charcoal,
                    checkedTrackColor = JarvisColors.Saffron,
                    uncheckedThumbColor = JarvisColors.Muted,
                    uncheckedTrackColor = JarvisColors.Charcoal
                )
            )
            IconButton(onClick = onOpenLeads) {
                Icon(Icons.Filled.Radar, contentDescription = "Leads", tint = JarvisColors.SoftGold)
            }
            IconButton(onClick = onOpenBrief) {
                Icon(Icons.Filled.WbSunny, contentDescription = "Daily Brief", tint = JarvisColors.SoftGold)
            }
            IconButton(onClick = onOpenPortfolio) {
                Icon(Icons.Filled.VideoLibrary, contentDescription = "Portfolio", tint = JarvisColors.SoftGold)
            }
            IconButton(onClick = onOpenPipeline) {
                Icon(Icons.Filled.ViewKanban, contentDescription = "Pipeline", tint = JarvisColors.SoftGold)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = JarvisColors.SoftGold)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clickable { if (state == JarvisState.IDLE) conversation.startListening() else conversation.stopAll() },
            contentAlignment = Alignment.Center
        ) {
            VoiceOrb(state, level, running, Modifier.size(220.dp))
        }
        Text(
            when {
                partial.isNotBlank() -> "“$partial”"
                state == JarvisState.IDLE -> if (running) "Say \"Jarvis\" or tap the orb" else status
                else -> state.name.lowercase().replaceFirstChar { it.uppercase() } + "…"
            },
            color = JarvisColors.Muted, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp)
        )
        if (state == JarvisState.IDLE && running && status.isNotBlank() && !status.startsWith("Listening for")) {
            Text(status, color = JarvisColors.Danger, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (turns.isEmpty()) {
                item {
                    Text(
                        "Ask anything by voice, or type below. Say \"details\" after an answer to hear more.",
                        color = JarvisColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            items(turns, key = { it.id }) { ChatBubble(it) }
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Type a message", color = JarvisColors.Muted) },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisColors.Saffron,
                    unfocusedBorderColor = JarvisColors.Surface,
                    focusedTextColor = JarvisColors.Text,
                    unfocusedTextColor = JarvisColors.Text,
                    cursorColor = JarvisColors.Saffron
                )
            )
            Spacer(Modifier.size(6.dp))
            val busy = state != JarvisState.IDLE
            IconButton(
                onClick = {
                    when {
                        input.isNotBlank() -> { conversation.submitText(input); input = "" }
                        busy -> conversation.stopAll()
                        else -> conversation.startListening()
                    }
                },
                modifier = Modifier.size(52.dp).background(JarvisColors.Saffron, CircleShape)
            ) {
                Icon(
                    when {
                        input.isNotBlank() -> Icons.AutoMirrored.Filled.Send
                        busy -> Icons.Filled.Stop
                        else -> Icons.Filled.Mic
                    },
                    contentDescription = "Send or speak", tint = JarvisColors.Charcoal
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(turn: ChatTurn) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (turn.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (turn.fromUser) JarvisColors.Saffron.copy(alpha = 0.18f) else JarvisColors.Surface,
                    RoundedCornerShape(16.dp)
                )
                .clickable(enabled = turn.detail.isNotBlank()) { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(turn.text, color = if (turn.isError) JarvisColors.Danger else JarvisColors.Text, fontSize = 15.sp)
            if (turn.detail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                if (expanded) Text(turn.detail, color = JarvisColors.Muted, fontSize = 14.sp)
                Text(if (expanded) "Hide details" else "Show details", color = JarvisColors.SoftGold, fontSize = 12.sp)
            }
        }
    }
}
