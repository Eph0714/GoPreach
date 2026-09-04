package com.emfitsolutions.gopreach.ui.screens.groupchat

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.emfitsolutions.gopreach.data.export.CsvExporter
import com.emfitsolutions.gopreach.data.model.GroupChatAttachmentType
import com.emfitsolutions.gopreach.data.model.GroupChatMessage
import com.emfitsolutions.gopreach.ui.components.FormDialog
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp

/**
 * "Shared Document Folder" (spec §9-10) — every attachment ever sent in this
 * one group chat, auto-organized by file type (Images / PDF / Word / Excel),
 * searchable, each row showing uploader/date/size with a Download action.
 * Scoped to the group chat this dialog was opened from (participant access
 * is already enforced by the fact they could open the chat at all — see
 * firestore.rules' groupChats match).
 */
@Composable
fun SharedDocumentsDialog(groupName: String, messages: List<GroupChatMessage>, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<GroupChatAttachmentType?>(null) }

    val documents = remember(messages, query, typeFilter) {
        messages
            .filter { it.hasAttachment }
            .filter { typeFilter == null || it.attachmentType == typeFilter }
            .filter { query.isBlank() || (it.attachmentFileName?.contains(query, ignoreCase = true) == true) || it.senderName.contains(query, ignoreCase = true) }
            .sortedByDescending { it.createdAt }
    }

    FormDialog(
        onDismissRequest = onDismiss,
        title = "Shared Documents · $groupName",
        onConfirm = onDismiss,
        confirmLabel = "Close",
        dismissLabel = "Close",
        maxContentHeight = 560.dp,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search files") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            AssistChip(onClick = { typeFilter = null }, label = { Text("All") })
            AssistChip(onClick = { typeFilter = GroupChatAttachmentType.IMAGE }, label = { Text("Images") })
            AssistChip(onClick = { typeFilter = GroupChatAttachmentType.PDF }, label = { Text("PDF") })
            AssistChip(onClick = { typeFilter = GroupChatAttachmentType.WORD }, label = { Text("Word") })
            AssistChip(onClick = { typeFilter = GroupChatAttachmentType.EXCEL }, label = { Text("Excel") })
        }
        if (documents.isEmpty()) {
            Text(
                "No shared documents yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            documents.forEach { message ->
                DocumentRow(message)
                Divider()
            }
        }
    }
}

@Composable
private fun DocumentRow(message: GroupChatMessage) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(message.attachmentFileName ?: "Attachment", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "Uploaded by: ${message.senderName} · ${formatRecordTimestamp(message.createdAt)} · ${formatFileSize(message.attachmentSize)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = {
            val mime = when (message.attachmentType) {
                GroupChatAttachmentType.IMAGE -> "image/*"
                GroupChatAttachmentType.PDF -> "application/pdf"
                else -> "*/*"
            }
            CsvExporter.openWithChooser(context, Uri.parse(message.attachmentUrl), mime)
        }) {
            Icon(Icons.Rounded.Download, contentDescription = "Download")
        }
    }
}
