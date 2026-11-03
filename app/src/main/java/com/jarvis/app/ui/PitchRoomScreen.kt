package com.jarvis.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.JarvisApp
import com.jarvis.app.core.BrainException
import com.jarvis.app.data.Lead
import com.jarvis.app.outreach.Pitch
import com.jarvis.app.outreach.PitchWriter
import com.jarvis.app.settings.SettingsRepository
import kotlinx.coroutines.launch

private val Paper = Color(0xFFF5F0E8)
private val Ink = Color(0xFF2B2620)
private val Faded = Color(0xFF7A6F62)

/** The one light, paper-style screen: a dossier to read before a call. */
@Composable
fun PitchRoomScreen(leadId: Long, onBack: () -> Unit, onWrite: (Long) -> Unit) {
    val context = LocalContext.current
    val dao = (context.applicationContext as JarvisApp).database.leadDao()
    val writer = remember { PitchWriter(SettingsRepository.get(context)) }
    val scope = rememberCoroutineScope()
    var lead by remember { mutableStateOf<Lead?>(null) }
    var pitch by remember { mutableStateOf<Pitch?>(null) }
    var price by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun generate(l: Lead) {
        busy = true; error = ""
        scope.launch {
            try {
                val p = writer.write(l)
                pitch = p; price = p.price
                val updated = l.copy(pitch = p.toJson()); dao.update(updated); lead = updated
            } catch (e: BrainException) { error = e.userMessage }
            busy = false
        }
    }

    fun savePrice() {
        val l = lead ?: return; val p = pitch ?: return
        val np = p.copy(price = price)
        scope.launch { val u = l.copy(pitch = np.toJson()); dao.update(u); lead = u; pitch = np }
    }

    LaunchedEffect(leadId) {
        val l = dao.byId(leadId) ?: return@LaunchedEffect
        lead = l
        val cached = Pitch.parse(l.pitch)
        if (cached != null) { pitch = cached; price = cached.price } else generate(l)
    }

    Column(
        Modifier.fillMaxSize().background(Paper).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { savePrice(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
            Text("PITCH ROOM", color = Faded, fontSize = 13.sp, letterSpacing = 4.sp, fontFamily = FontFamily.Serif)
        }
        val l = lead
        Text(l?.name ?: "…", color = Ink, fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        if (l != null) Text("${l.platform} · ${l.niche.ifBlank { "—" }} · Score ${l.score}/100",
            color = Faded, fontSize = 13.sp, fontFamily = FontFamily.Serif)
        HorizontalDivider(color = Ink.copy(alpha = 0.3f))

        val p = pitch
        when {
            p != null -> {
                Section("The business", p.summary)
                Section("What to offer", p.offer)
                Text("PRICE RANGE", color = Faded, fontSize = 11.sp, letterSpacing = 2.sp)
                OutlinedTextField(
                    value = price, onValueChange = { price = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Serif, fontSize = 18.sp, color = Ink),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Ink, unfocusedBorderColor = Ink.copy(alpha = 0.25f), cursorColor = Ink)
                )
                Text("TALKING POINTS", color = Faded, fontSize = 11.sp, letterSpacing = 2.sp)
                p.points.forEachIndexed { i, t ->
                    Text("${i + 1}.  $t", color = Ink, fontSize = 16.sp, fontFamily = FontFamily.Serif, lineHeight = 22.sp)
                }
            }
            busy -> Text("Preparing your dossier…", color = Faded, fontFamily = FontFamily.Serif, fontSize = 16.sp)
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFB3261E), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick = { lead?.let { generate(it) } }, enabled = !busy && lead != null) {
                Text(if (pitch == null) "Generate" else "Regenerate", color = Ink)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { savePrice(); onWrite(leadId) }) { Text("Write message →", color = Ink, fontWeight = FontWeight.Bold) }
        }
        Text("Prices come from your Portfolio pricing notes. Edit the range freely; it's saved here.",
            color = Faded, fontSize = 12.sp)
    }
}

@Composable
private fun Section(title: String, body: String) {
    Text(title.uppercase(), color = Faded, fontSize = 11.sp, letterSpacing = 2.sp)
    Text(body, color = Ink, fontSize = 16.sp, fontFamily = FontFamily.Serif, lineHeight = 22.sp)
}
