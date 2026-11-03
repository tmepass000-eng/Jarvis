package com.jarvis.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.JarvisApp
import com.jarvis.app.core.BrainException
import com.jarvis.app.data.Lead
import com.jarvis.app.data.LeadStatus
import com.jarvis.app.settings.SettingsRepository
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class LeadTab(val label: String) { FIND("Find"), RADAR("Radar"), CARDS("Cards") }

@Composable
fun LeadsScreen(onBack: () -> Unit, onWrite: (Long) -> Unit, onPitch: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as JarvisApp
    val dao = app.database.leadDao()
    val settings = SettingsRepository.get(context)
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(LeadTab.FIND) }
    val active by dao.active().collectAsStateWithLifecycle(initialValue = emptyList())
    val unswiped by dao.unswiped().collectAsStateWithLifecycle(initialValue = emptyList())
    val today by dao.countSince(startOfToday()).collectAsStateWithLifecycle(initialValue = 0)
    val target by settings.leadTarget.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<Lead?>(null) }

    fun setStatus(l: Lead, status: String, star: Boolean = l.starred) =
        scope.launch { dao.update(l.copy(status = status, starred = star)) }

    Column(
        Modifier.fillMaxSize().background(JarvisColors.Charcoal).systemBarsPadding().imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = JarvisColors.SoftGold) }
            Text("LEADS", color = JarvisColors.Saffron, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Spacer(Modifier.weight(1f))
            Text("Today $today / $target", color = if (today >= target) JarvisColors.SoftGold else JarvisColors.Muted, fontSize = 13.sp)
        }
        LinearProgressIndicator(
            progress = { (today.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            color = JarvisColors.Saffron, trackColor = JarvisColors.Surface
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LeadTab.entries.forEach { t ->
                val sel = t == tab
                Text(
                    t.label + if (t == LeadTab.CARDS && unswiped.isNotEmpty()) " (${unswiped.size})" else "",
                    color = if (sel) JarvisColors.Charcoal else JarvisColors.Text,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                        .background(if (sel) JarvisColors.Saffron else JarvisColors.Surface, RoundedCornerShape(12.dp))
                        .clickable { tab = t }.padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        when (tab) {
            LeadTab.FIND -> FindTab(onDone = { tab = LeadTab.CARDS })
            LeadTab.RADAR -> RadarTab(active) { open = it }
            LeadTab.CARDS -> CardsTab(
                unswiped,
                onKeep = { setStatus(it, LeadStatus.FOUND) },
                onSkip = { setStatus(it, LeadStatus.SKIPPED) },
                onStar = { setStatus(it, LeadStatus.FOUND, star = true) },
                onOpen = { open = it }
            )
        }
    }

    open?.let { l ->
        AlertDialog(
            onDismissRequest = { open = null },
            containerColor = JarvisColors.Surface,
            title = { Text((if (l.starred) "★ " else "") + l.name, color = JarvisColors.Text) },
            text = {
                Column {
                    Text("${l.platform} · Score ${l.score} · ${l.status}", color = JarvisColors.SoftGold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(l.reason, color = JarvisColors.Text)
                    Spacer(Modifier.height(8.dp))
                    Text(l.profileUrl, color = JarvisColors.Muted, fontSize = 12.sp)
                    Text("${l.niche} · ${l.region}", color = JarvisColors.Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { open = null; onWrite(l.id) }) { Text("Write message", color = JarvisColors.Saffron) }
                    TextButton(onClick = { open = null; onPitch(l.id) }) { Text("Pitch Room", color = JarvisColors.SoftGold) }
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.profileUrl))) }
                    }) { Text("Open profile", color = JarvisColors.SoftGold) }
                }
            },
            dismissButton = {
                Row {
                    if (l.status != LeadStatus.FOUND) TextButton(onClick = { setStatus(l, LeadStatus.FOUND); open = null }) { Text("Keep", color = JarvisColors.SoftGold) }
                    TextButton(onClick = { setStatus(l, l.status.takeIf { it != LeadStatus.NEW } ?: LeadStatus.FOUND, !l.starred); open = null }) {
                        Text(if (l.starred) "Unstar" else "Star", color = JarvisColors.SoftGold)
                    }
                    TextButton(onClick = { setStatus(l, LeadStatus.SKIPPED); open = null }) { Text("Skip", color = JarvisColors.Muted) }
                }
            }
        )
    }
}

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun FindTab(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as JarvisApp
    val scope = rememberCoroutineScope()
    var niche by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("India") }
    var pasted by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun run(block: suspend () -> com.jarvis.app.leads.FindResult) {
        busy = true; message = "Searching and scoring…"
        scope.launch {
            message = try { block().also { if (it.added > 0) onDone() }.message } catch (e: BrainException) { e.userMessage }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Say \"Jarvis, find online skincare brands in India\", or type below.", color = JarvisColors.Muted, fontSize = 13.sp)
        Field(niche, { niche = it }, "Niche (e.g. online skincare brands)")
        Field(region, { region = it }, "Region (e.g. India, Dubai, USA)")
        Button(
            onClick = { run { app.leadFinder.find(niche, region) } },
            enabled = !busy && niche.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.Saffron, contentColor = JarvisColors.Charcoal),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Working…" else "Find leads") }
        if (message.isNotBlank()) Text(message, color = JarvisColors.SoftGold)
        Spacer(Modifier.height(8.dp))
        Text("Or paste profile links (one per line)", color = JarvisColors.Text, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = pasted, onValueChange = { pasted = it },
            placeholder = { Text("instagram.com/brand1\nlinkedin.com/company/brand2", color = JarvisColors.Muted) },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            colors = fieldColors()
        )
        Button(
            onClick = { run { app.leadFinder.scorePasted(pasted, niche, region).also { if (it.added > 0) pasted = "" } } },
            enabled = !busy && pasted.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.Surface, contentColor = JarvisColors.Saffron),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Score pasted links") }
        Text("Search uses Google through your Gemini key (free, ~500 a day). Add a Tavily key in Settings for a second free source.",
            color = JarvisColors.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Field(v: String, on: (String) -> Unit, hint: String) = OutlinedTextField(
    value = v, onValueChange = on, singleLine = true,
    placeholder = { Text(hint, color = JarvisColors.Muted) },
    modifier = Modifier.fillMaxWidth(), colors = fieldColors()
)

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = JarvisColors.Saffron, unfocusedBorderColor = JarvisColors.Surface,
    focusedTextColor = JarvisColors.Text, unfocusedTextColor = JarvisColors.Text, cursorColor = JarvisColors.Saffron
)

@Composable
private fun RadarTab(leads: List<Lead>, onTap: (Lead) -> Unit) {
    val sweep by rememberInfiniteTransition(label = "radar").animateFloat(
        0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "sweep"
    )
    val shown = leads.take(40)
    val blipR = with(LocalDensity.current) { 7.dp.toPx() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(shown) {
                    detectTapGestures { tap ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = size.width / 2f * 0.92f
                        shown.minByOrNull { (blipPos(it, c, r) - tap).getDistance() }
                            ?.takeIf { (blipPos(it, c, r) - tap).getDistance() < blipR * 4 }?.let(onTap)
                    }
                }
            ) {
                val c = center
                val r = size.minDimension / 2f * 0.92f
                for (i in 1..4) drawCircle(JarvisColors.Saffron.copy(alpha = 0.18f), r * i / 4f, c, style = Stroke(1.5f))
                drawLine(JarvisColors.Saffron.copy(alpha = 0.12f), Offset(c.x - r, c.y), Offset(c.x + r, c.y))
                drawLine(JarvisColors.Saffron.copy(alpha = 0.12f), Offset(c.x, c.y - r), Offset(c.x, c.y + r))
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, JarvisColors.Saffron.copy(alpha = 0.35f)), c),
                    startAngle = sweep - 60f, sweepAngle = 60f, useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                )
                shown.forEach { l ->
                    val p = blipPos(l, c, r)
                    val ang = angleOf(l)
                    val since = ((sweep - ang) % 360f + 360f) % 360f
                    val glow = 1f - (since / 360f)
                    val col = if (l.starred) JarvisColors.SoftGold else JarvisColors.Saffron
                    drawCircle(col.copy(alpha = 0.15f + 0.35f * glow), blipR * (1.6f + glow), p)
                    drawCircle(col.copy(alpha = 0.6f + 0.4f * glow), blipR * (if (l.status == LeadStatus.FOUND) 1f else 0.7f), p)
                }
            }
        }
        Text(
            if (shown.isEmpty()) "No leads yet. Use Find first." else "Closer to the centre = better match. Tap a blip.",
            color = JarvisColors.Muted, fontSize = 13.sp
        )
    }
}

