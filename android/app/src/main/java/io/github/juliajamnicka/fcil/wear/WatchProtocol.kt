package io.github.juliajamnicka.fcil.wear

import io.github.juliajamnicka.fcil.data.DeparturesResponse
import io.github.juliajamnicka.fcil.data.NearbyResponse
import io.github.juliajamnicka.fcil.data.TransitRepository
import io.github.juliajamnicka.fcil.data.VehicleDto
import io.github.juliajamnicka.fcil.data.VehiclesResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Messages between the phone and the watch over Wear Engine P2P (docs/SPEC.md 6).
 *
 * Watch -> phone:  {"c":"home"} | {"c":"dep","p":"U1073Z2"} | {"c":"pl","p":"U1073Z2"} | {"c":"near"}
 *                  | {"c":"veh","r":radiusMetres,"m":[[line, headsign], ...]}
 * Phone -> watch:  compact JSON with "c" echoing the kind, "lg" the UI language and lists as
 * positional arrays to stay under [MAX_BYTES]; see the builders below for the field order.
 */
object WatchProtocol {
    /** Kept below the Wear Engine message size limit; entries at the end of lists are dropped to fit. */
    const val MAX_BYTES = 1000
    const val HEADSIGN_MAX = 20
    const val NAME_MAX = 20
    /** Radar headsigns: one line of the watch's vehicle card. */
    const val RADAR_HEADSIGN_MAX = 16
    const val RADAR_RADIUS_MIN = 100
    const val RADAR_RADIUS_MAX = 2000

    sealed interface Request {
        data object Home : Request
        data class Departures(val platform: String) : Request
        data class Platforms(val platform: String) : Request
        data object Nearby : Request
        /**
         * Vehicles within [radius] metres. [mine] are the watch's departures as (line, headsign as
         * the watch got it, "" for any direction); vehicles matching one are marked and sent first.
         */
        data class Vehicles(
            val radius: Int = TransitRepository.RADAR_RADIUS_M,
            val mine: List<Pair<String, String>> = emptyList(),
        ) : Request
    }

    enum class Error(val code: String) { NO_LOCATION("noloc"), OUT_OF_RANGE("far"), NETWORK("net"), SERVER("srv"), AUTH("auth"), BAD_REQUEST("req") }

