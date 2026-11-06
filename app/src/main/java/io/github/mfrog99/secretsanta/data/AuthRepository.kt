package io.github.mfrog99.secretsanta.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository(private val client: SupabaseClient) {

    /** The session is persisted by supabase-kt and restored here on app start. */
    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    val currentUserId: String? get() = client.auth.currentUserOrNull()?.id

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String, displayName: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject { put("display_name", displayName) }
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
    }
}
