package com.emfitsolutions.gopreach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.repository.ThemePreference
import com.emfitsolutions.gopreach.data.repository.ThemePreferenceRepository
import com.emfitsolutions.gopreach.ui.navigation.GoPreachNavGraph
import com.emfitsolutions.gopreach.ui.theme.GoPreachTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferenceRepository: ThemePreferenceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preference by themePreferenceRepository.preference.collectAsStateWithLifecycle()
            val darkTheme = when (preference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            // Dynamic (wallpaper-derived) color is deliberately off — spec §1 asks for
            // a clean, consistent look, which a fixed brand palette delivers more
            // reliably than colors that shift with the user's wallpaper.
            GoPreachTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GoPreachNavGraph()
                }
            }
        }
    }
}
