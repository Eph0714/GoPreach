package com.emfitsolutions.gopreach.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.BuildConfig
import com.emfitsolutions.gopreach.data.repository.ThemePreference
import com.emfitsolutions.gopreach.ui.components.ThemeOptionRow
import com.emfitsolutions.gopreach.ui.components.update.UpdateViewModel
import com.emfitsolutions.gopreach.ui.theme.ThemeColorOption

/** Display preference — per-device, not tied to any account (spec §1: "modern
 * Android UI"). Available to every signed-in role. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val colorOption by viewModel.colorOption.collectAsStateWithLifecycle()
    // Explicitly Activity-scoped (not the default nav-entry scope) so this is
    // the *same* instance MainActivity's UpdateHost renders the result of —
    // otherwise tapping "Check for Updates" here would update a ViewModel
    // nothing on screen is actually observing.
    val updateViewModel: UpdateViewModel = hiltViewModel(LocalContext.current as ComponentActivity)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    ThemeOptionRow("System default", ThemePreference.SYSTEM, theme, viewModel::setTheme)
                    ThemeOptionRow("Light", ThemePreference.LIGHT, theme, viewModel::setTheme)
                    ThemeOptionRow("Dark", ThemePreference.DARK, theme, viewModel::setTheme)
                }
            }

            Text("Theme Color", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your own choice on this device only — not shared with anyone else.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                // A plain wrapped Row grid, not LazyVerticalGrid — this Card already
                // sits inside the screen's own scrollable Column, and a lazy grid
                // nested inside another scrollable container has no bounded height
                // to lay out against. Six fixed swatches don't need laziness anyway.
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ThemeColorOption.entries.chunked(3).forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowOptions.forEach { option ->
                                ThemeColorSwatchOption(
                                    option = option,
                                    selected = option == colorOption,
                                    onClick = { viewModel.setColorOption(option) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            Text("About", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = updateViewModel::checkManually,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) { Text("Check for Updates") }
                }
            }
        }
    }
}

/** One tappable swatch + label in the Theme Color picker — the swatch itself is
 * the option's light-mode primary color, with a check mark overlay when
 * selected, matching the common "pick an accent color" pattern. */
@Composable
private fun ThemeColorSwatchOption(
    option: ThemeColorOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(option.swatch.light, CircleShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White)
            }
        }
        Text(option.label, style = MaterialTheme.typography.labelSmall)
    }
}
