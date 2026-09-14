package com.spendobserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.spendobserver.ui.theme.SpendObserverTheme
import com.spendobserver.ui.trackers.TrackerListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SpendObserverApplication).container
        setContent {
            SpendObserverTheme {
                TrackerListScreen(
                    trackerRepository = container.trackerRepository,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}