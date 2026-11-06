package io.github.mfrog99.secretsanta.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Room(
    val id: String,
    val name: String,
    val code: String,
    @SerialName("owner_id") val ownerId: String,
    val status: String,
) {
    val isDrawn: Boolean get() = status == "drawn"
}

@Serializable
data class ProfileName(
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class Member(
    @SerialName("room_id") val roomId: String,
    @SerialName("user_id") val userId: String,
    val wishlist: String? = null,
    val profiles: ProfileName? = null,
) {
    val displayName: String get() = profiles?.displayName ?: "Участник"
}

@Serializable
data class Assignment(
    @SerialName("room_id") val roomId: String,
    @SerialName("santa_id") val santaId: String,
    @SerialName("recipient_id") val recipientId: String,
    val revealed: Boolean = false,
)
