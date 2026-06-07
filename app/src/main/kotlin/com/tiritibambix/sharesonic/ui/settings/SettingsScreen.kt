package com.tiritibambix.sharesonic.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.data.settings.AppTheme
import com.tiritibambix.sharesonic.ui.autodj.AutoDjSettingsContent
import com.tiritibambix.sharesonic.ui.autodj.AutoDjSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    autoDjViewModel: AutoDjSettingsViewModel,
    onNavigateToBrowser: () -> Unit,
    miniPlayerVisible: Boolean = false
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Settings") })
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Serveur") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Auto-DJ") }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> ServerSettingsContent(
                viewModel = viewModel,
                onNavigateToBrowser = onNavigateToBrowser,
                modifier = Modifier.padding(padding)
            )
            else -> AutoDjSettingsContent(
                viewModel = autoDjViewModel,
                modifier = Modifier.padding(padding),
                miniPlayerVisible = miniPlayerVisible
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerSettingsContent(
    viewModel: SettingsViewModel,
    onNavigateToBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()

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
            label = { Text("mStream server URL") },
            placeholder = { Text("https://mstream.example.com") },
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
                    viewModel.save(serverUrl, username, password)
                    onNavigateToBrowser()
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

        HorizontalDivider()

        Text(
            "Theme",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Velvet est le thème par défaut, assorti à mStream.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                AppTheme.VELVET to "Velvet",
                AppTheme.DARK to "Dark",
                AppTheme.LIGHT to "Light"
            )
            options.forEachIndexed { index, (theme, label) ->
                SegmentedButton(
                    selected = appTheme == theme,
                    onClick = { viewModel.setAppTheme(theme) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(label)
                }
            }
        }
    }
}
