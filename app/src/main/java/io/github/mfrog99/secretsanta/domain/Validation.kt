package io.github.mfrog99.secretsanta.domain

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val ROOM_CODE = Regex("^[A-Z0-9]{6}$")

const val MIN_PASSWORD_LENGTH = 8
const val MAX_NAME_LENGTH = 50
const val MAX_ROOM_NAME_LENGTH = 60
const val MAX_WISHLIST_LENGTH = 2000

fun emailError(email: String): String? =
    if (EMAIL.matches(email.trim())) null else "Введите корректный email"

fun passwordError(password: String): String? =
    if (password.length >= MIN_PASSWORD_LENGTH) null
    else "Пароль должен быть не короче $MIN_PASSWORD_LENGTH символов"

fun nameError(name: String): String? = when {
    name.isBlank() -> "Введите имя"
    name.trim().length > MAX_NAME_LENGTH -> "Имя длиннее $MAX_NAME_LENGTH символов"
    else -> null
}

fun normalizeRoomCode(code: String): String = code.trim().uppercase()

fun isValidRoomCode(code: String): Boolean = ROOM_CODE.matches(normalizeRoomCode(code))
