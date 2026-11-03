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
import com.jarvis.app.ui.MainScreen
import com.jarvis.app.ui.PermissionsScreen
import com.jarvis.app.ui.SettingsScreen

private enum class Screen { PERMISSIONS, BATTERY, MAIN, SETTINGS, LEADS }

class MainActivity : ComponentActivity() {
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
                        onOpenLeads = { screen = Screen.LEADS }
                    )
                    Screen.LEADS -> {
                        BackHandler { screen = Screen.MAIN }
                        LeadsScreen(onBack = { screen = Screen.MAIN })
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
