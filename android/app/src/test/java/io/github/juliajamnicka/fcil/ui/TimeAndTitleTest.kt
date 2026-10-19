package io.github.juliajamnicka.fcil.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeAndTitleTest {
    @Test
    fun countsMinutesOrShowsTheClock() {
        assertEquals("teď", departureTimeLabel(1010, 1000, clock = false, nowWord = "teď"))
        // 30-59 s used to read "0 min"
        assertEquals("teď", departureTimeLabel(1045, 1000, clock = false, nowWord = "teď"))
        assertEquals("1 min", departureTimeLabel(1060, 1000, clock = false, nowWord = "teď"))
        assertEquals("6 min", departureTimeLabel(1400, 1000, clock = false, nowWord = "teď"))
        // the clock setting shows the time even for a departure that is leaving now
        assertTrue(departureTimeLabel(1010, 1000, clock = true, nowWord = "teď").matches(Regex("""\d{1,2}[:.]\d\d.*""")))
    }

    @Test
    fun coloursOnlyTheAccentsAboveTheLetters() {
        assertEquals(0.581f, accentCut('á'))
        assertEquals(0.546f, accentCut('í')) // its accent sits lower
        assertEquals(0.811f, accentCut('Č')) // above the cap height
        assertNull(accentCut('a'))
        assertNull(accentCut('ť')) // háček beside the ascender: stays blue
    }
}
