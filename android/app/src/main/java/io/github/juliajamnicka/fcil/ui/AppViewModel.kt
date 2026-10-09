package io.github.juliajamnicka.fcil.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.juliajamnicka.fcil.FcilApp
import io.github.juliajamnicka.fcil.R
import io.github.juliajamnicka.fcil.data.ApiException
import io.github.juliajamnicka.fcil.data.DeparturesResponse
import io.github.juliajamnicka.fcil.data.NearbyResponse
import io.github.juliajamnicka.fcil.data.NoLocationException
import io.github.juliajamnicka.fcil.data.StopList
import io.github.juliajamnicka.fcil.data.StopListEntry
import io.github.juliajamnicka.fcil.data.StopListLogic
import io.github.juliajamnicka.fcil.data.VehiclesResponse
import io.github.juliajamnicka.fcil.widget.WidgetConfig
import io.github.juliajamnicka.fcil.widget.WidgetLogic
import io.github.juliajamnicka.fcil.widget.departuresFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

/** Something loaded from the backend: the last good value is kept while reloading or after an error. */
data class Loadable<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: Long? = null,
)

/**
 * State of the app's own screens (departures, stops, radar). Choosing a stop or a stop list works
 * like the widget: pinned until "Nearest stop" is chosen, reverse follows the opposite platform
 * (WidgetLogic); unpinned, a stop list shows by itself when one of its stops is nearby.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as FcilApp).graph
    private val repository = graph.repository

    /** The user's stop lists (docs/SPEC.md 6.2). */
    val stopLists: Flow<List<StopList>> = graph.settings.stopLists

    var config by mutableStateOf(WidgetConfig())
        private set
    var departures by mutableStateOf(Loadable<DeparturesResponse>())
        private set
    var stops by mutableStateOf(Loadable<NearbyResponse>())
        private set
    var radar by mutableStateOf(Loadable<VehiclesResponse>())
        private set

    private var departuresJob: Job? = null

    fun refreshDepartures() {
        departuresJob?.cancel()
        val requested = config
        departures = departures.copy(loading = true)
        departuresJob = viewModelScope.launch {
            departures = try {
                val (kept, response) = repository.departuresFor(requested, DEPARTURES_SHOWN)
                config = kept
                Loadable(response, updatedAt = System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                departures.copy(loading = false, error = errorMessage(getApplication(), e))
            }
        }
    }

    fun reverse() {
        config = WidgetLogic.reverse(config, departures.data)
        refreshDepartures()
    }

    /** Pins [platform], or goes back to automatic (nearest stop or a nearby list) when it is null. */
    fun pin(platform: String?) {
        config = WidgetConfig(pinnedPlatform = platform)
        refreshDepartures()
    }

    fun pinList(id: String) {
        config = WidgetConfig(pinnedList = id)
        refreshDepartures()
    }

    fun createList(name: String, first: StopListEntry?) = editLists { lists ->
        lists + StopList(UUID.randomUUID().toString(), name.trim(), listOfNotNull(first))
    }

    fun addToList(listId: String, entry: StopListEntry) = editLists { StopListLogic.add(it, listId, entry) }

    fun removeFromList(listId: String, platform: String) = editLists { StopListLogic.remove(it, listId, platform) }

    fun deleteList(listId: String) {
        if (config.pinnedList == listId) config = WidgetConfig()
        editLists { lists -> lists.filter { it.id != listId } }
    }

    private fun editLists(change: (List<StopList>) -> List<StopList>) {
        viewModelScope.launch { graph.settings.updateStopLists(change) }
    }

    fun refreshStops() {
        stops = stops.copy(loading = true)
        viewModelScope.launch {
            stops = try {
                Loadable(repository.nearby(), updatedAt = System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stops.copy(loading = false, error = errorMessage(getApplication(), e))
            }
        }
    }

    fun refreshRadar() {
        radar = radar.copy(loading = true)
        viewModelScope.launch {
            radar = try {
                Loadable(repository.vehicles(), updatedAt = System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                radar.copy(loading = false, error = errorMessage(getApplication(), e))
            }
        }
    }

    companion object {
        /** The phone has room for more than the watch (the backend allows up to 8). */
        const val DEPARTURES_SHOWN = 8
    }
}

fun errorMessage(context: Context, e: Exception): String = when (e) {
    is NoLocationException -> context.getString(R.string.error_no_location)
    is ApiException -> when (e.kind) {
        ApiException.Kind.AUTH -> context.getString(R.string.error_auth)
        ApiException.Kind.NETWORK -> context.getString(R.string.error_network)
        ApiException.Kind.SERVER -> context.getString(R.string.error_server, e.message ?: "")
    }
    else -> context.getString(R.string.error_server, e.message ?: "")
}
