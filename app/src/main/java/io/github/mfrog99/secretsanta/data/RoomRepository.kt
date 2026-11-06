package io.github.mfrog99.secretsanta.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RoomRepository(private val client: SupabaseClient) {

    /** RLS limits this to rooms the user belongs to. */
    suspend fun myRooms(): List<Room> =
        client.from("rooms")
            .select { order("created_at", Order.DESCENDING) }
            .decodeList()

    suspend fun room(roomId: String): Room =
        client.from("rooms")
            .select { filter { eq("id", roomId) } }
            .decodeSingle()

    suspend fun members(roomId: String): List<Member> =
        client.from("room_members")
            .select(Columns.raw("room_id,user_id,wishlist,profiles(display_name)")) {
                filter { eq("room_id", roomId) }
                order("joined_at", Order.ASCENDING)
            }
            .decodeList()

    /** RLS returns the user's own row, plus the row naming their santa once revealed. */
    suspend fun assignments(roomId: String): List<Assignment> =
        client.from("assignments")
            .select { filter { eq("room_id", roomId) } }
            .decodeList()

    suspend fun createRoom(name: String): Room =
        client.postgrest.rpc("create_room", buildJsonObject { put("p_name", name) }).decodeAs()

    suspend fun joinRoom(code: String): Room =
        client.postgrest.rpc("join_room", buildJsonObject { put("p_code", code) }).decodeAs()

    suspend fun draw(roomId: String) {
        client.postgrest.rpc("draw_room", buildJsonObject { put("p_room", roomId) })
    }

    suspend fun reveal(roomId: String) {
        client.postgrest.rpc("reveal_room", buildJsonObject { put("p_room", roomId) })
    }

    suspend fun saveWishlist(roomId: String, wishlist: String?) {
        val userId = requireNotNull(client.auth.currentUserOrNull()?.id)
        client.from("room_members").update({ set("wishlist", wishlist) }) {
            filter {
                eq("room_id", roomId)
                eq("user_id", userId)
            }
        }
    }

    /**
     * Emits once the subscription is live, then on every change to the room, its members or the
     * assignments the user may read. Consumers reload on each emission, so nothing is missed
     * between the initial load and the subscription going live.
     */
    fun roomEvents(roomId: String): Flow<Unit> = channelFlow {
        // A unique name: several screens of one room can be subscribed at the same time.
        val channel = client.channel("room-$roomId-${UUID.randomUUID()}")
        val changes = merge(
            channel.postgresChangeFlow<PostgresAction>("public") {
                table = "rooms"
                filter("id", FilterOperator.EQ, roomId)
            },
            channel.postgresChangeFlow<PostgresAction>("public") {
                table = "room_members"
                filter("room_id", FilterOperator.EQ, roomId)
            },
            channel.postgresChangeFlow<PostgresAction>("public") {
                table = "assignments"
                filter("room_id", FilterOperator.EQ, roomId)
            },
        )
        changes.onEach { send(Unit) }.launchIn(this)
        try {
            channel.subscribe(blockUntilSubscribed = true)
            send(Unit)
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { client.realtime.removeChannel(channel) }
        }
    }
}
