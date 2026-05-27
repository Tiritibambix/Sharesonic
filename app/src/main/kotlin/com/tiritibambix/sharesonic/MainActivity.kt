package com.tiritibambix.sharesonic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tiritibambix.sharesonic.ui.navigation.AppNavGraph
import com.tiritibambix.sharesonic.ui.theme.SharesonicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SharesonicTheme {
                AppNavGraph()
            }
        }
    }
}
