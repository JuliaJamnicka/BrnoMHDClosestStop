package io.github.juliajamnicka.fcil.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetColorsTest {
    private val white = 0xFFFFFFFF.toInt()
    private val night = 0xFF0E1B33.toInt()

    @Test
    fun defaultLooksLikeTheOriginalWidget() {
        val p = WidgetColors.palette(WidgetStyle(), system = null)
        assertEquals(night, p.background)
        assertEquals(white, p.text)
        assertEquals(0xFF3D7BD9.toInt(), p.accent)
        assertEquals(0xFFFFB020.toInt(), p.warning)
    }

    @Test
    fun picksDarkTextOnLightBackgroundsUnlessOverridden() {
        val light = WidgetStyle(background = white)
        assertEquals(night, WidgetColors.palette(light, null).text)
        assertEquals(0xFFB86E00.toInt(), WidgetColors.palette(light, null).warning)
        assertEquals(white, WidgetColors.palette(light.copy(text = WidgetStyle.TextMode.LIGHT), null).text)
        assertEquals(night, WidgetColors.palette(WidgetStyle(text = WidgetStyle.TextMode.DARK), null).text)
    }

    @Test
    fun opacityOnlyAffectsTheBackground() {
        val p = WidgetColors.palette(WidgetStyle(opacity = 50), null)
        assertEquals(0x800E1B33.toInt(), p.background)
        assertEquals(white, p.text)
        assertEquals(0x00, WidgetColors.palette(WidgetStyle(opacity = 0), null).background ushr 24)
    }

    @Test
    fun systemColoursWinOnlyWhenChosenAndAvailable() {
        val system = SystemColors(background = 0xFFF8EEF2.toInt(), accent = 0xFF8B4A62.toInt())
        val on = WidgetColors.palette(WidgetStyle(system = true), system)
        assertEquals(0xFFF8EEF2.toInt(), on.background)
        assertEquals(0xFF8B4A62.toInt(), on.accent)
        assertEquals(night, on.text)
        assertEquals(night, WidgetColors.palette(WidgetStyle(system = true), null).background) // before Android 12
        assertEquals(night, WidgetColors.palette(WidgetStyle(), system).background)
    }

    @Test
    fun parsesAndFormatsHex() {
        assertEquals(0xFF1A4FA3.toInt(), WidgetColors.parseHex("#1a4fa3"))
        assertEquals(0xFF1A4FA3.toInt(), WidgetColors.parseHex("1A4FA3"))
        assertNull(WidgetColors.parseHex("1A4FA"))
        assertNull(WidgetColors.parseHex("GGGGGG"))
        assertEquals("0E1B33", WidgetColors.hex(night))
    }
}
