package io.github.juliajamnicka.brnomhd.widget

import io.github.juliajamnicka.brnomhd.data.DeparturesResponse
import io.github.juliajamnicka.brnomhd.widget.WidgetLogic.Fetch
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLogicTest {
    private fun shown(group: String, platform: String, opp: String?) =
        DeparturesResponse(t = 1, g = group, stop = "Česká", p = platform, dir = "x", opp = opp)

    @Test
    fun followsTheHomeStopByDefault() {
        assertEquals(Fetch.Home, WidgetLogic.firstFetch(WidgetConfig()))
        assertEquals(Fetch.Platform("U1Z2"), WidgetLogic.firstFetch(WidgetConfig(pinnedPlatform = "U1Z2")))
        assertEquals(Fetch.List("home"), WidgetLogic.firstFetch(WidgetConfig(pinnedList = "home")))
    }

    @Test
    fun reverseOnTheHomeStopShowsTheOppositePlatformUntilPressedAgain() {
        val home = shown("G1", "U1Z1", opp = "U1Z2")
        val reversed = WidgetLogic.reverse(WidgetConfig(), home)
        assertEquals(WidgetConfig(reversedGroup = "G1"), reversed)
        assertEquals(reversed to "U1Z2", WidgetLogic.afterHome(reversed, home))
        // pressing reverse again (while the opposite platform of G1 is shown) goes back
        assertEquals(WidgetConfig(), WidgetLogic.reverse(reversed, shown("G1", "U1Z2", opp = "U1Z1")))
    }

    @Test
    fun reverseIsForgottenWhenTheHomeStopChanges() {
        val reversed = WidgetConfig(reversedGroup = "G1")
        assertEquals(WidgetConfig() to null, WidgetLogic.afterHome(reversed, shown("G2", "U2Z1", opp = "U2Z2")))
    }

    @Test
    fun reverseOnAPinnedPlatformMovesThePin() {
        val pinned = WidgetConfig(pinnedPlatform = "U1Z1")
        assertEquals(WidgetConfig(pinnedPlatform = "U1Z2"), WidgetLogic.reverse(pinned, shown("G1", "U1Z1", opp = "U1Z2")))
    }

    @Test
    fun reverseWithoutAnOppositePlatformDoesNothing() {
        assertEquals(WidgetConfig(), WidgetLogic.reverse(WidgetConfig(), shown("G1", "U1Z1", opp = null)))
        assertEquals(WidgetConfig(), WidgetLogic.reverse(WidgetConfig(), null))
    }
}
