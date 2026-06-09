package com.tiritibambix.sharesonic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.tiritibambix.sharesonic.data.settings.AppTheme
import com.tiritibambix.sharesonic.data.settings.SettingsRepository
import com.tiritibambix.sharesonic.ui.navigation.AppNavGraph
import com.tiritibambix.sharesonic.ui.theme.SharesonicTheme
import com.tiritibambix.sharesonic.utils.LocalIsTV
import com.tiritibambix.sharesonic.utils.isTV
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appThemeFlow = SettingsRepository(applicationContext).appTheme
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AppTheme.VELVET)

        val runningOnTV = isTV()
        setContent {
            val appTheme by appThemeFlow.collectAsState()
            SharesonicTheme(appTheme = appTheme) {
                // Provide TV flag to every composable so they can swap
                // swipe gestures for explicit buttons where needed.
                CompositionLocalProvider(LocalIsTV provides runningOnTV) {
                    AppNavGraph()
                }
            }
        }
    }
}
