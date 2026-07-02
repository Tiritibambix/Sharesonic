package com.tiritibambix.sharesonic.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Velvet server URL / account / connection-test sub-screen, opened from the Settings menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToBrowser: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server") },
                navigationIcon = {
                    // Keep the hamburger glyph here too — reached via the drawer,
                    // tapping it again exits this screen back to the browser.
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        ServerSettingsContent(
            viewModel = viewModel,
            onNavigateToBrowser = onNavigateToBrowser,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun ServerSettingsContent(
    viewModel: SettingsViewModel,
    onNavigateToBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var serverUrl by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var username  by remember(settings.username)  { mutableStateOf(settings.username) }
    var password  by remember(settings.password)  { mutableStateOf(settings.password) }

    Column(
        modifier = modifier
            .padding(24.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Velvet server URL") },
            placeholder = { Text("https://velvet.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { viewModel.testConnection(serverUrl, username, password) },
                enabled = connectionState !is ConnectionState.Testing,
                modifier = Modifier.weight(1f)
            ) {
                if (connectionState is ConnectionState.Testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Test")
                }
            }

            Button(
                onClick = {
                    val looksValid = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
                    if (looksValid) {
                        // Navigate from save()'s onSaved hook — i.e. only once the
                        // DataStore write has actually completed. Navigating right
                        // after *calling* save() (which only launches a coroutine
                        // and returns immediately) raced that write: the freshly
                        // created FolderBrowserViewModel read settingsRepo.settings
                        // before the new values landed and showed
                        // "Server not configured" — fixed by waiting for the real
                        // completion signal instead of a fire-and-forget call.
                        viewModel.save(serverUrl, username, password) { onNavigateToBrowser() }
                    } else {
                        viewModel.save(serverUrl, username, password)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
        }

        when (val state = connectionState) {
            is ConnectionState.Success -> {
                Text(
                    "Connection successful",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            is ConnectionState.Failure -> {
                Text(
                    "Failed: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            else -> {}
        }
    }
}
