package com.emfitsolutions.gopreach.ui.screens.settings

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.rounded.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.BuildConfig
import com.emfitsolutions.gopreach.data.repository.ThemePreference
import com.emfitsolutions.gopreach.notifications.AlarmScheduler
import com.emfitsolutions.gopreach.domain.AppLanguage
import com.emfitsolutions.gopreach.ui.components.ColorWheelPicker
import com.emfitsolutions.gopreach.ui.components.EyedropperImagePicker
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
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
    val language by viewModel.language.collectAsStateWithLifecycle()
    var showCustomColorDialog by remember { mutableStateOf(false) }
    val showToast = rememberActionToast()
    LaunchedEffect(Unit) { viewModel.languageChanged.collect { showToast(it) } }
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
            Text(stringResource(com.emfitsolutions.gopreach.R.string.settings_language_title), style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    AppLanguage.entries.forEach { option ->
                        LanguageOptionRow(option = option, selected = language, onSelected = viewModel::setLanguage)
                    }
                }
            }

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

            NotificationSoundSection(viewModel = viewModel)

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
 * "Allow all the users to manage notification sound... browse to mobile
 * notification sounds" — one setting shared by every incoming notification
 * this app posts (Transfer Request, Announcement, Calendar Alarm alike; see
 * [com.emfitsolutions.gopreach.notifications.NotificationHelper]'s doc
 * comment). Uses the system's own ringtone picker
 * ([RingtoneManager.ACTION_RINGTONE_PICKER]) rather than a custom list, so it
 * shows exactly the same notification sounds the user's phone already offers
 * everywhere else. Also surfaces the "Alarms & reminders" system permission
 * when it's missing (API 31+) — without it, Calendar Alarms still ring, just
 * not necessarily at the exact minute.
 */
@Composable
private fun NotificationSoundSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val soundUri by viewModel.notificationSoundUri.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    var exactAlarmsAllowed by remember { mutableStateOf(AlarmScheduler.canScheduleExactAlarms(context)) }

    val pickRingtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.setNotificationSound(picked)
        }
    }

    Text("Notifications", style = MaterialTheme.typography.titleMedium)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            ListItem(
                headlineContent = { Text("Notifications") },
                supportingContent = { Text("Transfer requests, announcements, and monthly report reminders. Calendar Alarms you've scheduled still ring either way.") },
                leadingContent = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
                trailingContent = {
                    Switch(checked = notificationsEnabled, onCheckedChange = viewModel::setNotificationsEnabled)
                },
            )
            androidx.compose.material3.HorizontalDivider()
            ListItem(
                headlineContent = { Text("Notification Sound") },
                supportingContent = { Text(ringtoneTitle(context, soundUri)) },
                leadingContent = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                modifier = Modifier
                    .alpha(if (notificationsEnabled) 1f else 0.5f)
                    .clickable(enabled = notificationsEnabled) {
                        val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                            )
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, soundUri)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Notification Sound")
                        }
                        pickRingtone.launch(intent)
                    },
            )
            Text(
                "Plays for every incoming notification — transfer requests, announcements, and calendar alarms.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmsAllowed) {
                androidx.compose.material3.HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Allow Exact Alarms") },
                    supportingContent = { Text("Needed so Calendar Alarms ring at the exact scheduled time.") },
                    leadingContent = { Icon(Icons.Rounded.Alarm, contentDescription = null) },
                    modifier = Modifier.clickable {
                        context.startActivity(
                            android.content.Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            },
                        )
                        exactAlarmsAllowed = AlarmScheduler.canScheduleExactAlarms(context)
                    },
                )
            }
        }
    }
}

/** Best-effort human-readable name for [uri] via [RingtoneManager] — falls
 * back to a generic label rather than crashing if the picked sound was later
 * uninstalled/removed (e.g. a ringtone from an app the user removed). */
private fun ringtoneTitle(context: android.content.Context, uri: Uri?): String {
    if (uri == null) return "Default"
    return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
        .getOrNull() ?: "Custom sound"
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

/** "Settings -> Language" — English/Filipino/Iloko, same radio-row shape
 * [ThemeOptionRow] already uses for the Appearance section right below this
 * one. Picking a row applies immediately (see [SettingsViewModel.setLanguage]'s
 * own doc comment for why — on-device first, then synced to the signed-in
 * Person's profile). */
@Composable
private fun LanguageOptionRow(option: AppLanguage, selected: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected == option, onClick = { onSelected(option) })
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == option, onClick = { onSelected(option) })
        Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.padding(start = 8.dp, end = 8.dp))
        Text(option.displayLabel, style = MaterialTheme.typography.bodyLarge)
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
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).imePadding(),
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
