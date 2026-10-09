package io.github.juliajamnicka.fcil.data

import kotlinx.serialization.Serializable
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A user's stop list (docs/SPEC.md 6.2), e.g. "Home from work": platforms near one place that
 * all lead where the user wants to go. When one of them is nearby, the list's merged departures
 * are shown instead of the single nearest stop.
 */
@Serializable
data class StopList(val id: String, val name: String, val entries: List<StopListEntry> = emptyList())

@Serializable
data class StopListEntry(
    val platform: String,
    val stop: String,
    val dir: String,
    val lat: Double,
    val lon: Double,
    /** Lines serving the platform, shown in the editor (absent in lists saved by older versions). */
    val lines: String = "",
)

object StopListLogic {
    /** A list switches on when any of its stops is within this distance. */
    const val NEARBY_M = 500.0

    /** The list with the closest stop within [NEARBY_M] of [at], if any. */
    fun nearby(lists: List<StopList>, at: GeoPoint): StopList? =
        lists
            .mapNotNull { list -> list.entries.minOfOrNull { distanceM(at.lat, at.lon, it.lat, it.lon) }?.let { list to it } }
            .filter { (_, d) -> d <= NEARBY_M }
            .minByOrNull { (_, d) -> d }
            ?.first

    /** Replaces the list with [id], or adds a new one when there is none (or [id] is null). */
    fun save(lists: List<StopList>, id: String, name: String, entries: List<StopListEntry>): List<StopList> {
        val list = StopList(id, name.trim(), entries.distinctBy { it.platform })
        return if (lists.any { it.id == id }) lists.map { if (it.id == id) list else it } else lists + list
    }


    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val toRad = Math.PI / 180
        val a = sin((lat2 - lat1) * toRad / 2).pow(2) + cos(lat1 * toRad) * cos(lat2 * toRad) * sin((lon2 - lon1) * toRad / 2).pow(2)
        return 2 * 6_371_000.0 * asin(sqrt(a))
    }
}
