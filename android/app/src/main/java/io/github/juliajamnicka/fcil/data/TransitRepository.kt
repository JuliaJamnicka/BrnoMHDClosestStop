package io.github.juliajamnicka.fcil.data

/** What the watch (and the widget) needs; implemented over the backend API. */
interface TransitSource {
    suspend fun home(n: Int): DeparturesResponse
    suspend fun departures(platform: String, n: Int): DeparturesResponse
    suspend fun nearby(): NearbyResponse
    suspend fun vehicles(): VehiclesResponse
}

class NoLocationException : Exception("Location unavailable")

/** The nearest stop is too far for IDS JMK to be of use here (e.g. abroad): show that instead of the stop. */
class OutOfRangeException : Exception("Outside the IDS JMK area")

class TransitRepository(
    private val api: ApiClient,
    private val location: LocationSource,
    private val clock: () -> Long = System::currentTimeMillis,
    /** The user's stop lists (docs/SPEC.md 6.2). */
    private val stopLists: suspend () -> List<StopList> = { emptyList() },
) : TransitSource {
    private var homeCache: Pair<Long, DeparturesResponse>? = null

    override suspend fun home(n: Int): DeparturesResponse {
        // Reopening the watch app within 20 s is answered instantly (docs/SPEC.md 6)
        homeCache?.let { (at, value) -> if (clock() - at < HOME_CACHE_MS && value.dep.size >= n) return value }
        return departuresForPreview(n).also { homeCache = clock() to it }
    }

    override suspend fun departures(platform: String, n: Int): DeparturesResponse =
        api.departures(platform, n, location.current())

    override suspend fun nearby(): NearbyResponse =
        api.nearby(location.current() ?: throw NoLocationException(), NEARBY_LIMIT)

    override suspend fun vehicles(): VehiclesResponse =
        api.vehicles(location.current() ?: throw NoLocationException(), RADAR_RADIUS_M)

    suspend fun warmUp() = api.warmUp()

    /** Stop search for the stop-list editor; works without a location too. */
    suspend fun searchStops(query: String): NearbyResponse = api.searchStops(query, location.current(), SEARCH_LIMIT)

    /**
     * What to show here and now, fetched fresh: the board of a stop list with a stop nearby,
     * else the nearest stop with service soon (as chosen by the backend).
     */
    suspend fun departuresForPreview(n: Int): DeparturesResponse {
        val point = location.current() ?: throw NoLocationException()
        StopListLogic.nearby(stopLists(), point)?.let { return board(it, n) }
        val home = api.home(point, n)
        if ((home.d ?: 0) > OUT_OF_RANGE_M) throw OutOfRangeException()
        return home
    }

    /** The stop list with this id, or null when it was deleted. */
    suspend fun stopList(id: String): StopList? = stopLists().firstOrNull { it.id == id }

    /** A stop list's departures in one timeline, shaped like a single stop's response. */
    suspend fun board(list: StopList, n: Int): DeparturesResponse {
        val board = api.board(list.entries.map { it.platform }, n, StopListLogic.hideParam(list))
        val dep = StopListLogic.visible(list, board.dep)
        return DeparturesResponse(t = board.t, g = "", stop = list.name, p = "", dir = "", dep = dep, listId = list.id)
    }

    companion object {
        const val HOME_CACHE_MS = 20_000L
        /** Inside the region the nearest stop is a few km away at most; Bratislava's was 60 km. */
        const val OUT_OF_RANGE_M = 10_000
        const val NEARBY_LIMIT = 12
        const val RADAR_RADIUS_M = 800
        const val SEARCH_LIMIT = 20
    }
}
