package com.emfitsolutions.gopreach

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.repository.ThemePreference
import com.emfitsolutions.gopreach.data.repository.ThemePreferenceRepository
import com.emfitsolutions.gopreach.ui.components.update.UpdateHost
import com.emfitsolutions.gopreach.ui.components.update.UpdateViewModel
import com.emfitsolutions.gopreach.ui.navigation.GoPreachNavGraph
import com.emfitsolutions.gopreach.ui.theme.GoPreachTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    // FragmentActivity, not the usual bare ComponentActivity, because
    // androidx.biometric.BiometricPrompt (used by the login screen's fingerprint/
    // face sign-in) needs a FragmentManager to host its internal dialog fragment.
    // FragmentActivity is itself a ComponentActivity, so setContent{}/enableEdgeToEdge()
    // below are unaffected.

    @Inject
    lateinit var themePreferenceRepository: ThemePreferenceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preference by themePreferenceRepository.preference.collectAsStateWithLifecycle()
            val colorOption by themePreferenceRepository.colorOption.collectAsStateWithLifecycle()
            val customColor by themePreferenceRepository.customColor.collectAsStateWithLifecycle()
            val darkTheme = when (preference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            // Dynamic (wallpaper-derived) color is deliberately off — spec §1 asks for
            // a clean, consistent look, which a fixed brand palette delivers more
            // reliably than colors that shift with the user's wallpaper. colorOption
            // is the user's own per-device accent color pick (Settings screen) instead;
            // customColor only matters when that pick is ThemeColorOption.CUSTOM (a
            // color wheel/eyedropper choice).
            GoPreachTheme(darkTheme = darkTheme, dynamicColor = false, colorOption = colorOption, customColor = customColor) {
                // enableEdgeToEdge() opts this app out of the system's automatic
                // windowSoftInputMode="adjustResize" handling — Compose has to react to
                // the IME inset itself, or the keyboard simply draws on top of whatever
                // field/button was underneath it instead of the content shrinking to
                // make room. imePadding() here, once, is what actually gets every form
                // and dialog in the app to scroll its focused field (and its Save
                // button) up above the keyboard instead of behind it.
                Surface(modifier = Modifier.fillMaxSize().imePadding()) {
                    GoPreachNavGraph()

                    // Checked once per app process, regardless of whether the user is
                    // signed in yet — "Application Starts -> Check Update Server" per spec.
                    val updateViewModel: UpdateViewModel = hiltViewModel(this@MainActivity)
                    LaunchedEffect(Unit) { updateViewModel.checkOnAppStart() }
                    UpdateHost(updateViewModel)
                }
            }
        }
    }
}
