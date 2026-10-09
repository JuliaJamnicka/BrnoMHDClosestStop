package io.github.juliajamnicka.brnomhd.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class TransitRepositoryTest {
    private val server = MockWebServer()
    private var now = 1_000_000L
    private val body = """{"t":1,"g":"U1073N2860","stop":"Česká","p":"U1073Z1","dir":"Komárov","opp":"U1073Z2","d":20,
        "pl":[],"dep":[{"l":"6","m":"T","h":"Starý Lískovec","e":100,"s":100,"dl":0,"lv":1},
                       {"l":"4","m":"T","h":"Náměstí Míru","e":200,"s":200,"dl":0,"lv":1}],"extra":"ignored"}"""

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun repository(location: GeoPoint? = GeoPoint(49.1978, 16.6056), lists: List<StopList> = emptyList()): TransitRepository {
        val api = ApiClient(config = { ApiClient.ApiConfig(server.url("/").toString(), "secret") })
        return TransitRepository(api, { location }, clock = { now }, stopLists = { lists })
    }

    @Test
    fun showsAStopListWhenOneOfItsStopsIsNearby() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"t":1,"dep":[{"l":"12","m":"T","h":"Komárov","e":100,"s":100,"dl":0,"lv":1,"p":"U1Z2","sn":"Česká"}]}""",
            ),
        )
        val list = StopList(
            "home",
            "Domů",
            listOf(
                StopListEntry("U1Z2", "Česká", "Centrum", 49.1979, 16.6059),
                StopListEntry("U7Z1", "Grohova", "Úvoz", 49.2010, 16.5990),
            ),
        )
        val board = repository(lists = listOf(list)).home(4)
        assertEquals("/v1/board?platforms=U1Z2%2CU7Z1&n=4", server.takeRequest().path)
        assertEquals("Domů", board.stop)
        assertEquals("home", board.listId)
        assertEquals("Česká", board.dep.single().sn)
    }

    @Test
    fun sendsKeyAndPositionAndCachesHomeFor20Seconds() = runTest {
        server.enqueue(MockResponse().setBody(body))
        server.enqueue(MockResponse().setBody(body))
        val repo = repository()

        assertEquals("Česká", repo.home(2).stop)
        val request = server.takeRequest()
        assertEquals("secret", request.getHeader("x-api-key"))
        assertEquals("/v1/home?lat=49.1978&lon=16.6056&n=2", request.path)

        now += 10_000
        repo.home(2)
        assertEquals(1, server.requestCount)

        now += 15_000
        repo.home(2)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun reportsRejectedKeyAndMissingLocation() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val e = runCatching { repository().home(4) }.exceptionOrNull() as ApiException
        assertEquals(ApiException.Kind.AUTH, e.kind)
        assertThrows(NoLocationException::class.java) { kotlinx.coroutines.runBlocking { repository(location = null).home(4) } }
    }
}
