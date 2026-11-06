package io.github.mfrog99.secretsanta.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mfrog99.secretsanta.data.AppContainer
import io.github.mfrog99.secretsanta.data.Room
import io.github.mfrog99.secretsanta.domain.isValidRoomCode
import io.github.mfrog99.secretsanta.domain.normalizeRoomCode
import io.github.mfrog99.secretsanta.domain.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val rooms: List<Room> = emptyList(),
    val loading: Boolean = true,
    val code: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

class HomeViewModel : ViewModel() {
    private val rooms = AppContainer.roomRepository
    private val auth = AppContainer.authRepository

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    private val _openRoom = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openRoom: SharedFlow<String> = _openRoom

    fun refresh() {
        viewModelScope.launch {
            try {
                val list = rooms.myRooms()
                _state.update { it.copy(rooms = list, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun onCode(value: String) = _state.update {
        it.copy(code = normalizeRoomCode(value).take(6), error = null)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun createRoom(name: String) = enterRoom { rooms.createRoom(name.trim()) }

    fun joinRoom() {
        val code = _state.value.code
        if (!isValidRoomCode(code)) {
            _state.update { it.copy(error = "Код состоит из 6 букв и цифр") }
            return
        }
        enterRoom { rooms.joinRoom(code) }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                auth.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }

    private fun enterRoom(action: suspend () -> Room) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val room = action()
                _state.update { it.copy(busy = false, code = "") }
                _openRoom.emit(room.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.toUserMessage()) }
            }
        }
    }
}
