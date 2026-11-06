package io.github.mfrog99.secretsanta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentScreen(onBack: () -> Unit, vm: AssignmentViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Моя цель") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            val recipient = state.recipient
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🎁 Вы дарите подарок", style = MaterialTheme.typography.labelLarge)
                    if (recipient == null) {
                        Text("Назначение пока не найдено")
                    } else {
                        Text(recipient.displayName, style = MaterialTheme.typography.headlineMedium)
                        Text("Пожелание", style = MaterialTheme.typography.labelMedium)
                        Text(
                            recipient.wishlist?.takeIf { it.isNotBlank() }
                                ?: "Участник пока ничего не указал",
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🎅 Ваш Санта", style = MaterialTheme.typography.labelLarge)
                    val santa = state.santa
                    if (santa == null) {
                        Text("Пока тайна. Имя появится, когда владелец комнаты раскроет всех Сант")
                    } else {
                        Text(santa.displayName, style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        }
    }
}
