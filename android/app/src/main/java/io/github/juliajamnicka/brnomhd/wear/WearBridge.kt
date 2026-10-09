package io.github.juliajamnicka.brnomhd.wear

import android.content.Context
import com.huawei.hmf.tasks.Task
import com.huawei.wearengine.HiWear
import com.huawei.wearengine.common.WearEngineErrorCode
import com.huawei.wearengine.device.Device
import com.huawei.wearengine.monitor.MonitorItem
import com.huawei.wearengine.monitor.MonitorListener
import com.huawei.wearengine.p2p.Message
import com.huawei.wearengine.p2p.Receiver
import com.huawei.wearengine.p2p.SendCallback
import io.github.juliajamnicka.brnomhd.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wear Engine P2P link to the watch app. Needs the Huawei Health app with the watch paired,
 * the Wear Engine permission granted (see AuthClient in MainActivity) and the watch app's
 * package name + signing fingerprint (BuildConfig.WATCH_PACKAGE / WATCH_FINGERPRINT).
 */
class WearBridge(context: Context) {
    private val deviceClient = HiWear.getDeviceClient(context)
    private val monitorClient = HiWear.getMonitorClient(context)
    private val p2pClient = HiWear.getP2pClient(context)
        .setPeerPkgName(BuildConfig.WATCH_PACKAGE)
        .setPeerFingerPrint(BuildConfig.WATCH_FINGERPRINT)

    private var device: Device? = null
    private var receiver: Receiver? = null
    private var monitor: MonitorListener? = null

    val deviceName: String? get() = device?.name

    /** Finds the paired watch and starts listening for its messages. */
    suspend fun connect(onMessage: (ByteArray) -> Unit, onConnectionChanged: (Boolean) -> Unit) {
        disconnect()
        val devices = deviceClient.bondedDevices.await()
        val watch = devices.firstOrNull { it.isConnected } ?: devices.firstOrNull()
            ?: throw IllegalStateException("No watch paired in Huawei Health")
        device = watch

        // The lite-wearable SDK sends text (Builder.setDescription); accept it as data or description.
        val r = Receiver { message ->
            if (message.type != Message.MESSAGE_TYPE_FILE) {
                (message.data ?: message.description?.encodeToByteArray())?.let(onMessage)
            }
        }
        p2pClient.registerReceiver(watch, r).await()
        receiver = r

        val m = MonitorListener { _, _, data -> onConnectionChanged(data?.asBool() == true) }
        runCatching { monitorClient.register(watch, MonitorItem.MONITOR_ITEM_CONNECTION, m).await() }
            .onSuccess { monitor = m }
    }

    /** Sends a reply; returns true when the watch acknowledged it. */
    suspend fun send(payload: ByteArray): Boolean {
        val watch = device ?: return false
        val message = Message.Builder().setPayload(payload).build()
        return suspendCancellableCoroutine { cont ->
            p2pClient.send(watch, message, object : SendCallback {
                override fun onSendResult(resultCode: Int) {
                    if (cont.isActive) cont.resume(resultCode == WearEngineErrorCode.ERROR_CODE_COMM_SUCCESS)
                }
                override fun onSendProgress(progress: Long) {}
            }).addOnFailureListener { if (cont.isActive) cont.resume(false) }
        }
    }

    fun disconnect() {
        receiver?.let { runCatching { p2pClient.unregisterReceiver(it) } }
        monitor?.let { runCatching { monitorClient.unregister(it) } }
        receiver = null
        monitor = null
        device = null
    }
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
}
