package io.github.juliajamnicka.fcil.wear

import io.github.juliajamnicka.fcil.data.ApiException
import io.github.juliajamnicka.fcil.data.NoLocationException
import io.github.juliajamnicka.fcil.data.OutOfRangeException
import io.github.juliajamnicka.fcil.data.TransitSource
import io.github.juliajamnicka.fcil.wear.WatchProtocol.Error
import io.github.juliajamnicka.fcil.wear.WatchProtocol.Request
import kotlinx.coroutines.CancellationException

/** Turns a raw watch message into the reply bytes; never throws. */
class RequestHandler(
    private val source: TransitSource,
    private val departureCount: suspend () -> Int,
    private val language: () -> String,
    /** The departure time setting: clock times instead of minutes. */
    private val clockTimes: suspend () -> Boolean = { false },
) {

    suspend fun handle(raw: ByteArray): ByteArray {
        val lang = language()
        val request = WatchProtocol.parseRequest(raw) ?: return WatchProtocol.error(Error.BAD_REQUEST, lang)
        return try {
            when (request) {
                Request.Home -> WatchProtocol.departures(source.home(departureCount()), lang, clockTimes())
                is Request.Departures -> WatchProtocol.departures(source.departures(request.platform, departureCount()), lang, clockTimes())
                is Request.Platforms -> WatchProtocol.platforms(source.departures(request.platform, 1), lang)
                Request.Nearby -> WatchProtocol.nearby(source.nearby(), lang)
                is Request.Vehicles -> WatchProtocol.vehicles(source.vehicles(request.radius), lang, request.mine)
            }
        } catch (e: NoLocationException) {
            WatchProtocol.error(Error.NO_LOCATION, lang)
        } catch (e: OutOfRangeException) {
            WatchProtocol.error(Error.OUT_OF_RANGE, lang)
        } catch (e: ApiException) {
            WatchProtocol.error(
                when (e.kind) {
                    ApiException.Kind.NETWORK -> Error.NETWORK
                    ApiException.Kind.AUTH -> Error.AUTH
                    ApiException.Kind.SERVER -> Error.SERVER
                },
                lang,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WatchProtocol.error(Error.SERVER, lang)
        }
    }
}
