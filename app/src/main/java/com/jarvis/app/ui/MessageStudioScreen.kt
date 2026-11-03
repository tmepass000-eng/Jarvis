package com.jarvis.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.JarvisApp
import com.jarvis.app.core.BrainException
import com.jarvis.app.data.Lead
import com.jarvis.app.data.LeadStatus
import com.jarvis.app.data.Outreach
import com.jarvis.app.outreach.Drafter
import com.jarvis.app.outreach.FollowUps
import com.jarvis.app.outreach.Offer
import com.jarvis.app.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DAILY_SAFE = 25
private const val DAY = 24L * 60 * 60 * 1000

@Composable
fun MessageStudioScreen(leadId: Long, onBack: () -> Unit, onPitch: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as JarvisApp
    val db = app.database
    val drafter = remember { Drafter(SettingsRepository.get(context)) }
    val scope = rememberCoroutineScope()
    val lead by db.leadDao().observe(leadId).collectAsStateWithLifecycle(initialValue = null)

    var offer by remember { mutableStateOf(Offer.SINGLE) }
    var full by remember { mutableStateOf("") }
    var shown by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var followDays by remember { mutableIntStateOf(3) }
    var warnCount by remember { mutableStateOf<Int?>(null) }
    var sampleTitle by remember { mutableStateOf<String?>(null) }
    val typing = shown < full.length

    fun generate(l: Lead) {
        busy = true; error = ""
        scope.launch {
            try {
                val prev = text.takeIf { it.isNotBlank() }
                val d = drafter.draft(l, offer, prev, db.portfolioDao().list())
                sampleTitle = d.sample?.title
                full = d.text; shown = 0; text = ""
            } catch (e: BrainException) { error = e.userMessage }
            busy = false
        }
    }

    LaunchedEffect(full) {
        while (shown < full.length) { delay(14); shown++ }
        text = full
    }

    fun doSend(l: Lead) = scope.launch {
        val msg = text.trim()
        copy(context, msg)
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.profileUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "Couldn't open the profile. Message is copied.", Toast.LENGTH_LONG).show() }
        val now = System.currentTimeMillis()
        val follow = if (followDays > 0) now + followDays * DAY else null
        val newStatus = if (l.status in listOf(LeadStatus.NEW, LeadStatus.FOUND)) LeadStatus.CONTACTED else l.status
        db.leadDao().update(l.copy(status = newStatus, contactedAt = now, followUpAt = follow))
        db.outreachDao().insert(Outreach(leadId = l.id, platform = l.platform))
        if (follow != null) FollowUps.schedule(context, l.id, follow) else FollowUps.cancel(context, l.id)
        Toast.makeText(context, "Copied. Paste it in the chat and tap send.", Toast.LENGTH_LONG).show()
    }

    Column(
        Modifier.fillMaxSize().background(JarvisColors.Charcoal).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = JarvisColors.SoftGold) }
            Text("MESSAGE STUDIO", color = JarvisColors.Saffron, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }
        val l = lead ?: run { Text("Loading…", color = JarvisColors.Muted); return@Column }
        Text(l.name, color = JarvisColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${l.platform} · Score ${l.score} · ${l.status}", color = JarvisColors.SoftGold, fontSize = 13.sp)
        Text(l.reason, color = JarvisColors.Muted, fontSize = 13.sp)
        TextButton(onClick = { onPitch(l.id) }) { Text("Open Pitch Room", color = JarvisColors.SoftGold) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Offer.entries.forEach { o ->
                FilterChip(selected = offer == o, onClick = { offer = o }, label = { Text(o.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = JarvisColors.Saffron,
                        selectedLabelColor = JarvisColors.Charcoal, labelColor = JarvisColors.Text))
            }
        }

        if (full.isEmpty() || typing) {
            Text(
                if (full.isEmpty()) (if (busy) "Jarvis is writing…" else "Tap Write draft.") else full.take(shown) + "▌",
                color = JarvisColors.Text, fontFamily = FontFamily.Monospace, fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)
                    .background(JarvisColors.Surface, RoundedCornerShape(16.dp)).padding(16.dp)
            )
        } else {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = JarvisColors.Text),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JarvisColors.Saffron,
                    unfocusedBorderColor = JarvisColors.Surface, cursorColor = JarvisColors.Saffron)
            )
            Text("${text.split(Regex("\\s+")).count { it.isNotBlank() }} words · edit freely", color = JarvisColors.Muted, fontSize = 12.sp)
        }
        if (sampleTitle != null && !typing) Text("Attached sample: $sampleTitle", color = JarvisColors.SoftGold, fontSize = 12.sp)
        if (error.isNotBlank()) Text(error, color = JarvisColors.Danger, fontSize = 13.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { generate(l) }, enabled = !busy && !typing) {
                Text(if (full.isEmpty()) "Write draft" else "Regenerate", color = JarvisColors.Saffron)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    scope.launch {
                        val sent = db.outreachDao().countSince(l.platform, startOfDay())
                        if (sent >= DAILY_SAFE) warnCount = sent else doSend(l)
                    }
                },
                enabled = text.isNotBlank() && !typing,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.Saffron, contentColor = JarvisColors.Charcoal)
            ) { Text("Send (copy + open)") }
        }

        Text("Follow-up reminder", color = JarvisColors.Text, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "1 day", 3 to "3 days", 7 to "1 week", 0 to "None").forEach { (d, label) ->
                FilterChip(selected = followDays == d, onClick = { followDays = d }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = JarvisColors.SoftGold,
                        selectedLabelColor = JarvisColors.Charcoal, labelColor = JarvisColors.Text))
            }
        }
        Text("JARVIS never sends for you. Send copies the message and opens their profile; you paste and tap send.",
            color = JarvisColors.Muted, fontSize = 12.sp)
    }

    warnCount?.let { n ->
        AlertDialog(
            onDismissRequest = { warnCount = null },
            containerColor = JarvisColors.Surface,
            title = { Text("Easy there", color = JarvisColors.Text) },
            text = { Text("You've already sent $n messages on ${lead?.platform} today. Going past $DAILY_SAFE a day can get your account limited. Send anyway?", color = JarvisColors.Text) },
            confirmButton = { TextButton(onClick = { warnCount = null; lead?.let { doSend(it) } }) { Text("Send anyway", color = JarvisColors.Saffron) } },
            dismissButton = { TextButton(onClick = { warnCount = null }) { Text("Wait till tomorrow", color = JarvisColors.Muted) } }
        )
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("JARVIS message", text))
}

internal fun startOfDay(): Long = java.util.Calendar.getInstance().apply {
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis
