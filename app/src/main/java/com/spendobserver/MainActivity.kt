package com.spendobserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.spendobserver.ui.navigation.SpendObserverNavHost
import com.spendobserver.ui.theme.SpendObserverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SpendObserverApplication).container
        setContent {
            SpendObserverTheme {
                val navController = rememberNavController()
                SpendObserverNavHost(
                    navController = navController,
                    trackerRepository = container.trackerRepository,
                    recordRepository = container.recordRepository,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}