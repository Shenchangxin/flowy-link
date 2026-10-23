package com.flowy.link.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Attention = Color(0xFF4F6F8D)
private val AttentionPressed = Color(0xFF3E5A74)

@Composable
fun LinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Attention,
            onPrimary = Color.White,
            primaryContainer = AttentionPressed,
        ),
        content = content,
    )
}
