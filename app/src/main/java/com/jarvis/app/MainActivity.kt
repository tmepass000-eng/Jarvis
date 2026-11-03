package com.jarvis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jarvis.app.settings.SettingsRepository
import com.jarvis.app.ui.BatteryGuideScreen
import com.jarvis.app.ui.JarvisTheme
import com.jarvis.app.ui.LeadsScreen
import com.jarvis.app.ui.MessageStudioScreen
import com.jarvis.app.ui.PipelineScreen
import com.jarvis.app.ui.PitchRoomScreen
import com.jarvis.app.ui.BriefScreen
import com.jarvis.app.ui.PortfolioScreen
import com.jarvis.app.ui.MainScreen
import com.jarvis.app.ui.PermissionsScreen
import com.jarvis.app.ui.SettingsScreen

private enum class Screen { PERMISSIONS, BATTERY, MAIN, SETTINGS, LEADS, PIPELINE, STUDIO, PITCH, BRIEF, PORTFOLIO }

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_OPEN = "open" }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra(EXTRA_OPEN) != null) { setIntent(intent); recreate() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository.get(this)
        setContent {
            JarvisTheme {
                var screen by remember {
                    mutableStateOf(
                        when {
                            !settings.onboardingDone -> Screen.PERMISSIONS
                            !settings.batteryGuideShown -> Screen.BATTERY
                            else -> Screen.MAIN
                        }
                    )
                }
                var studioLead by remember { mutableStateOf(0L) }
                var studioReturn by remember { mutableStateOf(Screen.MAIN) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val target = intent?.getStringExtra(EXTRA_OPEN)
                    if (target != null && settings.onboardingDone) {
                        intent.removeExtra(EXTRA_OPEN)
                        screen = if (target == "brief") Screen.BRIEF else Screen.PIPELINE
                    }
                }
                fun write(id: Long, from: Screen) { studioLead = id; studioReturn = from; screen = Screen.STUDIO }
                var pitchReturn by remember { mutableStateOf(Screen.MAIN) }
                fun pitch(id: Long, from: Screen) { studioLead = id; pitchReturn = from; screen = Screen.PITCH }
                when (screen) {
                    Screen.PERMISSIONS -> PermissionsScreen(onDone = {
                        settings.onboardingDone = true
                        screen = if (settings.batteryGuideShown) Screen.MAIN else Screen.BATTERY
                    })
                    Screen.BATTERY -> BatteryGuideScreen(onDone = {
                        settings.batteryGuideShown = true
                        screen = Screen.MAIN
                    })
                    Screen.MAIN -> MainScreen(
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onOpenLeads = { screen = Screen.LEADS },
                        onOpenPipeline = { screen = Screen.PIPELINE },
                        onOpenBrief = { screen = Screen.BRIEF },
                        onOpenPortfolio = { screen = Screen.PORTFOLIO }
                    )
                    Screen.LEADS -> {
                        BackHandler { screen = Screen.MAIN }
                        LeadsScreen(onBack = { screen = Screen.MAIN }, onWrite = { write(it, Screen.LEADS) }, onPitch = { pitch(it, Screen.LEADS) })
                    }
                    Screen.PIPELINE -> {
                        BackHandler { screen = Screen.MAIN }
                        PipelineScreen(onBack = { screen = Screen.MAIN }, onWrite = { write(it, Screen.PIPELINE) }, onPitch = { pitch(it, Screen.PIPELINE) })
                    }
                    Screen.STUDIO -> {
                        BackHandler { screen = studioReturn }
                        MessageStudioScreen(leadId = studioLead, onBack = { screen = studioReturn }, onPitch = { pitch(it, Screen.STUDIO) })
                    }
                    Screen.PITCH -> {
                        BackHandler { screen = pitchReturn }
                        PitchRoomScreen(leadId = studioLead, onBack = { screen = pitchReturn },
                            onWrite = { write(it, if (pitchReturn == Screen.STUDIO) studioReturn else pitchReturn) })
                    }
                    Screen.BRIEF -> {
                        BackHandler { screen = Screen.MAIN }
                        BriefScreen(onBack = { screen = Screen.MAIN }, onPipeline = { screen = Screen.PIPELINE },
                            onLeads = { screen = Screen.LEADS }, onWrite = { write(it, Screen.BRIEF) })
                    }
                    Screen.PORTFOLIO -> {
                        BackHandler { screen = Screen.MAIN }
                        PortfolioScreen(onBack = { screen = Screen.MAIN })
                    }
                    Screen.SETTINGS -> {
                        BackHandler { screen = Screen.MAIN }
                        SettingsScreen(
                            settings = settings,
                            onBack = { screen = Screen.MAIN },
                            onOpenBatteryGuide = { screen = Screen.BATTERY }
                        )
                    }
                }
            }
        }
    }
}
