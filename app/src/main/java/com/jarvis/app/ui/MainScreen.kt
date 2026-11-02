package com.jarvis.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.JarvisApp
import com.jarvis.app.core.JarvisState
import com.jarvis.app.core.StateHolder
import com.jarvis.app.service.JarvisService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val state by StateHolder.state.collectAsStateWithLifecycle()
    val running by StateHolder.serviceRunning.collectAsStateWithLifecycle()
    val status by StateHolder.status.collectAsStateWithLifecycle()
    val dao = remember { (context.applicationContext as JarvisApp).database.wakeEventDao() }
    val events by dao.lastTen().collectAsStateWithLifecycle(initialValue = emptyList())
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(JarvisColors.Charcoal)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("JARVIS", color = JarvisColors.Saffron, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = JarvisColors.SoftGold)
            }
        }

        Spacer(Modifier.height(24.dp))
        StateRing(state = state, active = running)
        Spacer(Modifier.height(12.dp))
        Text(
            status,
            color = if (status.startsWith("Could") || status.startsWith("Microphone") || status.startsWith("Listening stopped"))
                JarvisColors.Danger else JarvisColors.Muted,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(JarvisColors.Surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (running) "Listening is ON" else "Listening is OFF", color = JarvisColors.Text, fontWeight = FontWeight.SemiBold)
                Text("Say \"Jarvis\" from anywhere", color = JarvisColors.Muted, fontSize = 13.sp)
            }
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
        }

        Spacer(Modifier.height(22.dp))
        Text("LAST 10 WAKE EVENTS", color = JarvisColors.SoftGold, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (events.isEmpty()) {
            Text("No wake events yet. Turn listening on and say \"Jarvis\".", color = JarvisColors.Muted, fontSize = 14.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(events, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("▸ ", color = JarvisColors.Saffron, fontFamily = FontFamily.Monospace)
                    Text(fmt.format(Date(e.timestamp)), color = JarvisColors.Text, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text("conf ${"%.2f".format(e.confidence)}", color = JarvisColors.Muted, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Big state readout inside a saffron ring that breathes while listening. */
@Composable
private fun StateRing(state: JarvisState, active: Boolean) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.92f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale"
    )
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(230.dp)) {
            val base = size.minDimension / 2.2f
            val r = if (state == JarvisState.LISTENING) base * pulse else base
            drawCircle(
                color = if (active) JarvisColors.Saffron else JarvisColors.Surface,
                radius = r,
                center = Offset(size.width / 2, size.height / 2),
                style = Stroke(width = if (state == JarvisState.LISTENING) 10f else 4f)
            )
            if (state == JarvisState.LISTENING) {
                drawCircle(JarvisColors.SoftGold.copy(alpha = 0.15f), radius = r * 0.85f)
            }
        }
        Text(
            state.name,
            color = if (state == JarvisState.LISTENING) JarvisColors.SoftGold else JarvisColors.Text,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )
    }
}
