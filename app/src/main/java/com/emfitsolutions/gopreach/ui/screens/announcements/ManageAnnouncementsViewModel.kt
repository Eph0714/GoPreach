package com.emfitsolutions.gopreach.ui.screens.announcements

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.Announcement
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.repository.AnnouncementRepository
import com.emfitsolutions.gopreach.data.repository.AnnouncementSeenStore
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Announcement Module" — Super-Admin/Admin/Coordinator Elder manage
 * (create/edit/delete, own congregation for Admin/Coordinator Elder), every
 * Publisher in that congregation reads. This one ViewModel/screen serves
 * both sides — see [com.emfitsolutions.gopreach.ui.screens.announcements
 * .AnnouncementsScreen]'s `readOnly` parameter.
 */
@HiltViewModel
class ManageAnnouncementsViewModel @Inject constructor(
    private val announcementRepository: AnnouncementRepository,
    private val auditLogRepository: AuditLogRepository,
    private val announcementSeenStore: AnnouncementSeenStore,
    congregationRepository: CongregationRepository,
) : ViewModel() {

    val congregations: StateFlow<List<Congregation>> =
        congregationRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun rowsFor(congregationId: String?): Flow<List<Announcement>> =
        announcementRepository.observeAll().map { list ->
            list.filter { congregationId == null || it.congregationId == congregationId }
                .sortedByDescending { it.createdAt }
        }

    /** Live unseen count for the notification balloon — every announcement
     * in [congregationId] created after this Publisher last opened the
     * announcements screen (see [markSeen]). */
    fun unseenCountFor(congregationId: String?, personId: String): Flow<Int> =
        combine(rowsFor(congregationId), announcementSeenStore.lastSeenAtByPerson) { rows, seenMap ->
            val lastSeenAt = seenMap[personId] ?: announcementSeenStore.lastSeenAt(personId)
            rows.count { it.createdAt > lastSeenAt }
        }

    fun markSeen(personId: String) = announcementSeenStore.markSeenNow(personId)

    /** Saves the announcement's text fields, then applies the image change,
     * if any, in the same coroutine — a brand-new announcement has no id
     * (and therefore no Storage path) until the first save completes, so an
     * image pick has to wait for that regardless of which field changed.
     * [removeImage] clears an existing image (spec: "the image can be
     * cleared or removed"); [pickedImageUri] takes priority if both are
     * somehow set (a fresh pick replaces whatever was there, removal or not). */
    fun saveWithImage(
        announcement: Announcement,
        pickedImageUri: Uri?,
        removeImage: Boolean,
        actorPersonId: String,
    ) {
        val isNew = announcement.id.isBlank()
        viewModelScope.launch {
            var saved = announcementRepository.save(
                announcement.copy(lastEditedByPersonId = actorPersonId, lastEditedAt = System.currentTimeMillis()),
            )
            if (pickedImageUri != null) {
                val url = announcementRepository.uploadImage(saved.id, pickedImageUri)
                saved = announcementRepository.save(saved.copy(imageUrl = url))
            } else if (removeImage && saved.imageUrl != null) {
                announcementRepository.deleteImage(saved.id)
                saved = announcementRepository.save(saved.copy(imageUrl = null))
            }
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = if (isNew) "CREATE_ANNOUNCEMENT" else "EDIT_ANNOUNCEMENT",
                targetType = "Announcement",
                targetId = saved.id,
                congregationId = saved.congregationId,
            )
        }
    }

    fun delete(announcement: Announcement, actorPersonId: String) {
        viewModelScope.launch {
            announcementRepository.delete(announcement.id)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "DELETE_ANNOUNCEMENT",
                targetType = "Announcement",
                targetId = announcement.id,
                congregationId = announcement.congregationId,
            )
        }
    }
}
