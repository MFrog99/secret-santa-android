package io.github.mfrog99.secretsanta.domain

import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException

private val KNOWN_ERRORS = listOf(
    "ROOM_NOT_FOUND" to "Комната с таким кодом не найдена",
    "ROOM_ALREADY_DRAWN" to "Жеребьёвка уже проведена, войти в комнату нельзя",
    "ROOM_NOT_DRAWN" to "Сначала нужно провести жеребьёвку",
    "NOT_ENOUGH_MEMBERS" to "Для жеребьёвки нужно минимум 2 участника",
    "NOT_ROOM_OWNER" to "Это может сделать только владелец комнаты",
    "INVALID_ROOM_NAME" to "Название комнаты: от 1 до $MAX_ROOM_NAME_LENGTH символов",
    "invalid_credentials" to "Неверный email или пароль",
    "invalid login credentials" to "Неверный email или пароль",
    "user_already_exists" to "Пользователь с таким email уже зарегистрирован",
    "already registered" to "Пользователь с таким email уже зарегистрирован",
    "weak_password" to "Слишком простой пароль",
    "email_address_invalid" to "Введите корректный email",
    "over_request_rate_limit" to "Слишком много попыток, подождите немного",
    "over_email_send_rate_limit" to "Слишком много попыток, подождите немного",
)

/** Pure mapping, kept separate so it can be unit-tested without SDK exception types. */
fun messageForServerError(raw: String?): String {
    val text = raw.orEmpty()
    return KNOWN_ERRORS.firstOrNull { (needle, _) -> text.contains(needle, ignoreCase = true) }?.second
        ?: "Что-то пошло не так. Попробуйте ещё раз"
}

fun Throwable.toUserMessage(): String = when (this) {
    is HttpRequestException, is HttpRequestTimeoutException, is IOException ->
        "Нет связи с сервером. Проверьте интернет"
    else -> messageForServerError(message)
}
