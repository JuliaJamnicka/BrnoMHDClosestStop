package io.github.juliajamnicka.brnomhd.wear

import io.github.juliajamnicka.brnomhd.data.ApiException
import io.github.juliajamnicka.brnomhd.data.DeparturesResponse
import io.github.juliajamnicka.brnomhd.data.NearbyResponse
import io.github.juliajamnicka.brnomhd.data.NoLocationException
import io.github.juliajamnicka.brnomhd.data.TransitSource
import io.github.juliajamnicka.brnomhd.data.VehiclesResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestHandlerTest {
    private class FakeSource(private val failure: Exception? = null) : TransitSource {
        var lastCount = 0
        var lastPlatform: String? = null
        private fun response(p: String) = DeparturesResponse(t = 1, g = "G", stop = "Česká", p = p, dir = "Komárov")
        override suspend fun home(n: Int): DeparturesResponse {
            failure?.let { throw it }
            lastCount = n
            return response("U1073Z1")
        }
        override suspend fun departures(platform: String, n: Int): DeparturesResponse {
            failure?.let { throw it }
            lastPlatform = platform
            lastCount = n
            return response(platform)
        }
        override suspend fun nearby() = NearbyResponse(1, emptyList())
        override suspend fun vehicles() = VehiclesResponse(1, emptyList())
    }

    private fun handler(source: TransitSource) = RequestHandler(source, departureCount = { 3 }, language = { "cs" })

    @Test
    fun answersHomeWithConfiguredCount() = runTest {
        val source = FakeSource()
        val reply = handler(source).handle("""{"c":"home"}""".toByteArray()).decodeToString()
        assertTrue(reply.startsWith("""{"c":"dep","lg":"cs""""))
        assertEquals(3, source.lastCount)
    }

    @Test
    fun passesThePlatformOfTheReverseButton() = runTest {
        val source = FakeSource()
        handler(source).handle("""{"c":"dep","p":"U1073Z2"}""".toByteArray())
        assertEquals("U1073Z2", source.lastPlatform)
    }

    @Test
    fun mapsFailuresToWatchErrorCodes() = runTest {
        suspend fun errorFor(e: Exception) = handler(FakeSource(e)).handle("""{"c":"home"}""".toByteArray()).decodeToString()
        assertEquals("""{"c":"err","lg":"cs","e":"noloc"}""", errorFor(NoLocationException()))
        assertEquals("""{"c":"err","lg":"cs","e":"net"}""", errorFor(ApiException(ApiException.Kind.NETWORK, "x")))
        assertEquals("""{"c":"err","lg":"cs","e":"auth"}""", errorFor(ApiException(ApiException.Kind.AUTH, "x")))
        assertEquals("""{"c":"err","lg":"cs","e":"srv"}""", errorFor(IllegalStateException("boom")))
        assertEquals("""{"c":"err","lg":"cs","e":"req"}""", handler(FakeSource()).handle("??".toByteArray()).decodeToString())
    }
}
