package com.celllogger.lite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5E4B),
    secondary = Color(0xFF4A6360),
    tertiary = Color(0xFF00639B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78D8BD),
    secondary = Color(0xFFB2CCC7),
    tertiary = Color(0xFF8BCBFF)
)

@Composable
fun CellLoggerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
