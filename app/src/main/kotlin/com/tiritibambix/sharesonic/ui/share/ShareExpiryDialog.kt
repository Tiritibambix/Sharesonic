package com.tiritibambix.sharesonic.ui.share

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.ui.theme.textSecondary

/**
 * Asks the user how long the public share link should remain valid before
 * actually creating it — mirrors Velvet's "days until expiration"
 * field in its share dialog.
 *
 * Leaving the field empty (or 0) creates a permanent link, matching the
 * native API contract where omitting `time` means "never expires".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareExpiryDialog(
    onConfirm: (expiryDays: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var daysText by remember { mutableStateOf("") }

    val parsedDays: Int? = daysText.trim().toIntOrNull()?.takeIf { it > 0 }
    val isValid = daysText.isBlank() || parsedDays != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_expiry_confirm)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.share_expiry_title),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.share_expiry_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.textSecondary
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { input ->
                        // Digits only — keeps the field a clean day-count entry.
                        daysText = input.filter { it.isDigit() }
                    },
                    placeholder = { Text(stringResource(R.string.share_expiry_permanent)) },
                    singleLine = true,
                    isError = !isValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(parsedDays) },
                enabled = isValid
            ) { Text(stringResource(R.string.share_expiry_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
