package com.flowy.link

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.flowy.link.ui.DisconnectedScreen
import com.flowy.link.ui.LinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinkTheme {
                DisconnectedScreen()
            }
        }
    }
}
