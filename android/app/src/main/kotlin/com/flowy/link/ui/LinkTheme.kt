package com.flowy.link.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Attention = Color(0xFF4F6F8D)
val AttentionPressed = Color(0xFF3E5A74)
val Danger = Color(0xFF8C3A3A)
val Ink = Color(0xFF1C1C1C)
val Meta = Color(0xFF5C5C5C)
val Line = Color(0xFFE6E6E6)
val Paper = Color(0xFFF2F2F2)

private val Type = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Normal, color = Ink),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 22.sp, color = Ink),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp, color = Meta),
)

@Composable
fun LinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Attention,
            onPrimary = Color.White,
            primaryContainer = AttentionPressed,
            background = Color.White,
            surface = Color.White,
        ),
        typography = Type,
        content = content,
    )
}
