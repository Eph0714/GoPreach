package com.emfitsolutions.gopreach.ui.components.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent

/**
 * Hosted once, near the app's root (see MainActivity) — reflects whatever
 * [UpdateViewModel.state] currently is. Renders nothing for Idle/Checking, so
 * mounting this doesn't itself show anything; only a real Available/
 * Downloading/Verifying/ReadyToInstall/Failed/UpToDate state does.
 */
@Composable
fun UpdateHost(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Once the download is verified, hand off to the system Package Installer
    // immediately — there's no separate in-app "Installing…" screen because
    // installation itself is entirely Android's own UI from this point on.
    LaunchedEffect(state) {
        val ready = state as? UpdateCheckState.ReadyToInstall ?: return@LaunchedEffect
        if (!viewModel.canInstall()) {
            context.startActivity(viewModel.requestInstallPermissionIntent())
            return@LaunchedEffect
        }
        context.startActivity(viewModel.installIntentFor(ready.apkFile))
    }

    when (val s = state) {
        is UpdateCheckState.Available -> {
            AlertDialog(
                onDismissRequest = viewModel::dismiss,
                title = { Text("Update Available") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("A new version of GoPreach is available.")
                        Text("Current Version: ${s.currentVersion}")
                        Text("New Version: ${s.info.version}", fontWeight = FontWeight.Bold)
                        if (s.info.releaseNotes.isNotBlank()) {
                            Text(s.info.releaseNotes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = viewModel::updateNow) { Text("UPDATE NOW") }
                },
                dismissButton = {
                    val shareText = viewModel.shareText(s.info)
                    OutlinedButton(onClick = { launchShare(context, shareText) }) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("SHARE APK")
                    }
                },
            )
        }

        is UpdateCheckState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Updating GoPreach...") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Downloading version ${s.info.version}")
                        LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("${s.percent}%")
                    }
                },
                confirmButton = {},
            )
        }

        is UpdateCheckState.Verifying -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Verifying Update...") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text("Checking the download's integrity and signature before installing.")
                    }
                },
                confirmButton = {},
            )
        }

        is UpdateCheckState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Installing Update...") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text("Handing off to Android's installer for version ${s.info.version}.")
                    }
                },
                confirmButton = {},
            )
        }

        is UpdateCheckState.Failed -> {
            AlertDialog(
                onDismissRequest = viewModel::dismiss,
                title = { Text("Update Failed") },
                text = { Text("${s.message}\n\nYour current version is still available.") },
                confirmButton = {
                    Button(onClick = viewModel::retry) { Text("TRY AGAIN") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismiss) { Text("Close") }
                },
            )
        }

        is UpdateCheckState.UpToDate -> {
            AlertDialog(
                onDismissRequest = viewModel::dismiss,
                title = { Text("GoPreach is up to date.") },
                text = { Text("Version ${s.version}") },
                confirmButton = {
                    TextButton(onClick = viewModel::dismiss) { Text("OK") }
                },
            )
        }

        UpdateCheckState.Idle, UpdateCheckState.Checking -> Unit
    }
}

private fun launchShare(context: android.content.Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "GoPreach update")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share GoPreach update"))
}
