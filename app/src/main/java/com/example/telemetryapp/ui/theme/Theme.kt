package com.example.telemetryapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary   = Color(0xFF82B1FF),
    secondary = Color(0xFF80CBC4),
    tertiary  = Color(0xFFCFD8DC)
)

private val LightColorScheme = lightColorScheme(
    primary   = Color(0xFF1565C0),
    secondary = Color(0xFF00796B),
    tertiary  = Color(0xFF455A64)
)

@Composable
fun IoTEdgeGatewayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content  : @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}