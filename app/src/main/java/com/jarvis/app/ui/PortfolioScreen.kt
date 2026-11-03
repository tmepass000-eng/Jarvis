package com.jarvis.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.JarvisApp
import com.jarvis.app.data.PortfolioItem
import com.jarvis.app.settings.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun PortfolioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = (context.applicationContext as JarvisApp).database.portfolioDao()
    val settings = remember { SettingsRepository.get(context) }
    val scope = rememberCoroutineScope()
    val items by dao.all().collectAsStateWithLifecycle(initialValue = emptyList())
    val single by settings.priceSingle.collectAsStateWithLifecycle()
    val monthly by settings.priceMonthly.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val linkOk = link.trim().startsWith("http")
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JarvisColors.Saffron, unfocusedBorderColor = JarvisColors.Surface,
        focusedTextColor = JarvisColors.Text, unfocusedTextColor = JarvisColors.Text, cursorColor = JarvisColors.Saffron)

    Column(
        Modifier.fillMaxSize().background(JarvisColors.Charcoal).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = JarvisColors.SoftGold) }
            Text("PORTFOLIO", color = JarvisColors.Saffron, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        }
        Text("Add 3 to 5 of your best AI ads. Jarvis attaches the best match to each message.",
            color = JarvisColors.Muted, fontSize = 13.sp)

        items.forEachIndexed { i, p ->
            Row(
                Modifier.fillMaxWidth().background(JarvisColors.Surface, RoundedCornerShape(14.dp))
                    .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.link))) } }
                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${i + 1}. ${p.title}", color = JarvisColors.Text, fontWeight = FontWeight.SemiBold)
                    if (p.description.isNotBlank()) Text(p.description, color = JarvisColors.Muted, fontSize = 13.sp)
                    Text(p.link, color = JarvisColors.SoftGold, fontSize = 11.sp, maxLines = 1)
                }
                IconButton(onClick = { scope.launch { dao.delete(p) } }) {
                    Icon(Icons.Filled.Delete, "Delete", tint = JarvisColors.Muted)
                }
            }
        }
        if (items.isEmpty()) Text("No samples yet.", color = JarvisColors.Muted)

        Text("Add a sample", color = JarvisColors.Text, fontWeight = FontWeight.Bold)
        OutlinedTextField(title, { title = it }, label = { Text("Title (e.g. Skincare launch ad)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = colors)
        OutlinedTextField(link, { link = it }, label = { Text("Link (Instagram, YouTube, Drive)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), colors = colors, isError = link.isNotBlank() && !linkOk)
        OutlinedTextField(desc, { desc = it }, label = { Text("One line: niche and style") },
            modifier = Modifier.fillMaxWidth(), colors = colors)
        Button(
            onClick = {
                scope.launch {
                    dao.insert(PortfolioItem(title = title.trim(), link = link.trim(), description = desc.trim()))
                    title = ""; link = ""; desc = ""
                }
            },
            enabled = title.isNotBlank() && linkOk,
            colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.Saffron, contentColor = JarvisColors.Charcoal)
        ) { Text("Add") }
        if (items.size >= 5) Text("5 is plenty. Keep only your strongest.", color = JarvisColors.SoftGold, fontSize = 12.sp)

        Text("Pricing notes", color = JarvisColors.Text, fontWeight = FontWeight.Bold)
        Text("Used by the Pitch Room for price ranges. Never put into messages.", color = JarvisColors.Muted, fontSize = 12.sp)
        OutlinedTextField(single, { settings.setPriceSingle(it) }, label = { Text("Single ad (e.g. ₹1,500–3,000)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = colors)
        OutlinedTextField(monthly, { settings.setPriceMonthly(it) }, label = { Text("Monthly package (e.g. ₹10,000 for 8 ads)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = colors)
    }
}
