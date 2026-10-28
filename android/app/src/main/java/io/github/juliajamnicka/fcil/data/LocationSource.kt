package io.github.juliajamnicka.fcil.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

fun interface LocationSource {
    /** Current position, or null when location is unavailable or not permitted. */
    suspend fun current(): GeoPoint?
}

/** Phone location via Google's fused provider (docs/SPEC.md 6). */
class FusedLocationSource(private val context: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun current(): GeoPoint? {
        if (!hasPermission()) return null
        val last = runCatching { client.lastLocation.await() }.getOrNull()
        if (last != null && ageSeconds(last) < 30 && last.accuracy < 50) return last.toPoint()
        // A fix up to 2 minutes old answers at once (waiting for a fresh one took seconds, e.g. for
        // the first watch request with the phone in a pocket); a fresh fix is still requested, so
        // the next request (the watch refreshes every 20 s) uses it.
        if (last != null && ageSeconds(last) < QUICK_AGE_S && last.accuracy < 100) {
            runCatching { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token) }
            return last.toPoint()
        }

        val token = CancellationTokenSource()
        val fresh = withTimeoutOrNull(4_000) {
            runCatching { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await() }.getOrNull()
        }
        if (fresh == null) token.cancel()
        return when {
            fresh != null -> fresh.toPoint()
            last != null && ageSeconds(last) < 600 -> last.toPoint(approximate = true)
            else -> null
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun ageSeconds(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000_000

    private fun Location.toPoint(approximate: Boolean = false) = GeoPoint(latitude, longitude, approximate)
}

/** A last known fix this young is used at once while a fresh one is requested (FusedLocationSource). */
private const val QUICK_AGE_S = 120
