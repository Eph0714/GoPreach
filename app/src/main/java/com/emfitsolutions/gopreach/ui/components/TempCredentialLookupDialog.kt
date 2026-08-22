package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.domain.CredentialGenerator

/**
 * Tapping a Person row on a Manage-X list (Admins, Publishers, Coordinator/
 * Regular Elders) surfaces this when that person still hasn't completed their
 * forced first-login password change — a quick way to recover a temp
 * username/password without re-enrolling them, for whoever lost the original
 * share link. Once the person changes their password, [Person.temporaryPassword]
 * is cleared server-side and this dialog has nothing left to show, by design.
 */
@Composable
fun TempCredentialLookupDialog(person: Person, onDismiss: () -> Unit) {
    val tempPassword = person.temporaryPassword
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temporary Sign-In") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${person.fullName} hasn't signed in and changed their password yet. Share this again if needed:",
                )
                Text("Username: ${person.username}", fontWeight = FontWeight.Bold)
                if (tempPassword != null) {
                    Text("Temporary Password: $tempPassword", fontWeight = FontWeight.Bold)
                    Text(
                        "Setup Link: ${CredentialGenerator.shareableSetupLink(person.username, tempPassword)}",
                    )
                } else {
                    Text("No temporary password on file for this account — it may have been created before this feature, or the record needs re-syncing.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
