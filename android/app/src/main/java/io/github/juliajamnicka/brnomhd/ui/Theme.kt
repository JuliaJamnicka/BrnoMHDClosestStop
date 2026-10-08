package io.github.juliajamnicka.brnomhd.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colours from the design canvas (phone settings artboard) and the watch mode badges.
val Teal = Color(0xFF0F5E54)
val TramRed = Color(0xFFC8262C)
val BusGreen = Color(0xFF1F7A4D)
val TrainBlue = Color(0xFF1D5FD1)
val DelayAmber = Color(0xFFB86E00)

private val Light = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    background = Color(0xFFF4F4F1),
    surface = Color.White,
    onSurface = Color(0xFF161616),
    onSurfaceVariant = Color(0xFF5C5C58),
    surfaceVariant = Color(0xFFECECE8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF00201A),
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFFAEAEB2),
    surfaceVariant = Color(0xFF2C2C2E),
)

@Composable
fun MhdTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

fun modeColor(mode: String): Color = when (mode) {
    "T" -> TramRed
    "V" -> TrainBlue
    else -> BusGreen
}
