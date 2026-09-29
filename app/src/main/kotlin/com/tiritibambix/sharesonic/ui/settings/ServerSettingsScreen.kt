package com.tiritibambix.sharesonic.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.utils.LocalIsTV
import com.tiritibambix.sharesonic.utils.TvCircleShape
import com.tiritibambix.sharesonic.utils.TvInitialFocus
import com.tiritibambix.sharesonic.utils.TvPillShape
import com.tiritibambix.sharesonic.utils.tvFocusRing
import com.tiritibambix.sharesonic.utils.tvKeyboardOptions
import com.tiritibambix.sharesonic.utils.tvTextFieldKeys

/** Velvet server URL / account / connection-test sub-screen, opened from the Settings menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    miniPlayerVisible: Boolean = false,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tvFocusRing(LocalIsTV.current, TvCircleShape)) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.common_menu))
                    }
                }
            )
        }
    ) { padding ->
        ServerSettingsContent(
            viewModel = viewModel,
            onNavigateToBrowser = onNavigateToBrowser,
            modifier = Modifier.padding(padding),
            miniPlayerVisible = miniPlayerVisible,
        )
    }
}

@Composable
private fun ServerSettingsContent(
    viewModel: SettingsViewModel,
    miniPlayerVisible: Boolean = false,
    onNavigateToBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var serverUrl by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var username  by remember(settings.username)  { mutableStateOf(settings.username) }
    var password  by remember(settings.password)  { mutableStateOf(settings.password) }

    // TV: start on the URL field; moving across the fields doesn't pop the
    // keyboard (OK opens it) and the keyboard's Next key walks URL → user →
    // password. Up / Down always leave a field (HDMI-CEC remotes included).
    val isTV = LocalIsTV.current
    val tvFirst = remember { FocusRequester() }
    TvInitialFocus(isTV, tvFirst)
    fun tvOptions(base: KeyboardOptions, action: ImeAction): KeyboardOptions =
        if (isTV) base.copy(imeAction = action).tvKeyboardOptions(true) else base

    Column(
        modifier = modifier
            .padding(24.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text(stringResource(R.string.server_url_label)) },
            placeholder = { Text(stringResource(R.string.server_url_placeholder)) },
            singleLine = true,
            keyboardOptions = tvOptions(KeyboardOptions(keyboardType = KeyboardType.Uri), ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isTV) Modifier.focusRequester(tvFirst) else Modifier)
                .tvTextFieldKeys(isTV)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.server_username)) },
            singleLine = true,
            keyboardOptions = tvOptions(KeyboardOptions.Default, ImeAction.Next),
            modifier = Modifier.fillMaxWidth().tvTextFieldKeys(isTV)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.server_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = tvOptions(KeyboardOptions(keyboardType = KeyboardType.Password), ImeAction.Done),
            modifier = Modifier.fillMaxWidth().tvTextFieldKeys(isTV)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // TV: stays enabled while testing (presses are ignored) — a button
            // that disables itself while focused drops the D-pad focus.
            OutlinedButton(
                onClick = {
                    if (connectionState !is ConnectionState.Testing)
                        viewModel.testConnection(serverUrl, username, password)
                },
                enabled = isTV || connectionState !is ConnectionState.Testing,
                modifier = Modifier.weight(1f).tvFocusRing(isTV, TvPillShape)
            ) {
                if (connectionState is ConnectionState.Testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.server_test))
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
                modifier = Modifier.weight(1f).tvFocusRing(isTV, TvPillShape)
            ) {
                Text(stringResource(R.string.server_save))
            }
        }

        when (val state = connectionState) {
            is ConnectionState.Success -> {
                Text(
                    stringResource(R.string.server_test_success),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            is ConnectionState.Failure -> {
                Text(
                    stringResource(R.string.server_test_failure, state.message),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            else -> {}
        }
        if (miniPlayerVisible) Spacer(Modifier.height(80.dp))
    }
}
