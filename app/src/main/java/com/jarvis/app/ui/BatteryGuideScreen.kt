package com.jarvis.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@SuppressLint("BatteryLife")
@Composable
fun BatteryGuideScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val pkg = Uri.parse("package:${context.packageName}")

    Column(
        Modifier
            .fillMaxSize()
            .background(JarvisColors.Charcoal)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Keep JARVIS awake", color = JarvisColors.Saffron, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Phones like Xiaomi, Realme, Vivo, Oppo and Samsung often kill apps that listen in the background. Do these once:",
            color = JarvisColors.Text, fontSize = 16.sp
        )
        GuideStep("1", "Battery: choose Unrestricted / Don't optimise for JARVIS.")
        OutlinedButton(
            onClick = {
                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
            },
            border = BorderStroke(1.dp, JarvisColors.Saffron),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open battery optimisation", color = JarvisColors.Saffron) }

        GuideStep("2", "Display over other apps: lets JARVIS pop up when you say \"Jarvis\" from another app.")
        OutlinedButton(
            onClick = { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)) },
            border = BorderStroke(1.dp, JarvisColors.Saffron),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Allow display over apps", color = JarvisColors.Saffron) }

        if (Build.VERSION.SDK_INT >= 34) {
            GuideStep("3", "Full-screen alerts: lets JARVIS wake the screen when locked.")
            OutlinedButton(
                onClick = { open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)) },
                border = BorderStroke(1.dp, JarvisColors.Saffron),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Allow full-screen alerts", color = JarvisColors.Saffron) }
        }
        GuideStep("•", "On Xiaomi/Redmi also turn on Autostart, and lock JARVIS in Recent apps (pull down on its card).")

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.Saffron, contentColor = JarvisColors.Charcoal)
        ) { Text("Done", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
private fun GuideStep(num: String, text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, JarvisColors.Surface, RoundedCornerShape(12.dp))
            .background(JarvisColors.Surface, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text("Step $num", color = JarvisColors.SoftGold, fontWeight = FontWeight.SemiBold)
        Text(text, color = JarvisColors.Text, fontSize = 15.sp)
    }
}
