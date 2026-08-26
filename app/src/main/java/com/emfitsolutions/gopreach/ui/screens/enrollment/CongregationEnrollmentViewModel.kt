package com.emfitsolutions.gopreach.ui.screens.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CongregationEnrollmentUiState(
    val name: String = "",
    val address: String = "",
    val code: String = "",
    /** "Language(s) Used" — optional, multiple, free-form (e.g. "Iloko", "Ibanag"). */
    val languages: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
)

/** Spec §4.1 — Super-Admin only. */
@HiltViewModel
class CongregationEnrollmentViewModel @Inject constructor(
    private val congregationRepository: CongregationRepository,
    private val auditLogRepository: AuditLogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CongregationEnrollmentUiState())
    val uiState: StateFlow<CongregationEnrollmentUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value.uppercase(), errorMessage = null) }
    fun onAddressChange(value: String) = _uiState.update { it.copy(address = value.uppercase(), errorMessage = null) }
    fun onCodeChange(value: String) = _uiState.update { it.copy(code = value.uppercase(), errorMessage = null) }
    fun onAddLanguage(value: String) = _uiState.update { it.copy(languages = it.languages + value) }
    fun onRemoveLanguage(value: String) = _uiState.update { it.copy(languages = it.languages - value) }

    fun save(createdByPersonId: String) {
        val state = _uiState.value
        if (state.name.isBlank() || state.address.isBlank() || state.code.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Name, address, and code are required.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            if (!congregationRepository.isCodeAvailable(state.code.trim())) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "That congregation code is already in use.") }
                return@launch
            }
            val congregation = congregationRepository.save(
                Congregation(
                    name = state.name.trim(),
                    address = state.address.trim(),
                    code = state.code.trim(),
                    languages = state.languages,
                    createdAt = System.currentTimeMillis(),
                    createdByPersonId = createdByPersonId,
                )
            )
            auditLogRepository.log(
                actorPersonId = createdByPersonId,
                action = "CREATE_CONGREGATION",
                targetType = "Congregation",
                targetId = congregation.id,
                congregationId = congregation.id,
            )
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }
}
