package io.github.juliajamnicka.fcil.wear

/** The kinds of watch the app can serve; the user picks one when connecting. */
enum class WatchKind { HUAWEI, GARMIN }

/**
 * A link to the watch app: requests come in as the JSON of docs/SPEC.md 6 and replies go out as the
 * JSON [WatchProtocol] builds, whatever the watch maker's transport does with them.
 */
interface WatchLink {
    /** The watch's name once connected. */
    val deviceName: String?

    /** Finds the paired watch and starts listening for its requests. */
    suspend fun connect(onRequest: (ByteArray) -> Unit, onConnectionChanged: (Boolean) -> Unit)

    /** Sends a reply; returns true when the watch acknowledged it. */
    suspend fun send(payload: ByteArray): Boolean

    fun disconnect()
}
