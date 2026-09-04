package com.emfitsolutions.gopreach.ui.screens.groupchat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.GroupChat
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp

/**
 * "Group Chat Setting" — list entry point. [canManage] (Coordinator Elder,
 * Admin, or Super-Admin) shows every group chat in [fixedCongregationId]
 * (null only for Super-Admin — "All Congregations", same convention every
 * other Manage screen uses) plus a "+ New Group Chat" FAB; everyone else
 * sees only the group chats they're actually a participant of (spec §6:
 * "provide access only to Group Chats where the logged-in user is an active
 * participant, unless the user has administrative permissions").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatListScreen(
    currentPersonId: String,
    canManage: Boolean,
    fixedCongregationId: String?,
    onBack: () -> Unit,
    onOpenGroupChat: (String) -> Unit,
    viewModel: GroupChatViewModel = hiltViewModel(),
) {
    val congregations by viewModel.congregations.collectAsStateWithLifecycle()
    val chatsFlow = remember(canManage, fixedCongregationId, currentPersonId) {
        if (canManage) viewModel.managedGroupChats(fixedCongregationId) else viewModel.myGroupChats(currentPersonId)
    }
    val chats by chatsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Chat Setting") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (canManage) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "New Group Chat")
                }
            }
        },
    ) { padding ->
        if (chats.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (canManage) "No group chats yet. Tap + to create one." else "You're not in any group chat yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chats, key = { it.id }) { chat ->
                    val congregationName = congregations.firstOrNull { it.id == chat.congregationId }?.name
                    GroupChatRow(
                        chat = chat,
                        congregationName = congregationName,
                        unreadCount = (chat.messageCount - (chat.readCounts[currentPersonId] ?: 0L)).coerceAtLeast(0L),
                        onClick = { onOpenGroupChat(chat.id) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupChatDialog(
            fixedCongregationId = fixedCongregationId,
            congregations = congregations,
            currentPersonId = currentPersonId,
            viewModel = viewModel,
            onDismiss = { showCreateDialog = false },
            onCreated = { chat -> showCreateDialog = false; onOpenGroupChat(chat.id) },
        )
    }
}

@Composable
private fun GroupChatRow(chat: GroupChat, congregationName: String?, unreadCount: Long, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(chat.groupName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(congregationName, "${chat.participantIds.size} participants").joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val preview = when {
                    chat.lastMessageIsAttachment -> "📎 ${chat.lastMessageSenderName}: attachment"
                    chat.lastMessageText != null -> "${chat.lastMessageSenderName}: ${chat.lastMessageText}"
                    else -> "No messages yet."
                }
                Text(preview, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (chat.lastMessageAt != null) {
                    Text(formatRecordTimestamp(chat.lastMessageAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (unreadCount > 0) {
                    Box(modifier = Modifier.padding(top = 4.dp)) {
                        Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                    }
                }
            }
        }
    }
}
