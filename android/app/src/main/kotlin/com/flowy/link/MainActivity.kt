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
                var session by remember { mutableStateOf(loadSession(this@MainActivity)) }
                var adding by remember { mutableStateOf(false) }
                val current = session
                if (current == null || adding) {
                    DisconnectedScreen(
                        onConnected = {
                            adding = false
                            session = loadSession(this@MainActivity)
                        },
                        onCancel = if (current != null) {
                            { adding = false }
                        } else {
                            null
                        },
                    )
                } else {
                    HomeScreen(
                        current,
                        onDisconnect = { session = loadSession(this@MainActivity) },
                        onAddComputer = { adding = true },
                        onSwitch = { session = it },
                    )
                }
            }
        }
    }
}
