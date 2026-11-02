package com.jarvis.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private data class PermItem(val title: String, val why: String)

@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val runtimePerms = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (micGranted()) onDone() else denied = true
    }

    val items = listOf(
        PermItem("Microphone", "To hear the word \"Jarvis\". Audio stays on your phone, nothing is recorded or uploaded."),
        PermItem("Notifications", "Shows a small notification while JARVIS listens, with a Stop button."),
        PermItem("Foreground service", "Keeps listening when the screen is off. Android requires this for always-on mic apps.")
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(JarvisColors.Charcoal)
            .systemBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("JARVIS", color = JarvisColors.Saffron, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 8.sp)
        Text("Before we start, JARVIS needs a few permissions.", color = JarvisColors.Text, fontSize = 18.sp)
        items.forEachIndexed { i, item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "${i + 1}",
                    color = JarvisColors.Charcoal,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .size(28.dp)
                        .background(JarvisColors.Saffron, CircleShape)
                        .padding(top = 3.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text(item.title, color = JarvisColors.SoftGold, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(item.why, color = JarvisColors.Muted, fontSize = 14.sp)
                }
            }
        }
        if (denied) {
            Text(
                "Microphone is required for the wake word. You can allow it in Android Settings › Apps › JARVIS › Permissions.",
                color = JarvisColors.Danger, fontSize = 14.sp
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { if (micGranted() && runtimePerms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) onDone() else launcher.launch(runtimePerms.toTypedArray()) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.Saffron, contentColor = JarvisColors.Charcoal)
        ) { Text("Allow and continue", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}
