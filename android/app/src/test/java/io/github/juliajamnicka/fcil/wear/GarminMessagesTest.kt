package io.github.juliajamnicka.fcil.wear

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class GarminMessagesTest {
    @Test
    fun turnsAWatchDictionaryIntoRequestJson() {
        val request = GarminMessages.toRequest(mapOf("c" to "dep", "p" to "U1073Z2"))
        val json = Json.parseToJsonElement(request.decodeToString()).jsonObject
        assertEquals("dep", json["c"]?.jsonPrimitive?.content)
        assertEquals(WatchProtocol.Request.Departures("U1073Z2"), WatchProtocol.parseRequest(request))
    }

    @Test
    fun turnsAReplyIntoMonkeyCFriendlyObjects() {
        // as WatchProtocol sends it: non-ASCII escaped, positional rows
        val reply = """{"c":"dep","lg":"cs","t":1700000000,"s":"Česká","d":85,"x":[["4","T","Nám. Míru",1700000300,2,1]]}"""
        val objects = GarminMessages.fromReply(reply.encodeToByteArray()) as Map<*, *>
        assertEquals("Česká", objects["s"])
        // whole numbers that fit go as Int (Monkey C Number), not Long
        assertEquals(1700000000, objects["t"])
        assertEquals(85, objects["d"])
        val row = (objects["x"] as List<*>).single() as List<*>
        assertEquals(listOf("4", "T", "Nám. Míru", 1700000300, 2, 1), row)
    }

    @Test
    fun keepsLargeNumbersAndFractions() {
        val objects = GarminMessages.fromReply("""{"a":5000000000,"b":1.5,"c":true,"d":null}""".encodeToByteArray()) as Map<*, *>
        assertEquals(5000000000L, objects["a"])
        assertEquals(1.5, objects["b"])
        assertEquals(true, objects["c"])
        assertEquals(null, objects["d"])
    }
}
