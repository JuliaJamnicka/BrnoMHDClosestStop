package io.github.juliajamnicka.fcil.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NETWORK, AUTH, SERVER }
}

/** HTTP client for the mhd-api backend. */
class ApiClient(
    private val config: suspend () -> ApiConfig,
    private val http: OkHttpClient = defaultHttpClient(),
) {
    data class ApiConfig(val baseUrl: String, val apiKey: String)

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun home(at: GeoPoint, n: Int): DeparturesResponse =
        get("v1/home") { addQueryParameter("lat", at.lat.toString()).addQueryParameter("lon", at.lon.toString()).addQueryParameter("n", n.toString()) }

    suspend fun departures(platform: String, n: Int, at: GeoPoint?): DeparturesResponse =
        get("v1/departures") {
            addQueryParameter("platform", platform).addQueryParameter("n", n.toString())
            if (at != null) addQueryParameter("lat", at.lat.toString()).addQueryParameter("lon", at.lon.toString())
        }

    suspend fun board(platforms: List<String>, n: Int): BoardResponse =
        get("v1/board") { addQueryParameter("platforms", platforms.joinToString(",")).addQueryParameter("n", n.toString()) }

    /** Stops whose name contains [query] (case and diacritics ignored), nearest first when [at] is known. */
    suspend fun searchStops(query: String, at: GeoPoint?, limit: Int): NearbyResponse =
        get("v1/stops") {
            addQueryParameter("q", query).addQueryParameter("limit", limit.toString())
            if (at != null) addQueryParameter("lat", at.lat.toString()).addQueryParameter("lon", at.lon.toString())
        }

    suspend fun nearby(at: GeoPoint, limit: Int): NearbyResponse =
        get("v1/nearby") { addQueryParameter("lat", at.lat.toString()).addQueryParameter("lon", at.lon.toString()).addQueryParameter("limit", limit.toString()) }

    suspend fun vehicles(at: GeoPoint, radius: Int): VehiclesResponse =
        get("v1/vehicles") { addQueryParameter("lat", at.lat.toString()).addQueryParameter("lon", at.lon.toString()).addQueryParameter("r", radius.toString()) }

    /** Wakes a scaled-to-zero Cloud Run instance; errors are ignored. */
    suspend fun warmUp() {
        runCatching { requestBody("v1/health") {} }
    }

    private suspend inline fun <reified T> get(path: String, crossinline query: HttpUrl.Builder.() -> Unit): T {
        val body = requestBody(path) { query() }
        return try {
            json.decodeFromString<T>(body)
        } catch (e: Exception) {
            throw ApiException(ApiException.Kind.SERVER, "Unexpected response: ${e.message}")
        }
    }

    private suspend fun requestBody(path: String, query: HttpUrl.Builder.() -> Unit): String {
        val cfg = config()
        val url = try {
            cfg.baseUrl.trimEnd('/').plus("/").plus(path).toHttpUrl().newBuilder().apply(query).build()
        } catch (e: IllegalArgumentException) {
            throw ApiException(ApiException.Kind.SERVER, "Invalid server URL")
        }
        val request = Request.Builder().url(url).header("x-api-key", cfg.apiKey).build()
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { res ->
                    when {
                        res.code == 401 -> throw ApiException(ApiException.Kind.AUTH, "API key rejected")
                        !res.isSuccessful -> throw ApiException(ApiException.Kind.SERVER, "HTTP ${res.code}")
                        else -> res.body?.string() ?: ""
                    }
                }
            } catch (e: IOException) {
                throw ApiException(ApiException.Kind.NETWORK, e.message ?: "Network error")
            }
        }
    }

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            // Cloud Run cold start can take a few seconds
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
