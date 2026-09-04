package com.emfitsolutions.gopreach.ui.screens.groupchat

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.emfitsolutions.gopreach.data.model.GroupChatAttachmentType
import com.emfitsolutions.gopreach.data.model.GroupChatMessage
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp
import com.emfitsolutions.gopreach.ui.components.rememberActionToast

/** A picked-but-not-yet-sent attachment — shown as its own row above the
 * input box (spec §8: "show file name, file type/icon, file size, upload
 * progress, cancel upload option"). */
private data class PendingAttachment(val uri: Uri, val fileName: String, val fileSize: Long, val type: GroupChatAttachmentType)

/**
 * "Group Chat Interface" (spec §7-8) — header, chronological message
 * bubbles, and a text+attach+send input row, plus Settings (participants,
 * gated to [canManageSettings]) and Shared Documents entry points in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupChatId: String,
    currentPersonId: String,
    currentPersonName: String,
    currentPersonRoleLabel: String,
    canManageSettings: Boolean,
    onBack: () -> Unit,
    viewModel: GroupChatViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val showToast = rememberActionToast()
    val chatFlow = remember(groupChatId) { viewModel.groupChat(groupChatId) }
    val chat by chatFlow.collectAsStateWithLifecycle(initialValue = null)
    val messagesFlow = remember(groupChatId) { viewModel.messages(groupChatId) }
    val messages by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val congregations by viewModel.congregations.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var showSettings by remember { mutableStateOf(false) }
    var showDocuments by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var pendingAttachment by remember { mutableStateOf<PendingAttachment?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    // Opening the chat (and every time a new message arrives while it's
    // open) marks it read — this is what clears the Chat Box/list unread
    // badge for this participant (spec §14: "real-time unread-message count
    // updates").
    LaunchedEffect(groupChatId, currentPersonId, messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
        viewModel.markRead(groupChatId, currentPersonId)
    }

    val pickAttachment = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = queryFileName(context, uri) ?: uri.lastPathSegment ?: "file"
            val type = inferAttachmentType(context, uri, name)
            if (type == null) {
                showToast("Unsupported file type. Choose an image, PDF, Word, or Excel file.")
            } else {
                val size = queryFileSize(context, uri)
                pendingAttachment = PendingAttachment(uri, name, size, type)
            }
        }
    }

    fun send() {
        val text = messageText
        val attachment = pendingAttachment
        if (text.isBlank() && attachment == null) return
        if (attachment != null) {
            isUploading = true
            viewModel.sendAttachment(
                groupChatId = groupChatId,
                senderId = currentPersonId,
                senderName = currentPersonName,
                senderRole = currentPersonRoleLabel,
                text = text,
                fileUri = attachment.uri,
                fileName = attachment.fileName,
                fileSize = attachment.fileSize,
                attachmentType = attachment.type,
                onFailed = { isUploading = false; showToast("File failed to upload. Try again.") },
            )
            isUploading = false
            pendingAttachment = null
        } else {
            viewModel.sendText(groupChatId, currentPersonId, currentPersonName, currentPersonRoleLabel, text)
        }
        messageText = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(chat?.groupName ?: "Group Chat", style = MaterialTheme.typography.titleMedium)
                        val congregationName = congregations.firstOrNull { it.id == chat?.congregationId }?.name
                        Text(
                            listOfNotNull(congregationName, chat?.let { "👥 ${it.participantIds.size} Participants" }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showDocuments = true }) {
                        Icon(Icons.Rounded.Folder, contentDescription = "Shared Documents")
                    }
                    if (canManageSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Group Settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message, isOwnMessage = message.senderId == currentPersonId)
                }
            }

            pendingAttachment?.let { attachment ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(attachment.type.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(attachment.fileName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text(formatFileSize(attachment.fileSize), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp).widthIn(max = 20.dp))
                    } else {
                        IconButton(onClick = { pendingAttachment = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel attachment")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { pickAttachment.launch(GROUP_CHAT_ATTACHMENT_MIME_TYPES) }) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = "Attach File")
                }
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Type your message...") },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = ::send, enabled = !isUploading) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    val currentChat = chat
    if (showSettings && currentChat != null) {
        ManageParticipantsDialog(chat = currentChat, currentPersonId = currentPersonId, viewModel = viewModel, onDismiss = { showSettings = false })
    }
    if (showDocuments && currentChat != null) {
        SharedDocumentsDialog(groupName = currentChat.groupName, messages = messages, onDismiss = { showDocuments = false })
    }
}

@Composable
private fun MessageBubble(message: GroupChatMessage, isOwnMessage: Boolean) {
    val bubbleColor = if (isOwnMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(bubbleColor, RoundedCornerShape(14.dp))
                .padding(10.dp),
        ) {
            if (!isOwnMessage) {
                Text(message.senderName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(message.senderRole, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (message.attachmentUrl != null) {
                val context = LocalContext.current
                if (message.attachmentType == GroupChatAttachmentType.IMAGE) {
                    AsyncImage(
                        model = message.attachmentUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = 4.dp, bottom = 4.dp)
                            .clickable { com.emfitsolutions.gopreach.data.export.CsvExporter.openWithChooser(context, Uri.parse(message.attachmentUrl), "image/*") },
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { com.emfitsolutions.gopreach.data.export.CsvExporter.openWithChooser(context, Uri.parse(message.attachmentUrl), "*/*") },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(message.attachmentType.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            message.attachmentFileName ?: "Attachment",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            if (message.text.isNotBlank()) {
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                formatRecordTimestamp(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun GroupChatAttachmentType?.icon() = when (this) {
    GroupChatAttachmentType.IMAGE -> Icons.Rounded.AttachFile
    GroupChatAttachmentType.PDF, GroupChatAttachmentType.WORD, GroupChatAttachmentType.EXCEL, null -> Icons.Rounded.InsertDriveFile
}

/** "Supported attachment types" spec §8 — image/PDF/Word/Excel, offered
 * together in one picker (same [ActivityResultContracts.OpenDocument]
 * multi-mime shape [com.emfitsolutions.gopreach.ui.screens.announcements
 * .AnnouncementsScreen]'s ATTACHMENT_MIME_TYPES already uses for
 * documents, with image types added). */
private val GROUP_CHAT_ATTACHMENT_MIME_TYPES = arrayOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
)

private fun inferAttachmentType(context: android.content.Context, uri: Uri, fileName: String): GroupChatAttachmentType? {
    val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
    val lowerName = fileName.lowercase()
    return when {
        mimeType.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") -> GroupChatAttachmentType.IMAGE
        mimeType == "application/pdf" || lowerName.endsWith(".pdf") -> GroupChatAttachmentType.PDF
        mimeType.contains("word") || lowerName.endsWith(".doc") || lowerName.endsWith(".docx") -> GroupChatAttachmentType.WORD
        mimeType.contains("excel") || mimeType.contains("spreadsheet") || lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") -> GroupChatAttachmentType.EXCEL
        else -> null
    }
}

/** Same [OpenableColumns.DISPLAY_NAME] query [AnnouncementsScreen]'s own
 * `queryFileName` uses — duplicated rather than shared since it's a
 * three-line, file-private helper there too. */
private fun queryFileName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment

private fun querySizeColumn(context: android.content.Context, uri: Uri): Long = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }
}.getOrNull() ?: 0L

private fun queryFileSize(context: android.content.Context, uri: Uri): Long = querySizeColumn(context, uri)

fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
