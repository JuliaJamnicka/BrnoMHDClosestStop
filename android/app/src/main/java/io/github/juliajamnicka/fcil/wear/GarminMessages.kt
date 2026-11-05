package io.github.juliajamnicka.fcil.wear

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Connect IQ carries objects, not text: the Garmin watch app sends and receives Monkey C
 * dictionaries and arrays, which the companion SDK maps to Java maps and lists. These convert
 * between them and the JSON the rest of the watch link speaks.
 */
object GarminMessages {
    /** A message from the watch as the request JSON for [RequestHandler]. */
    fun toRequest(message: Any?): ByteArray = toJson(message).toString().encodeToByteArray()

    /** A reply from [WatchProtocol] as the objects the watch receives. */
    fun fromReply(reply: ByteArray): Any? = toObjects(Json.parseToJsonElement(reply.decodeToString()))

    fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is List<*> -> JsonArray(value.map(::toJson))
        is Array<*> -> JsonArray(value.map(::toJson))
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    /**
     * Monkey C numbers are 32-bit, so whole numbers that fit (epoch seconds, metres, minutes) go as
     * Int; larger ones as Long, fractions as Double.
     */
    fun toObjects(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonObject -> element.mapValuesTo(LinkedHashMap()) { (_, v) -> toObjects(v) }
        is JsonArray -> element.map(::toObjects)
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.booleanOrNull
            element.longOrNull != null -> element.longOrNull!!.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
            else -> element.doubleOrNull
        }
    }
}
