package io.github.juliajamnicka.brnomhd.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Fčil brand colours (docs/brand/README.md) and the mode badges shared with the watch.
val BrandBlue = Color(0xFF1A4FA3)
val BrandRed = Color(0xFFE3242B)
val BrandNight = Color(0xFF0E1B33)
val RingBlue = Color(0xFF3D7BD9)
val SkyBlue = Color(0xFF6EA0EE)
val BrightRed = Color(0xFFF0373E)
val TramRed = Color(0xFFC8262C)
val BusGreen = Color(0xFF1F7A4D)
val TrainBlue = Color(0xFF1D5FD1)
val DelayAmber = Color(0xFFB86E00)
val DelayAmberDark = Color(0xFFFFB020)

private val Light = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandRed,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6F7),
    onSecondaryContainer = BrandNight,
    background = Color(0xFFF3F4F7),
    surface = Color.White,
    onSurface = BrandNight,
    onSurfaceVariant = Color(0xFF4A5468),
    surfaceVariant = Color(0xFFE6E9EF),
    surfaceContainer = Color.White,
)

private val Dark = darkColorScheme(
    primary = SkyBlue,
    onPrimary = BrandNight,
    secondary = BrightRed,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF24365C),
    onSecondaryContainer = Color.White,
    background = Color(0xFF070D1A),
    surface = Color(0xFF13203B),
    onSurface = Color(0xFFF2F4F8),
    onSurfaceVariant = Color(0xFFAEB7C8),
    surfaceVariant = Color(0xFF22304D),
    surfaceContainer = BrandNight,
)

@Composable
fun MhdTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** The red of "now" (the háček): brand red on light, the brighter red on dark. */
val nowColor: Color
    @Composable get() = MaterialTheme.colorScheme.secondary

val delayColor: Color
    @Composable get() = if (isSystemInDarkTheme()) DelayAmberDark else DelayAmber

fun modeColor(mode: String): Color = when (mode) {
    "T" -> TramRed
    "V" -> TrainBlue
    else -> BusGreen
}
