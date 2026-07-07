package com.tiritibambix.sharesonic

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.tiritibambix.sharesonic.R
import com.tiritibambix.sharesonic.data.settings.AppTheme
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import com.tiritibambix.sharesonic.ui.navigation.AppNavGraph
import com.tiritibambix.sharesonic.ui.theme.SharesonicTheme
import com.tiritibambix.sharesonic.utils.LocalIsTV
import com.tiritibambix.sharesonic.utils.isTV
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    /**
     * Apply the user-picked UI language BEFORE any resource is resolved.
     * `attachBaseContext` runs before `onCreate`, so we wrap the incoming
     * [Context] in one whose configuration carries the target locale — every
     * `stringResource(...)` and `LocalConfiguration` in Compose then follows it.
     *
     * Empty tag ⇒ follow the system locale (no wrapping). Works on minSdk 26
     * with a plain ComponentActivity, no androidx.appcompat dependency needed.
     * A [runBlocking] read is fine here: DataStore uses a process-wide
     * singleton file and this fires once per activity create.
     */
    override fun attachBaseContext(newBase: Context) {
        val tag = runBlocking { SettingsRepository(newBase).appLanguage.first() }
        if (tag.isEmpty()) {
            super.attachBaseContext(newBase)
            return
        }
        val locale = java.util.Locale.forLanguageTag(tag)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepo = SettingsRepository(applicationContext)
        val appThemeFlow = settingsRepo.appTheme
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AppTheme.VELVET)
        val accentColorFlow = settingsRepo.accentColor
            .stateIn(lifecycleScope, SharingStarted.Eagerly, null)

        // Pick up a stack trace saved by the last crash (see SharesonicApp) so it
        // can be shown + copied on this launch — no adb needed to diagnose crashes.
        val crashPrefs = getSharedPreferences(SharesonicApp.CRASH_PREFS, Context.MODE_PRIVATE)
        val lastCrash = crashPrefs.getString(SharesonicApp.KEY_LAST_CRASH, null)

        val runningOnTV = isTV()
        setContent {
            val appTheme by appThemeFlow.collectAsState()
            val accentArgb by accentColorFlow.collectAsState()
            SharesonicTheme(
                appTheme = appTheme,
                accent = accentArgb?.let { Color(it) },
            ) {
                CompositionLocalProvider(LocalIsTV provides runningOnTV) {
                    AppNavGraph()

                    // Surface the previous crash's stack trace once, copyable.
                    var showCrash by remember { mutableStateOf(lastCrash != null) }
                    if (showCrash && lastCrash != null) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text(stringResource(R.string.crash_title)) },
                            text = {
                                SelectionContainer {
                                    Text(
                                        text = lastCrash,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.verticalScroll(rememberScrollState())
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    crashPrefs.edit().remove(SharesonicApp.KEY_LAST_CRASH).apply()
                                    showCrash = false
                                }) { Text(stringResource(R.string.common_dismiss)) }
                            }
                        )
                    }
                }
            }
        }
    }
}
