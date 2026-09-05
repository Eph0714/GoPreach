package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emfitsolutions.gopreach.R

/**
 * "Do not allow the keyboard to override the button when saving. Always
 * show all the text and button at the top of keyboard" — Material3's own
 * `AlertDialog` renders `confirmButton`/`dismissButton` in a footer
 * *outside* the scrollable `text` slot, so a `Modifier.imePadding()` on
 * that slot alone (this app's original per-dialog keyboard fix, still
 * correct for the fields themselves) keeps text fields clear of the
 * keyboard but does nothing for those buttons — nothing ties their
 * position to the keyboard's height, so a tall form can still push Save
 * down behind it. [FormDialog] folds the action row into the *same*
 * imePadding-aware, scrollable Column as the fields (Material3's own
 * confirmButton/dismissButton slots are left empty), so the buttons are
 * always part of the one region guaranteed to sit above the IME — scroll to
 * see them on a long form, but they're never hidden behind the keyboard.
 *
 * [errorMessage] — "make an action message for every required text field
 * that is not filled up when saving" — shown just above the action row
 * (e.g. via [requiredFieldsMessage]); null hides it entirely. Confirm stays
 * enabled even while it's showing (unlike a hard [confirmEnabled] gate) so
 * tapping Save with something missing always produces this message instead
 * of just silently doing nothing — the caller's onConfirm is expected to
 * re-check and only actually save when there's nothing left to report.
 */
@Composable
fun FormDialog(
    onDismissRequest: () -> Unit,
    title: String,
    onConfirm: () -> Unit,
    // "Settings -> Language" (see AppLanguage) — defaults sourced from
    // strings.xml rather than a literal, so every one of this app's dozens
    // of FormDialog call sites that don't override these picks up the
    // signed-in user's language automatically, with no other file needing
    // to change.
    confirmLabel: String = stringResource(R.string.action_save),
    dismissLabel: String = stringResource(R.string.action_cancel),
    confirmEnabled: Boolean = true,
    errorMessage: String? = null,
    maxContentHeight: Dp = 480.dp,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = maxContentHeight)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) { Text(dismissLabel) }
                    TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

/**
 * "Make an action message for every required text field that is not filled
 * up when saving" — pass each required field as (human-readable label, is
 * it actually filled in); returns "X is required."/"X, Y are required." for
 * whichever ones aren't, or null once every one of them is. Meant to be
 * recomputed on every Save tap (see [FormDialog.onConfirm]) rather than
 * live on every keystroke, so the message only appears once the publisher
 * actually tries to save, not while they're still mid-typing the first field.
 */
fun requiredFieldsMessage(vararg fields: Pair<String, Boolean>): String? {
    val missing = fields.filter { !it.second }.map { it.first }
    if (missing.isEmpty()) return null
    return "${missing.joinToString(", ")} ${if (missing.size == 1) "is" else "are"} required."
}
