package io.github.juliajamnicka.fcil.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import io.github.juliajamnicka.fcil.FcilApp
import io.github.juliajamnicka.fcil.data.ApiException
import io.github.juliajamnicka.fcil.data.DeparturesResponse
import io.github.juliajamnicka.fcil.data.NoLocationException
import kotlinx.coroutines.CancellationException
import androidx.datastore.preferences.core.Preferences

/** Loads departures for widgets and stores them in their Glance state. */
object WidgetUpdater {
    @Volatile
    private var lastRefreshAll = 0L

    suspend fun refreshAll(context: Context, minIntervalMs: Long = 0) {
        val now = System.currentTimeMillis()
        if (now - lastRefreshAll < minIntervalMs) return
        lastRefreshAll = now
        val ids = GlanceAppWidgetManager(context).getGlanceIds(DeparturesWidget::class.java)
        ids.forEach { refresh(context, it) }
    }

    suspend fun refresh(context: Context, id: GlanceId, change: (WidgetConfig, DeparturesResponse?) -> WidgetConfig = { c, _ -> c }) {
        val before = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)
        val config = change(WidgetState.config(before), WidgetState.response(before))
        updateAppWidgetState(context, id) { p ->
            WidgetState.setConfig(p, config)
            p[WidgetState.loading] = true
        }
        DeparturesWidget().update(context, id)

        val result = load(context, config)
        updateAppWidgetState(context, id) { p ->
            p[WidgetState.loading] = false
            result.onSuccess { (newConfig, response) ->
                WidgetState.setConfig(p, newConfig)
                WidgetState.setResponse(p, response)
                p[WidgetState.updatedAt] = System.currentTimeMillis()
                p.remove(WidgetState.error)
            }.onFailure { e ->
                p[WidgetState.error] = when (e) {
                    is NoLocationException -> WidgetError.NO_LOCATION
                    is ApiException -> when (e.kind) {
                        ApiException.Kind.NETWORK -> WidgetError.NETWORK
                        ApiException.Kind.AUTH -> WidgetError.AUTH
                        ApiException.Kind.SERVER -> WidgetError.SERVER
                    }
                    else -> WidgetError.SERVER
                }.name
            }
        }
        DeparturesWidget().update(context, id)
    }

    /** Pins a widget to a platform or a stop list, or back to automatic when both are null. */
    suspend fun pin(context: Context, appWidgetId: Int, platform: String?, list: String? = null) {
        val id = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        refresh(context, id) { _, _ -> WidgetConfig(pinnedPlatform = platform, pinnedList = list) }
    }

    private suspend fun load(context: Context, config: WidgetConfig): Result<Pair<WidgetConfig, DeparturesResponse>> = try {
        val graph = (context.applicationContext as FcilApp).graph
        val n = graph.settings.current().departureCount
        val loaded = graph.repository.departuresFor(config, n)
        Result.success(loaded)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
