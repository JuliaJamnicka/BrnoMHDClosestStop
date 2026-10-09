package io.github.juliajamnicka.fcil.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class TextShortenerTest {
    @Test
    fun keepsShortText() = assertEquals("Komárov", TextShortener.shorten("Komárov", 18))

    @Test
    fun usesCommonAbbreviationsFirst() {
        assertEquals("Starý Lískovec, sm.", TextShortener.shorten("Starý Lískovec, smyčka", 19))
        assertEquals("Mendlovo nám.", TextShortener.shorten("Mendlovo náměstí", 14))
    }

    @Test
    fun cutsWithEllipsisAsLastResort() {
        val s = TextShortener.shorten("Technologický park Medlánky", 18)
        assertEquals(18, s.length)
        assertEquals('…', s.last())
    }

    @Test
    fun normalisesNonBreakingSpaces() = assertEquals("Ústřední hřbitov - smyčka", TextShortener.shorten("Ústřední hřbitov - smyčka", 40))
}
