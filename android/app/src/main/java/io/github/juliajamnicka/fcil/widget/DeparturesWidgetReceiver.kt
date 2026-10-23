package io.github.juliajamnicka.fcil.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.juliajamnicka.fcil.FcilApp
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DeparturesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeparturesWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // re-registers the periodic refresh too: installs from before it waited for a network keep their old one
        RefreshWorker.schedule(context)
        RefreshWorker.refreshNow(context)
        // also after a reboot, which clears alarms: (re)starts the minute redraw when it is needed
        val graph = (context.applicationContext as FcilApp).graph
        graph.scope.launch { WidgetClock.sync(context.applicationContext) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshWorker.cancel(context)
        WidgetClock.cancel(context)
    }
}

/**
 * Refreshes all widgets; Android allows periodic work at most every 15 minutes. It runs only with a
 * network (after the phone wakes, mobile data or Wi-Fi takes a few seconds to come back) and is
 * retried when the network fails anyway, so a refresh does not end as "cannot reach the server".
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (WidgetUpdater.refreshAll(applicationContext) && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()

    companion object {
        private const val PERIODIC = "widget_refresh"
        private const val ONCE = "widget_refresh_now"
        private const val MAX_RETRIES = 3
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).setConstraints(online).build(),
            )
        }

        /** Refreshes once the phone is online; a refresh already waiting is kept, not restarted. */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(online).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
