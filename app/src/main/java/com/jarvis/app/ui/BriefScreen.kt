package com.jarvis.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.JarvisApp
import com.jarvis.app.brief.DailyBrief
import com.jarvis.app.data.Lead
import com.jarvis.app.settings.SettingsRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Morning timeline: follow-ups due, new leads, one content idea. */
@Composable
fun BriefScreen(onBack: () -> Unit, onPipeline: () -> Unit, onLeads: () -> Unit, onWrite: (Long) -> Unit) {
    val context = LocalContext.current
    val dao = (context.applicationContext as JarvisApp).database.leadDao()
    val settings = remember { SettingsRepository.get(context) }
    val scope = rememberCoroutineScope()
    var due by remember { mutableStateOf<List<Lead>>(emptyList()) }
    var fresh by remember { mutableStateOf<List<Lead>>(emptyList()) }
    var idea by remember { mutableStateOf(settings.briefIdea) }
    var ideaBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        due = dao.followUpsDue(DailyBrief.endOfToday())
        fresh = dao.newSince(System.currentTimeMillis() - 24 * 3_600_000L)
        if (idea.isBlank()) { ideaBusy = true; idea = DailyBrief.newIdea(context); ideaBusy = false }
    }
    val today = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

    Column(
        Modifier.fillMaxSize().background(JarvisColors.Charcoal).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = JarvisColors.SoftGold) }
            Column {
                Text("DAILY BRIEF", color = JarvisColors.Saffron, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Text(today, color = JarvisColors.Muted, fontSize = 13.sp)
            }
        }
        Column(Modifier.padding(top = 16.dp)) {
            Step("Follow-ups due", if (due.isEmpty()) "Nothing due. Nice." else "${due.size} to nudge today",
                JarvisColors.Danger.takeIf { due.isNotEmpty() } ?: JarvisColors.SoftGold) {
                due.take(8).forEach { l ->
                    Text("• ${l.name}  ·  ${l.status}", color = JarvisColors.Text, fontSize = 14.sp,
                        modifier = Modifier.clickable { onWrite(l.id) }.padding(vertical = 4.dp))
                }
                if (due.isNotEmpty()) TextButton(onClick = onPipeline) { Text("Open pipeline", color = JarvisColors.Saffron) }
            }
            Step("New leads", if (fresh.isEmpty()) "None in the last 24 hours" else "${fresh.size} waiting to be swiped", JarvisColors.Saffron) {
                fresh.take(5).forEach { l ->
                    Text("• ${l.name}  ·  ${l.score}", color = JarvisColors.Text, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
                TextButton(onClick = onLeads) { Text(if (fresh.isEmpty()) "Find leads" else "Swipe them", color = JarvisColors.Saffron) }
            }
            Step("Content idea for your Reels", "", JarvisColors.SoftGold, last = true) {
                Text(if (ideaBusy) "Thinking…" else idea, color = JarvisColors.Text, fontSize = 15.sp, lineHeight = 21.sp,
                    modifier = Modifier.fillMaxWidth().background(JarvisColors.Surface, RoundedCornerShape(12.dp)).padding(12.dp))
                TextButton(onClick = {
                    ideaBusy = true
                    scope.launch { idea = DailyBrief.newIdea(context); ideaBusy = false }
                }, enabled = !ideaBusy) { Text("Another idea", color = JarvisColors.SoftGold) }
            }
        }
    }
}

@Composable
private fun Step(title: String, subtitle: String, dot: Color, last: Boolean = false, content: @Composable () -> Unit) {
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            Box(Modifier.size(12.dp).background(dot, CircleShape))
            if (!last) Box(Modifier.width(2.dp).size(width = 2.dp, height = 120.dp).background(JarvisColors.Surface))
        }
        Column(Modifier.padding(start = 10.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = JarvisColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, color = JarvisColors.Muted, fontSize = 13.sp)
            content()
        }
    }
}
