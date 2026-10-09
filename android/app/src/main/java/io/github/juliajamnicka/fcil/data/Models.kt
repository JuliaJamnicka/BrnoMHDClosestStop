package io.github.juliajamnicka.fcil.data

import kotlinx.serialization.Serializable

// Mirrors the backend API (backend/README.md). Field names are the backend's compact ones.

@Serializable
data class DepartureDto(
    /** Line, e.g. "4", "N93". */
    val l: String,
    /** Mode: T tram, B bus (incl. trolleybus), V train, L boat. */
    val m: String,
    /** Headsign. */
    val h: String,
    /** Expected departure, epoch seconds. */
    val e: Long,
    /** Scheduled departure, epoch seconds. */
    val s: Long,
    /** Delay in seconds. */
    val dl: Int,
    /** 1 when live vehicle data was used. */
    val lv: Int,
    /** Stop list boards only: the platform and its stop name. */
    val p: String? = null,
    val sn: String? = null,
)

@Serializable
data class PlatformDto(
    val id: String,
    val dir: String,
    val l: String,
    /** Coordinates (from the backend since stop lists); used to tell which stop list is nearby. */
    val la: Double? = null,
    val lo: Double? = null,
)

@Serializable
data class DeparturesResponse(
    val t: Long,
    val g: String,
    val stop: String,
    val p: String,
    val dir: String,
    val opp: String? = null,
    val d: Int? = null,
    val pl: List<PlatformDto> = emptyList(),
    val dep: List<DepartureDto> = emptyList(),
    /** Set by the phone when this is a stop list's board (stop = list name, no single platform). */
    val listId: String? = null,
)

@Serializable
data class BoardResponse(val t: Long, val dep: List<DepartureDto> = emptyList())

@Serializable
data class NearbyStop(
    val id: String,
    val n: String,
    /** Distance in metres; absent in search results made without a position. */
    val d: Int? = null,
    val m: String,
    val p: List<PlatformDto>,
)

@Serializable
data class NearbyResponse(val t: Long, val stops: List<NearbyStop>)

@Serializable
data class VehicleDto(
    val l: String,
    val m: String,
    val dx: Int,
    val dy: Int,
    /** Heading in degrees, 0 north; -1 when unknown. */
    val b: Int,
    val dl: Int,
    val a: Int,
    /** Headsign. */
    val h: String = "",
)

@Serializable
data class StopOffset(val n: String, val dx: Int, val dy: Int)

@Serializable
data class VehiclesResponse(val t: Long, val v: List<VehicleDto>, val s: List<StopOffset> = emptyList())

data class GeoPoint(val lat: Double, val lon: Double, val approximate: Boolean = false)
