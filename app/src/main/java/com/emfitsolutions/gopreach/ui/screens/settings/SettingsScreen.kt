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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.BuildConfig
import com.emfitsolutions.gopreach.data.repository.ThemePreference
import com.emfitsolutions.gopreach.ui.components.ColorWheelPicker
import com.emfitsolutions.gopreach.ui.components.EyedropperImagePicker
import kotlinx.coroutines.launch
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
    val customColor by viewModel.customColor.collectAsStateWithLifecycle()
    var showCustomColorDialog by remember { mutableStateOf(false) }
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
                                if (option == ThemeColorOption.CUSTOM) {
                                    // "Let the users select from color wheel...
                                    // eyedrop a color" — this tile is the entry
                                    // point into that picker, not a fixed swatch.
                                    CustomColorSwatchOption(
                                        customColor = customColor,
                                        selected = option == colorOption,
                                        onClick = { showCustomColorDialog = true },
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    ThemeColorSwatchOption(
                                        option = option,
                                        selected = option == colorOption,
                                        onClick = { viewModel.setColorOption(option) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            // Pads out the last row so a 7th tile alone
                            // doesn't stretch to fill the whole row width.
                            repeat(3 - rowOptions.size) { Box(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }

            AppVersionSection(updateViewModel = updateViewModel)
        }
    }

    if (showCustomColorDialog) {
        CustomColorPickerDialog(
            initialColor = customColor,
            onApply = { picked ->
                viewModel.setCustomColor(picked)
                showCustomColorDialog = false
            },
            onDismiss = { showCustomColorDialog = false },
        )
    }
}

/**
 * "Add a details of the newly installed update. Add a link for the updated
 * apk file after installation so that the user can share the app to
 * others. Put it inside the Settings / 'App Version' Folder." — replaces
 * the old plain "About" section. [UpdateViewModel.installedUpdateInfo] is
 * this device's own update history (see that store's doc comment for why
 * it can be empty); "Share App" always fetches the current latest release
 * fresh rather than relying on that history, so it works from a first
 * install too.
 */
@Composable
private fun AppVersionSection(updateViewModel: UpdateViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val installedUpdateInfo = remember { updateViewModel.installedUpdateInfo }

    Text("App Version", style = MaterialTheme.typography.titleMedium)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)

            if (installedUpdateInfo != null && installedUpdateInfo.releaseNotes.isNotBlank()) {
                Text(
                    "What's New",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(installedUpdateInfo.releaseNotes, style = MaterialTheme.typography.bodySmall)
            }

            if (shareError != null) {
                Text(shareError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = updateViewModel::checkManually,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text("Check for Updates") }

            OutlinedButton(
                onClick = {
                    isSharing = true
                    shareError = null
                    coroutineScope.launch {
                        updateViewModel.fetchLatestForShare()
                            .onSuccess { info -> launchAppShare(context, updateViewModel.shareText(info)) }
                            .onFailure { shareError = "Couldn't fetch the latest download link. Check your connection and try again." }
                        isSharing = false
                    }
                },
                enabled = !isSharing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSharing) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                } else {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                }
                Text("Share App")
            }
        }
    }
}

private fun launchAppShare(context: android.content.Context, text: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "GoPreach")
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share GoPreach"))
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
                // Never null here — this composable is only ever called for
                // the six fixed presets; ThemeColorOption.CUSTOM routes to
                // CustomColorSwatchOption instead (see SettingsScreen above).
                .background(option.swatch!!.light, CircleShape)
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

/** The "Custom" tile — a wheel/rainbow icon when no custom color has ever
 * been picked yet, otherwise the picked color itself, same swatch shape as
 * every preset. Tapping it (whether or not it's already selected) opens the
 * picker — unlike a preset, "select this option" and "change its color" are
 * the same gesture here. */
@Composable
private fun CustomColorSwatchOption(
    customColor: Color,
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
                .background(if (selected) customColor else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val iconTint = if (selected && customColor.luminance() < 0.5f) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(Icons.Rounded.Palette, contentDescription = "Custom color", tint = iconTint)
        }
        Text("Custom", style = MaterialTheme.typography.labelSmall)
    }
}

/** The color wheel + eyedropper dialog — spec: "let the users select from
 * color wheel... the user can eyedrop a color he wants." Both feed the same
 * live preview and the same [onApply] color; nothing is actually saved
 * until Apply is tapped, so browsing the wheel/photo never changes the
 * live app theme mid-pick. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomColorPickerDialog(
    initialColor: Color,
    onApply: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickedColor by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a Custom Color") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(pickedColor, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                ColorWheelPicker(color = pickedColor, onColorChanged = { pickedColor = it })
                EyedropperImagePicker(onColorPicked = { pickedColor = it }, modifier = Modifier.fillMaxWidth())
                Text(
                    "The color applied may be adjusted slightly to stay readable against white text.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(pickedColor) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