private fun angleOf(l: Lead): Float = ((l.profileUrl.hashCode() and 0x7fffffff) % 360).toFloat()

private fun blipPos(l: Lead, c: Offset, r: Float): Offset {
    val dist = r * (0.08f + 0.9f * (1f - l.score / 100f))
    val a = Math.toRadians(angleOf(l).toDouble())
    return Offset(c.x + (dist * cos(a)).toFloat(), c.y + (dist * sin(a)).toFloat())
}

@Composable
private fun CardsTab(
    leads: List<Lead>,
    onKeep: (Lead) -> Unit, onSkip: (Lead) -> Unit, onStar: (Lead) -> Unit, onOpen: (Lead) -> Unit
) {
    if (leads.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No new leads to swipe. Use Find to get more.", color = JarvisColors.Muted)
        }
        return
    }
    val scope = rememberCoroutineScope()
    val top = leads.first()
    val dx = remember(top.id) { Animatable(0f) }
    val dy = remember(top.id) { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }

    fun fling(action: (Lead) -> Unit, tx: Float, ty: Float) = scope.launch {
        launch { dx.animateTo(tx, tween(180)) }
        dy.animateTo(ty, tween(180))
        action(top)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(380.dp), contentAlignment = Alignment.Center) {
            leads.getOrNull(1)?.let { LeadCard(it, Modifier.graphicsLayer { scaleX = 0.94f; scaleY = 0.94f; alpha = 0.6f }) }
            LeadCard(
                top,
                Modifier
                    .offset { IntOffset(dx.value.roundToInt(), dy.value.roundToInt()) }
                    .graphicsLayer { rotationZ = dx.value / 25f }
                    .pointerInput(top.id) {
                        detectDragGestures(
                            onDragEnd = {
                                when {
                                    dx.value > threshold -> fling(onKeep, 1500f, dy.value)
                                    dx.value < -threshold -> fling(onSkip, -1500f, dy.value)
                                    dy.value < -threshold && abs(dx.value) < threshold -> fling(onStar, dx.value, -1800f)
                                    else -> scope.launch { launch { dx.animateTo(0f) }; dy.animateTo(0f) }
                                }
                            }
                        ) { change, drag ->
                            change.consume()
                            scope.launch { dx.snapTo(dx.value + drag.x); dy.snapTo(dy.value + drag.y) }
                        }
                    }
                    .clickable { onOpen(top) },
                hint = when {
                    dx.value > threshold / 2 -> "KEEP"
                    dx.value < -threshold / 2 -> "SKIP"
                    dy.value < -threshold / 2 -> "★ STAR"
                    else -> ""
                }
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { fling(onSkip, -1500f, 0f) }) { Text("✕ Skip", color = JarvisColors.Muted, fontSize = 16.sp) }
            TextButton(onClick = { fling(onStar, 0f, -1800f) }) { Text("★ Star", color = JarvisColors.SoftGold, fontSize = 16.sp) }
            TextButton(onClick = { fling(onKeep, 1500f, 0f) }) { Text("✓ Keep", color = JarvisColors.Saffron, fontSize = 16.sp) }
        }
        Text("Right = keep · Left = skip · Up = star", color = JarvisColors.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun LeadCard(l: Lead, modifier: Modifier = Modifier, hint: String = "") {
    Column(
        modifier.fillMaxWidth(0.92f).height(340.dp)
            .background(JarvisColors.Surface, RoundedCornerShape(24.dp))
            .border(1.dp, JarvisColors.Saffron.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(l.platform.uppercase(), color = JarvisColors.Muted, fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            if (hint.isNotEmpty()) Text(hint, color = JarvisColors.SoftGold, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
        Text(l.name, color = JarvisColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Spacer(Modifier.height(14.dp))
        Text("${l.score}", color = JarvisColors.Saffron, fontSize = 54.sp, fontWeight = FontWeight.Black)
        Text("match score", color = JarvisColors.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Text("What they lack", color = JarvisColors.SoftGold, fontSize = 12.sp)
        Text(l.reason, color = JarvisColors.Text, fontSize = 15.sp, maxLines = 4)
        Spacer(Modifier.weight(1f))
        Text("${l.niche} · ${l.region}", color = JarvisColors.Muted, fontSize = 12.sp, maxLines = 1)
    }
}
