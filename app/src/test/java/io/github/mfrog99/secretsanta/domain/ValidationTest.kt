package io.github.mfrog99.secretsanta.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    @Test
    fun `email must look like an address`() {
        assertNull(emailError("anna@example.com"))
        assertNull(emailError("  anna@example.com "))
        assertNotNull(emailError("anna"))
        assertNotNull(emailError("anna@example"))
        assertNotNull(emailError("an na@example.com"))
    }

    @Test
    fun `password needs the minimum length`() {
        assertNotNull(passwordError("1234567"))
        assertNull(passwordError("12345678"))
    }

    @Test
    fun `name must be present and short`() {
        assertNotNull(nameError("   "))
        assertNotNull(nameError("x".repeat(MAX_NAME_LENGTH + 1)))
        assertNull(nameError("Анна"))
    }

    @Test
    fun `room code is normalized and checked`() {
        assertEquals("ABC123", normalizeRoomCode(" abc123 "))
        assertTrue(isValidRoomCode("abc123"))
        assertFalse(isValidRoomCode("ABC12"))
        assertFalse(isValidRoomCode("ABC-12"))
        assertFalse(isValidRoomCode("АБВ123")) // Cyrillic look-alikes
    }

    @Test
    fun `server errors become readable messages`() {
        assertEquals("Комната с таким кодом не найдена", messageForServerError("ROOM_NOT_FOUND\nURL: ..."))
        assertEquals("Неверный email или пароль", messageForServerError("Invalid login credentials"))
        assertEquals("Что-то пошло не так. Попробуйте ещё раз", messageForServerError(null))
    }
}
