package io.github.juliajamnicka.brnomhd.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the watch link after a reboot if the user had it switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && WatchService.isEnabled(context)) {
            WatchService.start(context)
        }
    }
}
