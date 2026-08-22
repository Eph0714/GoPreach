package com.emfitsolutions.gopreach.ui.screens.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.ElderTitleEntity
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.AuthRepository
import com.emfitsolutions.gopreach.data.repository.ElderTitleRepository
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

data class RegularElderEnrollmentUiState(
    val name: String = "",
    val address: String = "",
    val email: String = "",
    val contact: String = "",
    val selectedElderTitleId: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val result: TempCredentials? = null,
)

/**
 * Spec §4.4 — Regular Elder enrollment (Super-Admin, Admin, or Coordinator Elder),
 * auto-assigned to the enrolling Coordinator Elder's congregation. Title comes
 * from the [ElderTitleEntity] lookup table (spec §3) so congregations can add new
 * titles without a code change.
 */
@HiltViewModel
class RegularElderEnrollmentViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    elderTitleRepository: ElderTitleRepository,
) : ViewModel() {

    val elderTitles: StateFlow<List<ElderTitleEntity>> =
        elderTitleRepository.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(RegularElderEnrollmentUiState())
    val uiState: StateFlow<RegularElderEnrollmentUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value.uppercase(), errorMessage = null) }
    fun onAddressChange(value: String) = _uiState.update { it.copy(address = value.uppercase(), errorMessage = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onContactChange(value: String) = _uiState.update { it.copy(contact = value.uppercase(), errorMessage = null) }
    fun onElderTitleSelected(id: String) = _uiState.update { it.copy(selectedElderTitleId = id, errorMessage = null) }

    fun save(enrollingPersonId: String) {
        val state = _uiState.value
        if (state.name.isBlank() || state.address.isBlank() || state.contact.isBlank() || state.selectedElderTitleId == null) {
            _uiState.update { it.copy(errorMessage = "Name, address, contact, and specific role are required.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val enrollerAssignments = roleAssignmentRepository.observeForPerson(enrollingPersonId).first()
            // Auto-assigned to the enrolling Coordinator Elder's congregation (spec §4.4);
            // Super-Admin/Admin enrolling directly fall back to their own congregation scope.
            val congregationId = enrollerAssignments.firstOrNull { assignment ->
                val role = (assignment.resolvedRoleType() as? RoleType.Admin)?.role
                role == AdminRole.COORDINATOR_ELDER || role == AdminRole.ADMIN_PER_CONGREGATION
            }?.congregationId
            if (congregationId == null) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Could not determine a congregation to assign.") }
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
                        roleType = RoleType.serialize(RoleType.Admin(AdminRole.REGULAR_ELDER)),
                        congregationId = congregationId,
                        elderTitleId = state.selectedElderTitleId,
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
