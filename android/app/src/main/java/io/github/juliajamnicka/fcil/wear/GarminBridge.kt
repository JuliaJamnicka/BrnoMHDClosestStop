package io.github.juliajamnicka.fcil.wear

import android.content.Context
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import io.github.juliajamnicka.fcil.BuildConfig
import io.github.juliajamnicka.fcil.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Connect IQ link to the Garmin watch app (garmin/ in the repository) through the Garmin Connect
 * app, which must be installed with the watch paired. No permission needs to be requested: the
 * watch app's id ([BuildConfig.GARMIN_APP_ID], the id in garmin/manifest.xml) is all that pairs them.
 */
class GarminBridge(private val context: Context) : WatchLink {
    private val connectIQ = ConnectIQ.getInstance(
        context.applicationContext,
        if (BuildConfig.GARMIN_TETHERED) ConnectIQ.IQConnectType.TETHERED else ConnectIQ.IQConnectType.WIRELESS,
    )
    private val app = IQApp(BuildConfig.GARMIN_APP_ID)
    private var device: IQDevice? = null
    private var initialized = false

    override val deviceName: String? get() = device?.friendlyName

    override suspend fun connect(onRequest: (ByteArray) -> Unit, onConnectionChanged: (Boolean) -> Unit) {
        disconnect()
        initialize()
        val devices = connectIQ.knownDevices.orEmpty()
        val watch = devices.firstOrNull { connectIQ.getDeviceStatus(it) == IQDevice.IQDeviceStatus.CONNECTED }
            ?: devices.firstOrNull()
            ?: throw IllegalStateException(context.getString(R.string.garmin_no_watch))
        device = watch
        connectIQ.registerForDeviceEvents(watch) { _, status -> onConnectionChanged(status == IQDevice.IQDeviceStatus.CONNECTED) }
        connectIQ.registerForAppEvents(watch, app) { _, _, messages, status ->
            if (status == ConnectIQ.IQMessageStatus.SUCCESS) messages.orEmpty().forEach { onRequest(GarminMessages.toRequest(it)) }
        }
    }

    override suspend fun send(payload: ByteArray): Boolean {
        val watch = device ?: return false
        val message = GarminMessages.fromReply(payload)
        return suspendCancellableCoroutine { cont ->
            try {
                connectIQ.sendMessage(watch, app, message) { _, _, status ->
                    if (cont.isActive) cont.resume(status == ConnectIQ.IQMessageStatus.SUCCESS)
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    override fun disconnect() {
        if (!initialized) return
        runCatching { connectIQ.unregisterAllForEvents() }
        runCatching { connectIQ.shutdown(context.applicationContext) }
        initialized = false
        device = null
    }

    /** Binds to Garmin Connect; fails with a readable message when it is missing or too old. */
    private suspend fun initialize() = suspendCancellableCoroutine { cont ->
        // no automatic dialog: the error is shown in the app's watch card instead
        connectIQ.initialize(context.applicationContext, false, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                initialized = true
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                val message = when (status) {
                    ConnectIQ.IQSdkErrorStatus.GCM_NOT_INSTALLED -> context.getString(R.string.garmin_connect_missing)
                    ConnectIQ.IQSdkErrorStatus.GCM_UPGRADE_NEEDED -> context.getString(R.string.garmin_connect_update)
                    else -> context.getString(R.string.garmin_connect_error, status.name)
                }
                if (cont.isActive) cont.resumeWithException(IllegalStateException(message))
            }

            override fun onSdkShutDown() {
                initialized = false
            }
        })
    }
}
