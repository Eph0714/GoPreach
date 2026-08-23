package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The "Admin Record Deletion and Inactive Status" spec's replacement for the
 * old `Delete -> Immediately Delete` pattern used across every Manage screen
 * (Admins, Elders, Publishers, Groups, Congregations, Users, Interested
 * People): tapping Delete now always asks *what kind* of deletion is meant,
 * and Permanent Delete always gets its own second, harder-to-hit
 * confirmation.
 *
 * [canPermanentlyDelete] gates the option entirely (spec §7: a user without
 * permanent-delete permission must not be able to reach it at all, not just
 * have it hidden-but-reachable) — callers pass `isSuperAdmin` today, since
 * permanent deletion is deliberately restricted to the top of the role
 * hierarchy across every record type in this pass (see BUILD_PLAN.md for
 * that scoping decision). [permanentDeleteBlockedReason], when non-null,
 * replaces the second step's delete button with an explanation instead —
 * used when a relationship check (spec §4) found dependent records that
 * would otherwise be silently destroyed.
 */
@Composable
fun DeleteChoiceDialog(
    recordLabel: String,
    canPermanentlyDelete: Boolean,
    onDismiss: () -> Unit,
    onMoveToInactive: () -> Unit,
    onDeletePermanently: () -> Unit,
    permanentDeleteBlockedReason: String? = null,
) {
    var showPermanentConfirm by remember { mutableStateOf(false) }

    if (!showPermanentConfirm) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("What would you like to do with \"$recordLabel\"?") },
            text = {
                Text(
                    "Move to Inactive keeps the record and its history, hidden from normal active lists, and can be restored later. " +
                        "Delete Permanently cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Button(
                        onClick = { onMoveToInactive(); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Move to Inactive") }
                    if (canPermanentlyDelete) {
                        OutlinedButton(
                            onClick = { showPermanentConfirm = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Delete Permanently") }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Permanently Delete Record?") },
            text = {
                Text(
                    permanentDeleteBlockedReason
                        ?: "This action cannot be undone. All associated information that is permitted to be permanently deleted will be removed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            // Both buttons live in this one slot, centered as a group, rather than
            // the default confirm/dismiss split (confirm right, dismiss left) —
            // Cancel reads as the safer of the two actions here, so it's centered
            // rather than pushed off to the corner.
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { if (permanentDeleteBlockedReason != null) onDismiss() else showPermanentConfirm = false }) {
                        Text(if (permanentDeleteBlockedReason != null) "Close" else "Cancel")
                    }
                    if (permanentDeleteBlockedReason == null) {
                        Button(
                            onClick = { onDeletePermanently(); onDismiss() },
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("Delete Permanently") }
                    }
                }
            },
        )
    }
}
