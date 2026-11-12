package com.shoptracker.pos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object LocalThemePreference {
    var dark by androidx.compose.runtime.mutableStateOf(false)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201C),
    secondaryContainer = Color(0xFFDCEBE8),
    onSecondaryContainer = Color(0xFF0B1F1C),
    surface = Color(0xFFFAFDFC),
    background = Color(0xFFFAFDFC),
    surfaceVariant = Color(0xFFE6F0EE)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF004D43),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondaryContainer = Color(0xFF2C3E3B),
    onSecondaryContainer = Color(0xFFDCEBE8),
    surface = Color(0xFF101414),
    background = Color(0xFF101414),
    surfaceVariant = Color(0xFF1E2625)
)

@Composable
fun ShopTheme(content: @Composable () -> Unit) {
    val dark = LocalThemePreference.dark
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

@Composable
fun positiveColor(): Color = if (LocalThemePreference.dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)

@Composable
fun negativeColor(): Color = if (LocalThemePreference.dark) Color(0xFFEF5350) else Color(0xFFC62828)

@Composable
fun neutralColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/** Green for improvement, red for decline, neutral for no change. */
@Composable
fun deltaColor(delta: Long): Color = when {
    delta > 0 -> positiveColor()
    delta < 0 -> negativeColor()
    else -> neutralColor()
}
