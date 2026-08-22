package com.emfitsolutions.gopreach.ui.screens.congregations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Spec §3/§5.1 — "Manage Congregation Master File", Super-Admin only. */
@HiltViewModel
class ManageCongregationsViewModel @Inject constructor(
    private val congregationRepository: CongregationRepository,
    private val auditLogRepository: AuditLogRepository,
) : ViewModel() {

    val congregations: StateFlow<List<Congregation>> = congregationRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun update(congregation: Congregation, updatedByPersonId: String) {
        viewModelScope.launch {
            congregationRepository.save(congregation)
            auditLogRepository.log(
                actorPersonId = updatedByPersonId,
                action = "UPDATE_CONGREGATION",
                targetType = "Congregation",
                targetId = congregation.id,
                congregationId = congregation.id,
            )
        }
    }

    fun delete(congregationId: String, deletedByPersonId: String) {
        viewModelScope.launch {
            congregationRepository.delete(congregationId)
            auditLogRepository.log(
                actorPersonId = deletedByPersonId,
                action = "DELETE_CONGREGATION",
                targetType = "Congregation",
                targetId = congregationId,
                congregationId = congregationId,
            )
        }
    }
}
