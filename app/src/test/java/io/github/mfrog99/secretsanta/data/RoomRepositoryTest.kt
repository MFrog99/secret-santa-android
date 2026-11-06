package io.github.mfrog99.secretsanta.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the real SDK over a fake HTTP layer: checks request shapes and response decoding. */
class RoomRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(status: HttpStatusCode = HttpStatusCode.OK, body: String): RoomRepository {
        val engine = MockEngine { request ->
            requests += request
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = createSupabaseClient("https://test.supabase.co", "anon-key") {
            httpEngine = engine
            defaultSerializer = KotlinXSerializer(lenientJson)
            install(Postgrest)
        }
        return RoomRepository(client)
    }

    private fun bodyOf(request: HttpRequestData): String =
        (request.body as? TextContent)?.text
            ?: (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
            ?: error("Unexpected body type: ${request.body::class}")

    // What PostgREST returns for a function declared `returns public.rooms`: one object.
    private val roomJson = """
        {"id":"r1","name":"Офис","code":"ABC234","owner_id":"u1","status":"draft",
         "created_at":"2026-11-06T13:00:00+00:00"}
    """.trimIndent()

    @Test
    fun `createRoom calls the rpc and decodes a single room`() = runTest {
        val room = repository(body = roomJson).createRoom("Офис")

        assertEquals("ABC234", room.code)
        assertEquals("u1", room.ownerId)
        assertEquals(false, room.isDrawn)
        assertTrue(requests.single().url.encodedPath.endsWith("/rest/v1/rpc/create_room"))
        assertEquals("""{"p_name":"Офис"}""", bodyOf(requests.single()))
    }

    @Test
    fun `joinRoom sends the code`() = runTest {
        val room = repository(body = roomJson).joinRoom("ABC234")

        assertEquals("r1", room.id)
        assertTrue(requests.single().url.encodedPath.endsWith("/rest/v1/rpc/join_room"))
        assertEquals("""{"p_code":"ABC234"}""", bodyOf(requests.single()))
    }

    @Test
    fun `draw copes with the empty response of a void function`() = runTest {
        repository(HttpStatusCode.NoContent, "").draw("r1")

        assertTrue(requests.single().url.encodedPath.endsWith("/rest/v1/rpc/draw_room"))
        assertEquals("""{"p_room":"r1"}""", bodyOf(requests.single()))
    }

    @Test
    fun `members decode the embedded profile`() = runTest {
        val members = repository(
            body = """[
                {"room_id":"r1","user_id":"u1","wishlist":"Книгу","profiles":{"display_name":"Анна"}},
                {"room_id":"r1","user_id":"u2","wishlist":null,"profiles":{"display_name":"Борис"}}
            ]""",
        ).members("r1")

        assertEquals(listOf("Анна", "Борис"), members.map { it.displayName })
        assertEquals("Книгу", members[0].wishlist)
        val url = requests.single().url
        assertEquals("eq.r1", url.parameters["room_id"])
        assertEquals("room_id,user_id,wishlist,profiles(display_name)", url.parameters["select"])
        assertTrue(url.parameters["order"].orEmpty().startsWith("joined_at.asc"))
    }

    @Test
    fun `myRooms ignores columns the app does not use`() = runTest {
        val rooms = repository(body = "[$roomJson]").myRooms()

        assertEquals(listOf("Офис"), rooms.map { it.name })
    }

    @Test
    fun `assignments decode`() = runTest {
        val rows = repository(
            body = """[{"room_id":"r1","santa_id":"u1","recipient_id":"u2","revealed":false}]""",
        ).assignments("r1")

        assertEquals("u2", rows.single().recipientId)
        assertEquals(false, rows.single().revealed)
    }

    @Test(expected = Exception::class)
    fun `server errors surface as exceptions`() = runTest {
        repository(
            HttpStatusCode.BadRequest,
            """{"code":"P0001","message":"ROOM_NOT_FOUND","details":null,"hint":null}""",
        ).joinRoom("NOPE00")
    }
}
