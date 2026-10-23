package com.flowy.link

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flowy.link.ui.DisconnectedScreen
import com.flowy.link.ui.HomeScreen
import com.flowy.link.ui.LinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinkTheme {
                var session by remember { mutableStateOf(loadSession(this)) }
                val current = session
                if (current == null) {
                    DisconnectedScreen(onConnected = { session = loadSession(this) })
                } else {
                    HomeScreen(current, onDisconnect = { session = null })
                }
            }
        }
    }
}
