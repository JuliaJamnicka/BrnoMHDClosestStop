package io.github.juliajamnicka.fcil.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StopListLogicTest {
    private fun entry(platform: String, lat: Double, lon: Double) = StopListEntry(platform, "Stop", "Dir", lat, lon)

    // Česká is at about (49.1980, 16.6060); 0.001° of latitude is about 111 m
    private val work = StopList("work", "Domů z práce", listOf(entry("U1Z2", 49.1980, 16.6060), entry("U4Z1", 49.2006, 16.6061)))
    private val far = StopList("far", "Babička", listOf(entry("U9Z1", 49.2300, 16.5800)))

    @Test
    fun picksTheListWithAStopWithinWalkingDistance() {
        assertEquals(work, StopListLogic.nearby(listOf(far, work), GeoPoint(49.1990, 16.6060)))
        assertNull(StopListLogic.nearby(listOf(far, work), GeoPoint(49.2100, 16.6060)))
        assertNull(StopListLogic.nearby(listOf(StopList("empty", "Empty")), GeoPoint(49.1990, 16.6060)))
    }

    @Test
    fun picksTheListWithTheClosestStopWhenSeveralQualify() {
        val near = StopList("near", "Near", listOf(entry("U4Z1", 49.2006, 16.6061)))
        assertEquals(near, StopListLogic.nearby(listOf(work.copy(entries = work.entries.take(1)), near), GeoPoint(49.2004, 16.6061)))
    }

    @Test
    fun addsEachPlatformOnceAndRemovesIt() {
        val added = StopListLogic.add(listOf(far), "far", entry("U8Z1", 49.0, 16.0))
        assertEquals(listOf("U9Z1", "U8Z1"), added.single().entries.map { it.platform })
        assertEquals(added, StopListLogic.add(added, "far", entry("U8Z1", 49.0, 16.0)))
        assertEquals(listOf("U9Z1"), StopListLogic.remove(added, "far", "U8Z1").single().entries.map { it.platform })
    }
}