    fun parseRequest(bytes: ByteArray): Request? = try {
        val obj = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        val platform = (obj["p"] as? JsonPrimitive)?.content
        when (obj["c"]?.jsonPrimitive?.content) {
            "home" -> Request.Home
            "dep" -> platform?.let { Request.Departures(it) }
            "pl" -> platform?.let { Request.Platforms(it) }
            "near" -> Request.Nearby
            "veh" -> Request.Vehicles(
                radius = ((obj["r"] as? JsonPrimitive)?.intOrNull ?: TransitRepository.RADAR_RADIUS_M).coerceIn(RADAR_RADIUS_MIN, RADAR_RADIUS_MAX),
                mine = (obj["m"] as? JsonArray).orEmpty().mapNotNull { entry ->
                    val pair = entry as? JsonArray ?: return@mapNotNull null
                    val line = (pair.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null
                    line to ((pair.getOrNull(1) as? JsonPrimitive)?.content ?: "")
                },
            )
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    /**
     * {"c":"dep","lg","t","g","s":stop,"p":platform,"o":opposite,"r":direction,"d":distance,
     *  "x":[[line, mode, headsign, expectedEpoch, delayMinutes, live0or1], ...]}
     *
     * A stop list's board (docs/SPEC.md 6.2) has "l":1, the list name as "s", empty "p" and "r",
     * and each row's stop name in place of the headsign: the user knows where their lines go.
     * "tf":1 asks the watch for clock times instead of minutes (the departure time setting).
     */
    fun departures(r: DeparturesResponse, lang: String, clock: Boolean = false): ByteArray = fit(
        buildJsonObject {
            put("c", "dep")
            put("lg", lang)
            put("t", r.t)
            put("g", r.g)
            put("s", TextShortener.shorten(r.stop, NAME_MAX))
            put("p", r.p)
            if (r.listId != null) put("l", 1)
            r.opp?.let { put("o", it) }
            put("r", TextShortener.shorten(r.dir, 24))
            r.d?.let { put("d", it) }
            if (clock) put("tf", 1)
        },
        r.dep.map { d ->
            buildJsonArray {
                add(JsonPrimitive(d.l))
                add(JsonPrimitive(d.m))
                add(JsonPrimitive(TextShortener.shorten(d.sn ?: d.h, HEADSIGN_MAX)))
                add(JsonPrimitive(d.e))
                add(JsonPrimitive(d.dl / 60))
                add(JsonPrimitive(d.lv))
            }
        },
    )

    /** {"c":"pl","lg","s":stop,"p":current,"x":[[platformId, lines, direction], ...]} */
    fun platforms(r: DeparturesResponse, lang: String): ByteArray = fit(
        buildJsonObject {
            put("c", "pl")
            put("lg", lang)
            put("s", TextShortener.shorten(r.stop, NAME_MAX))
            put("p", r.p)
        },
        r.pl.map { p ->
            buildJsonArray {
                add(JsonPrimitive(p.id))
                add(JsonPrimitive(p.l))
                add(JsonPrimitive(TextShortener.shorten(p.dir, HEADSIGN_MAX)))
            }
        },
    )

    /** {"c":"near","lg","t","x":[[closestPlatformId, name, distanceM, modes], ...]} */
    fun nearby(r: NearbyResponse, lang: String): ByteArray = fit(
        buildJsonObject {
            put("c", "near")
            put("lg", lang)
            put("t", r.t)
        },
        r.stops.map { s ->
            buildJsonArray {
                add(JsonPrimitive(s.p.firstOrNull()?.id ?: s.id))
                add(JsonPrimitive(TextShortener.shorten(s.n, NAME_MAX)))
                add(JsonPrimitive(s.d ?: 0))
                add(JsonPrimitive(s.m))
            }
        },
    )

    /**
     * {"c":"veh","lg","t","s":[[stopName, dx, dy], ...],
     *  "x":[[line, mode, dxMetres, dyMetres, headingDegreesOrMinus1, delayMinutes, headsign, mine0or1], ...]}
     *
     * A vehicle is "mine" when its line and headsign match one of [mine] (headsigns shortened as in
     * [departures], so they compare equal; an empty headsign matches any). Those come first, so the
     * size limit drops other vehicles before them; the rest stay nearest first.
     */
    fun vehicles(r: VehiclesResponse, lang: String, mine: List<Pair<String, String>> = emptyList()): ByteArray {
        fun isMine(v: VehicleDto) = mine.any { (line, headsign) ->
            line == v.l && (headsign.isEmpty() || headsign == TextShortener.shorten(v.h, HEADSIGN_MAX))
        }
        return fit(
            buildJsonObject {
                put("c", "veh")
                put("lg", lang)
                put("t", r.t)
                put("s", buildJsonArray {
                    r.s.take(3).forEach { s ->
                        add(buildJsonArray {
                            add(JsonPrimitive(TextShortener.shorten(s.n, 14)))
                            add(JsonPrimitive(s.dx))
                            add(JsonPrimitive(s.dy))
                        })
                    }
                })
            },
            r.v.map { it to isMine(it) }.sortedByDescending { it.second }.map { (v, own) ->
                buildJsonArray {
                    add(JsonPrimitive(v.l))
                    add(JsonPrimitive(v.m))
                    add(JsonPrimitive(v.dx))
                    add(JsonPrimitive(v.dy))
                    add(JsonPrimitive(v.b))
                    add(JsonPrimitive(v.dl / 60))
                    add(JsonPrimitive(TextShortener.shorten(v.h, RADAR_HEADSIGN_MAX)))
                    add(JsonPrimitive(if (own) 1 else 0))
                }
            },
        )
    }

    /** {"c":"err","lg","e":code} */
    fun error(error: Error, lang: String): ByteArray =
        buildJsonObject {
            put("c", "err")
            put("lg", lang)
            put("e", error.code)
        }.toString().encodeToByteArray()

    /**
     * JSON with every non-ASCII character escaped as \uXXXX, so Czech names survive whatever text
     * encoding the watch side of Wear Engine assumes; JSON.parse on the watch restores them.
     */
    fun asciiJson(json: String): String = buildString(json.length) {
        for (c in json) if (c.code < 0x80) append(c) else append("\\u%04x".format(c.code))
    }

    /** Adds [entries] as "x", dropping entries from the end until the message fits. */
    private fun fit(head: JsonObject, entries: List<JsonArray>): ByteArray {
        var count = entries.size
        while (true) {
            val message = asciiJson(JsonObject(head + ("x" to JsonArray(entries.take(count)))).toString()).encodeToByteArray()
            if (message.size <= MAX_BYTES || count == 0) return message
            count--
        }
    }
}
