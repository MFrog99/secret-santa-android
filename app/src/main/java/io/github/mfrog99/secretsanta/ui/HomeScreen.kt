package io.github.mfrog99.secretsanta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mfrog99.secretsanta.data.Room
import io.github.mfrog99.secretsanta.domain.MAX_ROOM_NAME_LENGTH

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenRoom: (String) -> Unit, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    LaunchedEffect(Unit) { vm.openRoom.collect(onOpenRoom) }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои комнаты") },
                actions = {
                    IconButton(onClick = vm::signOut) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Выйти")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Создать комнату") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.code,
                    onValueChange = vm::onCode,
                    label = { Text("Код комнаты") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = vm::joinRoom, enabled = !state.busy && state.code.isNotEmpty()) {
                    Text("Войти")
                }
            }

            when {
                state.loading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }

                state.rooms.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🎄", style = MaterialTheme.typography.displayMedium)
                    Text(
                        "Пока нет комнат. Создайте свою или введите код от друзей",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.rooms, key = { it.id }) { room ->
                        RoomCard(room, onClick = { onOpenRoom(room.id) })
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateRoomDialog(
            onDismiss = { showCreate = false },
            onCreate = {
                showCreate = false
                vm.createRoom(it)
            },
        )
    }
}

@Composable
private fun RoomCard(room: Room, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(room.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Код ${room.code} · " + if (room.isDrawn) "жеребьёвка проведена" else "идёт набор",
                fontFamily = FontFamily.Default,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreateRoomDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая комната") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_ROOM_NAME_LENGTH) },
                label = { Text("Название") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
