package io.github.mfrog99.secretsanta.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mfrog99.secretsanta.data.AppContainer
import io.github.mfrog99.secretsanta.data.Member
import io.github.mfrog99.secretsanta.data.Room
import io.github.mfrog99.secretsanta.domain.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val ROOM_ID_ARG = "roomId"

/** Reloads on every realtime event for as long as the scope lives, reconnecting after failures. */
private fun CoroutineScope.reloadOnRoomEvents(roomId: String, reload: suspend () -> Unit) {
    launch {
        AppContainer.roomRepository.roomEvents(roomId)
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) return@retryWhen false
                delay((1_000L shl attempt.coerceAtMost(5).toInt()))
                true
            }
            .collect { reload() }
    }
}

data class RoomUiState(
    val room: Room? = null,
    val members: List<Member> = emptyList(),
    val currentUserId: String? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val allRevealed: Boolean = false,
    val error: String? = null,
) {
    val isOwner: Boolean get() = room != null && room.ownerId == currentUserId
}

class RoomViewModel(savedState: SavedStateHandle) : ViewModel() {
    private val roomId: String = checkNotNull(savedState[ROOM_ID_ARG])
    private val rooms = AppContainer.roomRepository

    private val _state = MutableStateFlow(RoomUiState(currentUserId = AppContainer.authRepository.currentUserId))
    val state: StateFlow<RoomUiState> = _state

    init {
        refresh()
        viewModelScope.reloadOnRoomEvents(roomId) { load() }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun draw() = runAction { rooms.draw(roomId) }

    fun reveal() = runAction { rooms.reveal(roomId) }

    private suspend fun load() {
        try {
            val room = rooms.room(roomId)
            val members = rooms.members(roomId)
            val revealed = room.isDrawn && rooms.assignments(roomId).any { it.revealed }
            _state.update {
                it.copy(room = room, members = members, allRevealed = revealed, loading = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.toUserMessage()) }
        }
    }

    private fun runAction(action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                action()
                load()
                _state.update { it.copy(busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.toUserMessage()) }
            }
        }
    }
}

data class WishlistUiState(
    val text: String = "",
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
)

class WishlistViewModel(savedState: SavedStateHandle) : ViewModel() {
    private val roomId: String = checkNotNull(savedState[ROOM_ID_ARG])
    private val rooms = AppContainer.roomRepository
    private val userId = AppContainer.authRepository.currentUserId

    private val _state = MutableStateFlow(WishlistUiState())
    val state: StateFlow<WishlistUiState> = _state

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved

    init {
        viewModelScope.launch {
            try {
                val own = rooms.members(roomId).firstOrNull { it.userId == userId }
                _state.update { it.copy(text = own?.wishlist.orEmpty(), loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun onText(value: String) = _state.update { it.copy(text = value, error = null) }

    fun save() {
        val s = _state.value
        if (s.busy || s.loading) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                rooms.saveWishlist(roomId, s.text.trim().ifEmpty { null })
                _state.update { it.copy(busy = false) }
                _saved.emit(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.toUserMessage()) }
            }
        }
    }
}

data class AssignmentUiState(
    val recipient: Member? = null,
    val santa: Member? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class AssignmentViewModel(savedState: SavedStateHandle) : ViewModel() {
    private val roomId: String = checkNotNull(savedState[ROOM_ID_ARG])
    private val rooms = AppContainer.roomRepository
    private val userId = AppContainer.authRepository.currentUserId

    private val _state = MutableStateFlow(AssignmentUiState())
    val state: StateFlow<AssignmentUiState> = _state

    init {
        viewModelScope.launch { load() }
        viewModelScope.reloadOnRoomEvents(roomId) { load() }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val assignments = rooms.assignments(roomId)
            val members = rooms.members(roomId).associateBy { it.userId }
            val recipient = assignments.firstOrNull { it.santaId == userId }?.let { members[it.recipientId] }
            val santa = assignments
                .firstOrNull { it.recipientId == userId && it.revealed }
                ?.let { members[it.santaId] }
            _state.update { it.copy(recipient = recipient, santa = santa, loading = false, error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.toUserMessage()) }
        }
    }
}
