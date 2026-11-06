package io.github.mfrog99.secretsanta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AuthScreen(vm: AuthViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Text("🎁", style = MaterialTheme.typography.displayLarge)
            Text("Тайный Санта", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (state.isRegister) "Создайте аккаунт" else "Войдите в аккаунт",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (state.isRegister) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::onName,
                    label = { Text("Имя") },
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = state.email,
                onValueChange = vm::onEmail,
                label = { Text("Email") },
                isError = state.emailError != null,
                supportingText = state.emailError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = vm::onPassword,
                label = { Text("Пароль") },
                isError = state.passwordError != null,
                supportingText = state.passwordError?.let { { Text(it) } },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = vm::submit,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (state.isRegister) "Зарегистрироваться" else "Войти")
                }
            }
            TextButton(onClick = vm::toggleMode, enabled = !state.busy) {
                Text(if (state.isRegister) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться")
            }
        }
    }
}
