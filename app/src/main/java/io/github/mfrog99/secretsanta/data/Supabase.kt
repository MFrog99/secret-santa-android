package io.github.mfrog99.secretsanta.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.mfrog99.secretsanta.BuildConfig
import kotlinx.serialization.json.Json

/** A new server column must not break already installed APKs. */
val lenientJson = Json { ignoreUnknownKeys = true }

/** Single app-wide client and repositories; the app is small enough not to need a DI framework. */
object AppContainer {
    private val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            defaultSerializer = KotlinXSerializer(lenientJson)
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    val authRepository: AuthRepository by lazy { AuthRepository(client) }
    val roomRepository: RoomRepository by lazy { RoomRepository(client) }
}
