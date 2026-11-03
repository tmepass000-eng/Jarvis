package com.jarvis.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.JarvisApp
import com.jarvis.app.data.Lead
import com.jarvis.app.data.LeadStatus
import com.jarvis.app.outreach.FollowUps
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val DAY_MS = 24L * 60 * 60 * 1000

@Composable
fun PipelineScreen(onBack: () -> Unit, onWrite: (Long) -> Unit, onPitch: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as JarvisApp
    val dao = app.database.leadDao()
    val scope = rememberCoroutineScope()
    val leads by dao.pipeline().collectAsStateWithLifecycle(initialValue = emptyList())

    val columns = remember { mutableStateMapOf<String, Rect>() }
    var dragged by remember { mutableStateOf<Lead?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var open by remember { mutableStateOf<Lead?>(null) }

    fun move(l: Lead, status: String) = scope.launch {
        if (l.status == status) return@launch
        val updated = l.copy(status = status)
        dao.update(updated)
        if (status == LeadStatus.WON || status == LeadStatus.LOST) FollowUps.cancel(context, l.id)
    }

    Box(Modifier.fillMaxSize().background(JarvisColors.Charcoal).onGloballyPositioned { rootOrigin = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = JarvisColors.SoftGold) }
                Text("PIPELINE", color = JarvisColors.Saffron, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Spacer(Modifier.weight(1f))
                val due = leads.count { (it.followUpAt ?: Long.MAX_VALUE) <= System.currentTimeMillis() && it.status !in listOf(LeadStatus.WON, LeadStatus.LOST) }
                if (due > 0) Text("$due follow-up${if (due > 1) "s" else ""} due", color = JarvisColors.Danger, fontSize = 13.sp)
            }
            Text("Long-press a card and drag it to another column. Tap a card for notes.",
                color = JarvisColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Row(
                Modifier.fillMaxSize().horizontalScroll(rememberScrollState(), enabled = dragged == null).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LeadStatus.PIPELINE.forEach { status ->
                    val colLeads = leads.filter { it.status == status }
                    val hot = dragged != null && columns[status]?.contains(dragPos) == true
                    Column(
                        Modifier.width(230.dp).fillMaxHeight()
                            .background(if (hot) JarvisColors.Saffron.copy(alpha = 0.12f) else JarvisColors.Surface.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .onGloballyPositioned { columns[status] = it.boundsInRoot() }
                            .padding(8.dp)
                    ) {
                        Text("$status  ${colLeads.size}", color = JarvisColors.SoftGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(colLeads, key = { it.id }) { l ->
                                var cardOrigin by remember { mutableStateOf(Offset.Zero) }
                                PipelineCard(
                                    l,
                                    Modifier
                                        .onGloballyPositioned { cardOrigin = it.positionInRoot() }
                                        .graphicsLayer { alpha = if (dragged?.id == l.id) 0.3f else 1f }
                                        .pointerInput(l.id, l.status) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { off -> dragged = l; dragPos = cardOrigin + off },
                                                onDragEnd = {
                                                    val target = columns.entries.firstOrNull { it.value.contains(dragPos) }?.key
                                                    if (target != null) move(l, target)
                                                    dragged = null
                                                },
                                                onDragCancel = { dragged = null }
                                            ) { change, amount -> change.consume(); dragPos += amount }
                                        }
                                        .clickable { open = l }
                                )
                            }
                        }
                    }
                }
            }
        }
        dragged?.let { l ->
            PipelineCard(l, Modifier.width(210.dp)
                .offset { IntOffset((dragPos.x - rootOrigin.x - 100).roundToInt(), (dragPos.y - rootOrigin.y - 40).roundToInt()) }
                .graphicsLayer { rotationZ = -3f; shadowElevation = 16f })
        }
    }

    open?.let { start ->
        val l = leads.firstOrNull { it.id == start.id } ?: start
        var notes by remember(l.id) { mutableStateOf(l.notes) }
        AlertDialog(
            onDismissRequest = { scope.launch { dao.update(l.copy(notes = notes)) }; open = null },
            containerColor = JarvisColors.Surface,
            title = { Text((if (l.starred) "★ " else "") + l.name, color = JarvisColors.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${l.platform} · Score ${l.score}", color = JarvisColors.SoftGold, fontSize = 13.sp)
                    Text(l.reason, color = JarvisColors.Muted, fontSize = 13.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LeadStatus.PIPELINE.forEach { s ->
                            FilterChip(selected = l.status == s, onClick = { move(l, s) }, label = { Text(s, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = JarvisColors.Saffron,
                                    selectedLabelColor = JarvisColors.Charcoal, labelColor = JarvisColors.Text))
                        }
                    }
                    Text("Follow-up: " + (l.followUpAt?.let { fmt(it) } ?: "none"), color = JarvisColors.Text, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to "+1d", 3 to "+3d", 7 to "+1w", 0 to "Clear").forEach { (d, label) ->
                            TextButton(onClick = {
                                scope.launch {
                                    val at = if (d > 0) System.currentTimeMillis() + d * DAY_MS else null
                                    dao.update(l.copy(followUpAt = at, notes = notes))
                                    if (at != null) FollowUps.schedule(context, l.id, at) else FollowUps.cancel(context, l.id)
                                }
                            }) { Text(label, color = JarvisColors.SoftGold) }
                        }
                    }
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it }, label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JarvisColors.Saffron,
                            focusedTextColor = JarvisColors.Text, unfocusedTextColor = JarvisColors.Text)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { dao.update(l.copy(notes = notes)) }; open = null; onWrite(l.id) }) {
                    Text("Write message", color = JarvisColors.Saffron)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.profileUrl))) } }) {
                        Text("Profile", color = JarvisColors.SoftGold)
                    }
                    TextButton(onClick = { scope.launch { dao.update(l.copy(notes = notes)) }; open = null; onPitch(l.id) }) {
                        Text("Pitch", color = JarvisColors.SoftGold)
                    }
                    TextButton(onClick = { scope.launch { dao.update(l.copy(notes = notes)) }; open = null }) {
                        Text("Save", color = JarvisColors.Muted)
                    }
                }
            }
        )
    }
}

private fun fmt(t: Long) = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(t))

@Composable
private fun PipelineCard(l: Lead, modifier: Modifier = Modifier) {
    val now = System.currentTimeMillis()
    val due = l.followUpAt?.let { it <= now } == true && l.status !in listOf(LeadStatus.WON, LeadStatus.LOST)
    Column(
        modifier.fillMaxWidth()
            .background(JarvisColors.Charcoal, RoundedCornerShape(12.dp))
            .border(1.dp, if (due) JarvisColors.Danger else JarvisColors.Saffron.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (l.starred) "★ " else "") + l.name, color = JarvisColors.Text, fontWeight = FontWeight.SemiBold,
                maxLines = 1, modifier = Modifier.weight(1f))
            Text("${l.score}", color = JarvisColors.Saffron, fontWeight = FontWeight.Black)
        }
        Text(l.platform, color = JarvisColors.Muted, fontSize = 11.sp)
        l.followUpAt?.let {
            Text(if (due) "Follow up now" else "Follow up ${fmt(it)}",
                color = if (due) JarvisColors.Danger else JarvisColors.SoftGold, fontSize = 11.sp)
        }
        if (l.notes.isNotBlank()) Text(l.notes, color = JarvisColors.Muted, fontSize = 11.sp, maxLines = 2)
    }
}
