package io.github.juliajamnicka.brnomhd.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Live state of the watch link, shown in the phone app. */
object WatchStatus {
    data class State(
        val running: Boolean = false,
        val deviceName: String? = null,
        val connected: Boolean = false,
        val lastRequestAt: Long? = null,
        val lastError: String? = null,
    )

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable

    internal fun update(change: (State) -> State) = mutable.update(change)
}
