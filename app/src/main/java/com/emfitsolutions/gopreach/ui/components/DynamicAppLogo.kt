package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.emfitsolutions.gopreach.data.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LogoViewModel @Inject constructor(
    appSettingsRepository: AppSettingsRepository,
) : ViewModel() {
    val logoUrl: StateFlow<String?> = appSettingsRepository.observe()
        .map { it.logoUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

/**
 * The logo shown in [AppBanner] — the Super-Admin-uploaded image (spec §1, Control
 * Panel) when one exists, falling back to a plain icon glyph otherwise. Used as the
 * `logoContent` slot on every entry/dashboard banner so a logo change anywhere in
 * the Control Panel shows up everywhere without each screen re-implementing this.
 */
@Composable
fun DynamicAppLogo(viewModel: LogoViewModel = hiltViewModel()) {
    val logoUrl by viewModel.logoUrl.collectAsStateWithLifecycle()
    if (logoUrl != null) {
        AsyncImage(
            model = logoUrl,
            contentDescription = "GoPreach logo",
            modifier = Modifier.size(64.dp),
        )
    } else {
        Icon(
            imageVector = Icons.Filled.MenuBook,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
    }
}
