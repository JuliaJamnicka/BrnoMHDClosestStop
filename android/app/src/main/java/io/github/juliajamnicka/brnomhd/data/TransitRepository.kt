package io.github.juliajamnicka.brnomhd.data

/** What the watch (and later the widget) needs; implemented over the backend API. */
interface TransitSource {
    suspend fun home(n: Int): DeparturesResponse
    suspend fun departures(platform: String, n: Int): DeparturesResponse
    suspend fun nearby(): NearbyResponse
    suspend fun vehicles(): VehiclesResponse
}

class NoLocationException : Exception("Location unavailable")

class TransitRepository(
    private val api: ApiClient,
    private val location: LocationSource,
    private val clock: () -> Long = System::currentTimeMillis,
) : TransitSource {
    private var homeCache: Pair<Long, DeparturesResponse>? = null

    override suspend fun home(n: Int): DeparturesResponse {
        // Reopening the watch app within 20 s is answered instantly (docs/SPEC.md 6)
        homeCache?.let { (at, value) -> if (clock() - at < HOME_CACHE_MS && value.dep.size >= n) return value }
        val point = location.current() ?: throw NoLocationException()
        return api.home(point, n).also { homeCache = clock() to it }
    }

    override suspend fun departures(platform: String, n: Int): DeparturesResponse =
        api.departures(platform, n, location.current())

    override suspend fun nearby(): NearbyResponse =
        api.nearby(location.current() ?: throw NoLocationException(), NEARBY_LIMIT)

    override suspend fun vehicles(): VehiclesResponse =
        api.vehicles(location.current() ?: throw NoLocationException(), RADAR_RADIUS_M)

    suspend fun warmUp() = api.warmUp()

    /** Fresh departures for the phone's own preview (bypasses the watch cache). */
    suspend fun departuresForPreview(n: Int): DeparturesResponse {
        val point = location.current() ?: throw NoLocationException()
        return api.home(point, n)
    }

    companion object {
        const val HOME_CACHE_MS = 20_000L
        const val NEARBY_LIMIT = 12
        const val RADAR_RADIUS_M = 800
    }
}
