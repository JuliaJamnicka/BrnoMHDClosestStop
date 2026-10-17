package io.github.juliajamnicka.fcil.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import io.github.juliajamnicka.fcil.FcilApp
import io.github.juliajamnicka.fcil.data.DepartureTime
import kotlinx.coroutines.launch

/**
 * Keeps the widgets' minutes current. Data is loaded every 15 minutes at most (RefreshWorker), so
 * while departures show minutes the widgets are redrawn once a minute from the stored times. The
 * alarm does not wake the phone: with the screen off it waits until the phone is in use again.
 */
object WidgetClock {
    private const val MINUTE = 60_000L

    /** Applies the departure time setting to all widgets, redraws them and starts or stops the minute alarm. */
    suspend fun sync(context: Context) {
        val graph = (context.applicationContext as FcilApp).graph
        val clock = graph.settings.current().departureTime == DepartureTime.CLOCK
        val ids = GlanceAppWidgetManager(context).getGlanceIds(DeparturesWidget::class.java)
        val now = System.currentTimeMillis()
        ids.forEach { id ->
            // a changed state makes Glance recompose the widget, which recounts the minutes
            updateAppWidgetState(context, id) { p ->
                p[WidgetState.clockTimes] = clock
                p[WidgetState.redrawnAt] = now
            }
            DeparturesWidget().update(context, id)
        }
        if (!clock && ids.isNotEmpty()) schedule(context) else cancel(context)
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
                WidgetClock.sync(context)
            } finally {
                pending.finish()
            }
        }
    }
}
