package com.emfitsolutions.gopreach.ui.screens.monthlyreport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.PublisherCategory

/**
 * Spec §5.2 — monthly ministry report. Shows only the fields required for the
 * signed-in publisher's category, and locks once submitted (only a Coordinator/
 * Regular Elder can edit it from that point — see [MonthlyReportUiState.isLocked]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScreen(
    publisherPersonId: String,
    /** True when the signed-in user is a Coordinator/Regular Elder editing a
     * publisher's report rather than the publisher editing their own (spec
     * §5.2's post-submission edit right). */
    allowEditWhenLocked: Boolean = false,
    onBack: () -> Unit,
    viewModel: MonthlyReportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(publisherPersonId) { viewModel.load(publisherPersonId) }

    val isPioneer = uiState.category == PublisherCategory.REGULAR_PIONEER || uiState.category == PublisherCategory.AUXILIARY_PIONEER
    val effectivelyLocked = uiState.isLocked && !allowEditWhenLocked
    // The submission-window gate only applies to a publisher's own normal
    // submit flow — an Elder editing on someone's behalf (allowEditWhenLocked)
    // isn't restricted to the last-2-days window.
    val submitBlockedByWindow = !uiState.canSubmitWindow && !allowEditWhenLocked && !uiState.isLocked

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Report") },
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
            Text(
                "Category: ${uiState.category?.name?.replace('_', ' ') ?: "Unknown"}",
                style = MaterialTheme.typography.titleMedium,
            )

            if (effectivelyLocked) {
                Text(
                    "This report has been submitted and can only be changed by your Coordinator or Regular Elder.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedTextField(
                value = uiState.bibleStudiesCount,
                onValueChange = viewModel::onBibleStudiesChange,
                label = { Text("Number of Bible Studies Conducted") },
                singleLine = true,
                enabled = !effectivelyLocked,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isPioneer) {
                OutlinedTextField(
                    value = uiState.hoursRendered,
                    onValueChange = viewModel::onHoursChange,
                    label = { Text("Hours Rendered") },
                    singleLine = true,
                    enabled = !effectivelyLocked,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Participated in preaching this month?", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = uiState.participatedInPreaching,
                        onCheckedChange = viewModel::onParticipatedChange,
                        enabled = !effectivelyLocked,
                    )
                }
            }

            if (uiState.errorMessage != null) {
                Text(text = uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
            }

            if (submitBlockedByWindow) {
                Text(
                    "Submission opens 2 days before the end of the month.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = { viewModel.submit(publisherPersonId) },
                enabled = !uiState.isSaving && !effectivelyLocked && !submitBlockedByWindow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Submit Report")
            }
        }
    }
}
