package io.github.juliajamnicka.brnomhd.data

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
)

@Serializable
data class PlatformDto(val id: String, val dir: String, val l: String)

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
)

@Serializable
data class NearbyStop(val id: String, val n: String, val d: Int, val m: String, val p: List<PlatformDto>)

@Serializable
data class NearbyResponse(val t: Long, val stops: List<NearbyStop>)

@Serializable
data class VehicleDto(val l: String, val m: String, val dx: Int, val dy: Int, val b: Int, val dl: Int, val a: Int)

@Serializable
data class StopOffset(val n: String, val dx: Int, val dy: Int)

@Serializable
data class VehiclesResponse(val t: Long, val v: List<VehicleDto>, val s: List<StopOffset> = emptyList())

data class GeoPoint(val lat: Double, val lon: Double, val approximate: Boolean = false)
