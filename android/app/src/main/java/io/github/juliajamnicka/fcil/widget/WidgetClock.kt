package io.github.juliajamnicka.fcil.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import android.os.PowerManager
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import io.github.juliajamnicka.fcil.FcilApp
import io.github.juliajamnicka.fcil.data.DepartureTime
import kotlinx.coroutines.launch

/**
 * Keeps the widgets current while the phone is in use. Data is loaded every 15 minutes at most in
 * the background (RefreshWorker), so once a minute the widgets are redrawn from the stored times
 * (minutes recounted, departed rows dropped) and, with the screen on, data older than
 * [STALE_AFTER] or missing departed rows is reloaded. The alarm does not wake the phone: with the
 * screen off it waits until the phone is in use again.
 */
object WidgetClock {
    private const val MINUTE = 60_000L
    private const val STALE_AFTER = 5 * MINUTE

    /**
     * Applies the departure time setting to all widgets, redraws them and starts or stops the
     * minute alarm; on the alarm's [tick] also reloads stale data while the screen is on.
     */
    suspend fun sync(context: Context, tick: Boolean = false) {
        val graph = (context.applicationContext as FcilApp).graph
        val clock = graph.settings.current().departureTime == DepartureTime.CLOCK
        val ids = GlanceAppWidgetManager(context).getGlanceIds(DeparturesWidget::class.java)
        val now = System.currentTimeMillis()
        if (tick && context.getSystemService(PowerManager::class.java).isInteractive && ids.any { isStale(context, it, now) }) {
            RefreshWorker.refreshNow(context) // waits for a network
        }
        ids.forEach { id ->
            // a changed state makes Glance recompose the widget, which recounts the minutes
            updateAppWidgetState(context, id) { p ->
                p[WidgetState.clockTimes] = clock
                p[WidgetState.redrawnAt] = now
            }
            DeparturesWidget().update(context, id)
        }
        if (ids.isNotEmpty()) schedule(context) else cancel(context)
    }

    private suspend fun isStale(context: Context, id: GlanceId, now: Long): Boolean {
        val p = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)
        if (p[WidgetState.loading] == true) return false
        val age = now - (p[WidgetState.updatedAt] ?: 0L)
        val departed = WidgetState.response(p)?.dep?.any { it.e * 1000 < now - MINUTE } == true
        return age > STALE_AFTER || (departed && age > MINUTE)
    }

    private fun schedule(context: Context) {
        val now = System.currentTimeMillis()
        // first redraw on the next full minute, when "N min" changes
        context.getSystemService(AlarmManager::class.java)
            .setRepeating(AlarmManager.RTC, now - now % MINUTE + MINUTE, MINUTE, intent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(intent(context))
    }

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WidgetTickReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The minute alarm of [WidgetClock]. */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val graph = (context.applicationContext as FcilApp).graph
        graph.scope.launch {
            try {
                WidgetClock.sync(context, tick = true)
            } finally {
                pending.finish()
            }
        }
    }
}
