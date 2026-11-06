package io.github.mfrog99.secretsanta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mfrog99.secretsanta.domain.MAX_WISHLIST_LENGTH

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(onBack: () -> Unit, vm: WishlistViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.saved.collect { onBack() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Моё пожелание") },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Что вы хотели бы получить? Пожелание увидит ваш Санта и другие участники комнаты.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.text,
                onValueChange = { vm.onText(it.take(MAX_WISHLIST_LENGTH)) },
                label = { Text("Пожелание") },
                supportingText = { Text("${state.text.length} / $MAX_WISHLIST_LENGTH") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::save, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Сохранить")
                }
            }
        }
    }
}
