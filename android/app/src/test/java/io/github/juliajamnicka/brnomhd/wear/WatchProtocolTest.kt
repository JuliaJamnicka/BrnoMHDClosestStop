package io.github.juliajamnicka.brnomhd.wear

import io.github.juliajamnicka.brnomhd.data.DepartureDto
import io.github.juliajamnicka.brnomhd.data.DeparturesResponse
import io.github.juliajamnicka.brnomhd.data.PlatformDto
import io.github.juliajamnicka.brnomhd.data.VehicleDto
import io.github.juliajamnicka.brnomhd.data.VehiclesResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProtocolTest {
    private val ceska = DeparturesResponse(
        t = 1791489562, g = "U1073N2860", stop = "Česká", p = "U1073Z1",
        dir = "Náměstí Míru, Starý Lískovec, smyčka", opp = "U1073Z2", d = 20,
        pl = listOf(PlatformDto("U1073Z1", "Náměstí Míru", "3 4 5 6 H4"), PlatformDto("U1073Z2", "Babická", "3 4 5 6 10")),
        dep = listOf(
            DepartureDto("6", "T", "Starý Lískovec, smyčka", 1791489660, 1791489660, 0, 1),
            DepartureDto("4", "T", "Náměstí Míru", 1791490020, 1791489900, 150, 1),
        ),
    )

    @Test
    fun parsesWatchRequests() {
        assertEquals(WatchProtocol.Request.Home, WatchProtocol.parseRequest("""{"c":"home"}""".toByteArray()))
        assertEquals(WatchProtocol.Request.Departures("U1073Z2"), WatchProtocol.parseRequest("""{"c":"dep","p":"U1073Z2"}""".toByteArray()))
        assertEquals(WatchProtocol.Request.Platforms("U1073Z2"), WatchProtocol.parseRequest("""{"c":"pl","p":"U1073Z2"}""".toByteArray()))
        assertEquals(WatchProtocol.Request.Nearby, WatchProtocol.parseRequest("""{"c":"near"}""".toByteArray()))
        assertEquals(WatchProtocol.Request.Vehicles, WatchProtocol.parseRequest("""{"c":"veh"}""".toByteArray()))
        assertNull(WatchProtocol.parseRequest("""{"c":"dep"}""".toByteArray()))
        assertNull(WatchProtocol.parseRequest("not json".toByteArray()))
    }

    @Test
    fun encodesDeparturesCompactly() {
        val obj = Json.parseToJsonElement(WatchProtocol.departures(ceska, "cs").decodeToString()).jsonObject
        assertEquals("dep", obj["c"]!!.jsonPrimitive.content)
        assertEquals("cs", obj["lg"]!!.jsonPrimitive.content)
        assertEquals("U1073Z2", obj["o"]!!.jsonPrimitive.content)
        val rows = obj["x"]!!.jsonArray
        assertEquals("""["6","T","Starý Lískovec, sm.",1791489660,0,1]""", rows[0].toString())
        // delay is sent in whole minutes
        assertEquals("2", rows[1].jsonArray[4].jsonPrimitive.content)
    }

    @Test
    fun sendsAStopListBoardWithStopNamesInsteadOfHeadsigns() {
        val board = DeparturesResponse(
            t = 1, g = "", stop = "Domů z práce", p = "", dir = "", listId = "home",
            dep = listOf(DepartureDto("12", "T", "Komárov", 100, 100, 0, 1, p = "U1Z2", sn = "Česká")),
        )
        val obj = Json.parseToJsonElement(WatchProtocol.departures(board, "cs").decodeToString()).jsonObject
        assertEquals("1", obj["l"]!!.jsonPrimitive.content)
        assertNull(obj["o"])
        assertEquals("""["12","T","Česká",100,0,1]""", obj["x"]!!.jsonArray[0].toString())
        assertNull(Json.parseToJsonElement(WatchProtocol.departures(ceska, "cs").decodeToString()).jsonObject["l"])
    }

    @Test
    fun dropsEntriesToStayUnderTheSizeLimit() {
        val many = VehiclesResponse(t = 1, v = List(200) { VehicleDto("N${it}", "B", it, -it, 90, 0, 3) })
        val bytes = WatchProtocol.vehicles(many, "en")
        assertTrue(bytes.size <= WatchProtocol.MAX_BYTES)
        val kept = Json.parseToJsonElement(bytes.decodeToString()).jsonObject["x"]!!.jsonArray.size
        assertTrue(kept in 10 until 200)
    }

    @Test
    fun encodesPlatformsForThePicker() {
        val obj = Json.parseToJsonElement(WatchProtocol.platforms(ceska, "en").decodeToString()).jsonObject
        assertEquals("""["U1073Z2","3 4 5 6 10","Babická"]""", obj["x"]!!.jsonArray[1].toString())
    }

    @Test
    fun sendsOnlyAsciiSoDiacriticsSurviveTheWatchSdk() {
        val text = WatchProtocol.departures(ceska, "cs").decodeToString()
        assertTrue(text.all { it.code < 0x80 })
        assertTrue(text.contains("\\u010cesk\\u00e1"))
    }

    @Test
    fun encodesErrors() {
        assertEquals("""{"c":"err","lg":"en","e":"noloc"}""", WatchProtocol.error(WatchProtocol.Error.NO_LOCATION, "en").decodeToString())
    }
}
