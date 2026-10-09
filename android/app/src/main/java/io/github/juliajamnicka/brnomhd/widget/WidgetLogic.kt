package io.github.juliajamnicka.brnomhd.widget

import io.github.juliajamnicka.brnomhd.data.DeparturesResponse
import io.github.juliajamnicka.brnomhd.data.TransitRepository

/**
 * Which departures a widget, or the app's departures screen, shows (pure logic, unit tested).
 *
 * - Default: the home stop chosen by the backend (nearest platform with service soon).
 * - Reverse while following the home stop: remember the stop group and show its opposite
 *   platform until the home stop changes (the user walked elsewhere) or reverse is pressed again.
 * - A platform picked in the stop list is pinned until "Nearest stop" is chosen; reverse then
 *   moves the pin to the opposite platform.
 */
data class WidgetConfig(
    val pinnedPlatform: String? = null,
    val reversedGroup: String? = null,
)

object WidgetLogic {
    sealed interface Fetch {
        data object Home : Fetch
        data class Platform(val id: String) : Fetch
    }

    fun firstFetch(config: WidgetConfig): Fetch =
        config.pinnedPlatform?.let { Fetch.Platform(it) } ?: Fetch.Home

    /** After loading the home stop: the config to keep and, if reversed, the platform to load instead. */
    fun afterHome(config: WidgetConfig, home: DeparturesResponse): Pair<WidgetConfig, String?> =
        if (config.reversedGroup == home.g && home.opp != null) config to home.opp
        else config.copy(reversedGroup = null) to null

    /** Config after pressing reverse while [shown] is on screen. */
    fun reverse(config: WidgetConfig, shown: DeparturesResponse?): WidgetConfig = when {
        shown == null -> config
        config.pinnedPlatform != null -> config.copy(pinnedPlatform = shown.opp ?: config.pinnedPlatform)
        config.reversedGroup == shown.g -> config.copy(reversedGroup = null)
        shown.opp != null -> config.copy(reversedGroup = shown.g)
        else -> config
    }
}

/** Loads what [config] asks for; returns the config to keep (a stale reverse is dropped) and the departures. */
suspend fun TransitRepository.departuresFor(config: WidgetConfig, n: Int): Pair<WidgetConfig, DeparturesResponse> =
    when (val fetch = WidgetLogic.firstFetch(config)) {
        is WidgetLogic.Fetch.Platform -> config to departures(fetch.id, n)
        WidgetLogic.Fetch.Home -> {
            val home = departuresForPreview(n)
            val (newConfig, reversed) = WidgetLogic.afterHome(config, home)
            newConfig to (reversed?.let { departures(it, n) } ?: home)
        }
    }
