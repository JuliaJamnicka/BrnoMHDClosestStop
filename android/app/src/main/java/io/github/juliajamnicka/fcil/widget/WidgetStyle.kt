package io.github.juliajamnicka.fcil.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import kotlin.math.pow
import kotlin.math.roundToInt

/** How one widget looks, chosen in WidgetStyleActivity. Colours are opaque ARGB ints. */
data class WidgetStyle(
    /** Follow the wallpaper colours (Android 12+) instead of [background] and [accent]. */
    val system: Boolean = false,
    val background: Int = DEFAULT_BACKGROUND,
    val accent: Int = DEFAULT_ACCENT,
    /** Background opacity in percent; text, badges and buttons stay opaque. */
    val opacity: Int = 100,
    val text: TextMode = TextMode.AUTO,
) {
    enum class TextMode { AUTO, LIGHT, DARK }

    companion object {
        const val DEFAULT_BACKGROUND = 0xFF0E1B33.toInt() // Fčil night
        const val DEFAULT_ACCENT = 0xFF3D7BD9.toInt() // Fčil ring blue

        val BACKGROUNDS = listOf(0x0E1B33, 0x000000, 0x26292F, 0x3B1F2B, 0x1E3A2F, 0x1A4FA3, 0xF3F4F7, 0xFFFFFF).map(::opaque)
        val ACCENTS = listOf(0x3D7BD9, 0x6EA0EE, 0xF0373E, 0xFFB020, 0x22C55E, 0xA78BFA, 0xFFFFFF, 0x0E1B33).map(::opaque)
    }
}

/** Wallpaper-derived colours for [WidgetStyle.system]. */
data class SystemColors(val background: Int, val accent: Int)

/** The colours the widget draws with, all ARGB. */
data class WidgetPalette(
    val background: Int,
    val text: Int,
    val secondary: Int,
    val muted: Int,
    val accent: Int,
    val button: Int,
    /** Delays and the pin; darker on light backgrounds so it stays readable. */
    val warning: Int,
)

object WidgetColors {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val NIGHT = 0xFF0E1B33.toInt()

    fun palette(style: WidgetStyle, system: SystemColors?): WidgetPalette {
        val fromSystem = if (style.system) system else null
        val background = fromSystem?.background ?: style.background
        val light = when (style.text) {
            WidgetStyle.TextMode.LIGHT -> true
            WidgetStyle.TextMode.DARK -> false
            WidgetStyle.TextMode.AUTO -> luminance(background) < 0.4
        }
        val text = if (light) WHITE else NIGHT
        return WidgetPalette(
            background = withAlpha(background, style.opacity.coerceIn(0, 100) / 100f),
            text = text,
            secondary = withAlpha(text, 0.78f),
            muted = withAlpha(text, 0.6f),
            accent = opaque(fromSystem?.accent ?: style.accent),
            button = withAlpha(text, 0.14f),
            warning = if (light) 0xFFFFB020.toInt() else 0xFFB86E00.toInt(),
        )
    }

    /** Relative luminance (WCAG), 0 for black to 1 for white. */
    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val v = ((argb shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** "1a4fa3" or "#1A4FA3" to an opaque colour; null unless exactly six hex digits. */
    fun parseHex(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (hex.length != 6 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return opaque(hex.toInt(16))
    }

    fun hex(argb: Int): String = "%06X".format(argb and 0xFFFFFF)

    fun withAlpha(argb: Int, alpha: Float): Int = ((alpha * 255).roundToInt() shl 24) or (argb and 0xFFFFFF)
}

private fun opaque(rgb: Int): Int = rgb or (0xFF shl 24)

/** Material You colours from the wallpaper (Android 12+), light or dark like the system. */
fun systemColors(context: Context): SystemColors? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    return SystemColors(
        background = context.getColor(if (night) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50),
        accent = context.getColor(if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600),
    )
}
