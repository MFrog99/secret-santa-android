package io.github.mfrog99.secretsanta.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mfrog99.secretsanta.data.Member
import io.github.mfrog99.secretsanta.data.Room

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    onBack: () -> Unit,
    onWishlist: () -> Unit,
    onAssignment: () -> Unit,
    vm: RoomViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDraw by rememberSaveable { mutableStateOf(false) }
    var confirmReveal by rememberSaveable { mutableStateOf(false) }

    // Realtime can lag behind after the app was in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.room?.name ?: "Комната") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val room = state.room
        if (room == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator() else Text("Не удалось загрузить комнату")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusCard(room, state.members.size) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (room.isDrawn) {
                        Button(onClick = onAssignment, modifier = Modifier.fillMaxWidth()) {
                            Text("Показать мою цель")
                        }
                    } else if (state.isOwner) {
                        Button(
                            onClick = { confirmDraw = true },
                            enabled = !state.busy && state.members.size >= 2,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Начать жеребьёвку") }
                        if (state.members.size < 2) {
                            Text(
                                "Нужно минимум 2 участника. Поделитесь кодом с друзьями",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            "Ждём, пока владелец начнёт жеребьёвку",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onWishlist, modifier = Modifier.fillMaxWidth()) {
                        Text("Моё пожелание")
                    }
                    if (room.isDrawn && state.isOwner && !state.allRevealed) {
                        TextButton(
                            onClick = { confirmReveal = true },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Раскрыть всех Сант") }
                    }
                }
            }

            item {
                HorizontalDivider()
                Text(
                    "Участники: ${state.members.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(state.members, key = { it.userId }) { member ->
                MemberRow(member, isOwner = member.userId == room.ownerId, isMe = member.userId == state.currentUserId)
            }
        }
    }

    if (confirmDraw) {
        AlertDialog(
            onDismissRequest = { confirmDraw = false },
            title = { Text("Начать жеребьёвку?") },
            text = { Text("Отменить её нельзя, и новые участники больше не смогут войти в комнату.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDraw = false
                    vm.draw()
                }) { Text("Начать") }
            },
            dismissButton = { TextButton(onClick = { confirmDraw = false }) { Text("Отмена") } },
        )
    }
    if (confirmReveal) {
        AlertDialog(
            onDismissRequest = { confirmReveal = false },
            title = { Text("Раскрыть всех Сант?") },
            text = { Text("Каждый участник увидит, кто его Санта. Отменить это нельзя.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReveal = false
                    vm.reveal()
                }) { Text("Раскрыть") }
            },
            dismissButton = { TextButton(onClick = { confirmReveal = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun StatusCard(room: Room, memberCount: Int) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (room.isDrawn) "Жеребьёвка проведена" else "Идёт набор участников",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Код комнаты", style = MaterialTheme.typography.labelMedium)
            Text(room.code, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
            if (!room.isDrawn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { copyCode(context, room.code) }) { Text("Скопировать") }
                    TextButton(onClick = { shareCode(context, room) }) { Text("Поделиться") }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: Member, isOwner: Boolean, isMe: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val suffix = listOfNotNull("вы".takeIf { isMe }, "владелец".takeIf { isOwner })
            .joinToString(", ")
            .let { if (it.isEmpty()) "" else " ($it)" }
        Text(member.displayName + suffix, style = MaterialTheme.typography.bodyLarge)
        Text(
            if (member.wishlist.isNullOrBlank()) "Пожелание не указано" else "Пожелание указано",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun copyCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Код комнаты", code))
    Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
}

private fun shareCode(context: Context, room: Room) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Присоединяйся к Тайному Санте «${room.name}»! Код комнаты: ${room.code}")
    }
    context.startActivity(Intent.createChooser(send, "Поделиться кодом"))
}
