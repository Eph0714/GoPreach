package com.emfitsolutions.gopreach.ui.screens.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.AuthRepository
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import com.emfitsolutions.gopreach.data.repository.TempCredentials
import com.emfitsolutions.gopreach.domain.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoordinatorElderEnrollmentUiState(
    val name: String = "",
    val address: String = "",
    val email: String = "",
    val contact: String = "",
    /** Only used when the enroller is a Super-Admin (spec §4.3 is otherwise
     * auto-assigned to the enrolling Admin's own congregation). */
    val selectedCongregationId: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val result: TempCredentials? = null,
)

/** Spec §4.3 — Coordinator Elder enrollment, by Super-Admin or Admin. */
@HiltViewModel
class CoordinatorElderEnrollmentViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    congregationRepository: CongregationRepository,
) : ViewModel() {

    val congregations: StateFlow<List<Congregation>> =
        congregationRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(CoordinatorElderEnrollmentUiState())
    val uiState: StateFlow<CoordinatorElderEnrollmentUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value.uppercase(), errorMessage = null) }
    fun onAddressChange(value: String) = _uiState.update { it.copy(address = value.uppercase(), errorMessage = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onContactChange(value: String) = _uiState.update { it.copy(contact = value.uppercase(), errorMessage = null) }
    fun onCongregationSelected(id: String) = _uiState.update { it.copy(selectedCongregationId = id, errorMessage = null) }

    suspend fun isEnrollerSuperAdmin(enrollingPersonId: String): Boolean =
        PermissionChecker.hasAdminRole(roleAssignmentRepository.observeForPerson(enrollingPersonId).first(), AdminRole.SUPER_ADMIN)

    fun save(enrollingPersonId: String) {
        val state = _uiState.value
        if (state.name.isBlank() || state.address.isBlank() || state.contact.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Name, address, and contact are required.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val enrollerAssignments = roleAssignmentRepository.observeForPerson(enrollingPersonId).first()
            val congregationId = if (PermissionChecker.hasAdminRole(enrollerAssignments, AdminRole.SUPER_ADMIN)) {
                state.selectedCongregationId
            } else {
                enrollerAssignments.firstOrNull {
                    (it.resolvedRoleType() as? RoleType.Admin)?.role == AdminRole.ADMIN_PER_CONGREGATION
                }?.congregationId
            }
            if (congregationId == null) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Select a congregation.") }
                return@launch
            }
            val (firstName, lastName) = splitName(state.name)
            val credentials = authRepository.createAccountWithTempCredentials(
                person = Person(
                    firstName = firstName,
                    lastName = lastName,
                    address = state.address.trim(),
                    email = state.email.trim().ifBlank { null },
                    contact = state.contact.trim(),
                ),
                roleAssignment = { personId ->
                    RoleAssignment(
                        personId = personId,
                        roleType = RoleType.serialize(RoleType.Admin(AdminRole.COORDINATOR_ELDER)),
                        congregationId = congregationId,
                        status = RoleAssignmentStatus.ACTIVE,
                        dateAssigned = System.currentTimeMillis(),
                        assignedByPersonId = enrollingPersonId,
                    )
                },
                enrollingPersonId = enrollingPersonId,
            )
            _uiState.update { it.copy(isSaving = false, result = credentials) }
        }
    }
}
