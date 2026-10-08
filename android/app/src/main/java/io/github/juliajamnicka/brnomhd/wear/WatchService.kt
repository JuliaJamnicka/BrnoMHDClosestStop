package io.github.juliajamnicka.brnomhd.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.juliajamnicka.brnomhd.MhdApp
import io.github.juliajamnicka.brnomhd.R
import io.github.juliajamnicka.brnomhd.ui.MainActivity
import io.github.juliajamnicka.brnomhd.widget.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the Wear Engine receiver alive so the watch can ask for departures at any time while the
 * phone stays in the pocket. It does no work until a message arrives (no polling).
 */
class WatchService : LifecycleService() {
    private lateinit var bridge: WearBridge
    private lateinit var handler: RequestHandler
    private var connectJob: Job? = null

    // While the watch link runs anyway, also refresh home-screen widgets when the phone is unlocked
    // (at most once a minute); otherwise Android only allows a widget refresh every 15 minutes.
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lifecycleScope.launch { WidgetUpdater.refreshAll(applicationContext, minIntervalMs = 60_000) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val graph = (application as MhdApp).graph
        bridge = WearBridge(this)
        handler = RequestHandler(
            source = graph.repository,
            departureCount = { graph.settings.current().departureCount },
            language = graph::watchLanguage,
        )
        ContextCompat.registerReceiver(this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startInForeground()
        if (connectJob?.isActive != true) connectJob = lifecycleScope.launch { connectLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        bridge.disconnect()
        WatchStatus.update { WatchStatus.State() }
        super.onDestroy()
    }

    private suspend fun connectLoop() {
        while (lifecycleScope.isActive) {
            try {
                bridge.connect(onMessage = ::onMessage, onConnectionChanged = { connected ->
                    WatchStatus.update { it.copy(connected = connected) }
                })
                WatchStatus.update { it.copy(running = true, deviceName = bridge.deviceName, connected = true, lastError = null) }
                return
            } catch (e: Exception) {
                WatchStatus.update { it.copy(running = true, connected = false, lastError = e.message ?: e.javaClass.simpleName) }
                delay(RETRY_MS)
            }
        }
    }

    private fun onMessage(bytes: ByteArray) {
        lifecycleScope.launch {
            WatchStatus.update { it.copy(lastRequestAt = System.currentTimeMillis()) }
            val reply = handler.handle(bytes)
            if (!bridge.send(reply)) {
                WatchStatus.update { it.copy(lastError = getString(R.string.status_send_failed)) }
            }
        }
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        val withLocation = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                (if (withLocation) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        } else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
        } catch (e: SecurityException) {
            // Started from the background (e.g. after boot): location type is not allowed then.
            // Location still works if "Allow all the time" was granted.
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        }
    }

    companion object {
        private const val CHANNEL_ID = "watch_link"
        private const val NOTIFICATION_ID = 1
        private const val RETRY_MS = 60_000L
        private const val PREFS = "watch_service"
        private const val KEY_ENABLED = "enabled"

        fun start(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, true) }
            ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java))
        }

        fun stop(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, false) }
            context.stopService(Intent(context, WatchService::class.java))
        }

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
    }
}
