package com.spendobserver

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.spendobserver.data.settings.ThemeMode
import com.spendobserver.ui.navigation.SpendObserverNavHost
import com.spendobserver.ui.theme.SpendObserverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SpendObserverApplication).container
        setContent {
            val themeMode by container.themePreference.mode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // The default style follows the system setting: a forced theme would get unreadable bar icons.
            LaunchedEffect(darkTheme) {
                val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            SpendObserverTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                SpendObserverNavHost(
                    navController = navController,
                    trackerRepository = container.trackerRepository,
                    recordRepository = container.recordRepository,
                    tagRepository = container.tagRepository,
                    backupRepository = container.backupRepository,
                    themeMode = themeMode,
                    onThemeModeChange = container.themePreference::set,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
