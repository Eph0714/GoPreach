package com.emfitsolutions.gopreach.ui.screens.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RegularElderRole
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

data class ServiceOverseerEnrollmentUiState(
    val firstName: String = "",
    val lastName: String = "",
    val address: String = "",
    val email: String = "",
    val contact: String = "",
    /** Only used when the enroller is a Super-Admin — Admin/Coordinator
     * Elder are otherwise auto-assigned to their own congregation. */
    val selectedCongregationId: String? = null,
    /** "Select Role" checkboxes — see [CoordinatorElderEnrollmentUiState]'s
     * matching fields for the full reasoning (no groupId set here either,
     * filled in later via Manage Groups). */
    val isGroupOverseer: Boolean = false,
    val publisherCategory: PublisherCategory? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val result: TempCredentials? = null,
)

/**
 * New Service Overseer enrollment — reachable by Super-Admin, Admin (own
 * congregation only), and Coordinator Elder (own congregation), unlike
 * Coordinator Elder enrollment itself, which a Coordinator Elder cannot
 * reach. One Service Overseer per congregation is the goal ("there must be
 * 1 Service Overseer in every congregation"); enforced here as "at most one
 * *active* Service Overseer per congregation" at enrollment time, not a
 * hard invariant elsewhere in the app (a congregation may simply not have
 * one yet).
 */
@HiltViewModel
class ServiceOverseerEnrollmentViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    congregationRepository: CongregationRepository,
) : ViewModel() {

    val congregations: StateFlow<List<Congregation>> =
        congregationRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(ServiceOverseerEnrollmentUiState())
    val uiState: StateFlow<ServiceOverseerEnrollmentUiState> = _uiState.asStateFlow()

    fun onFirstNameChange(value: String) = _uiState.update { it.copy(firstName = value.uppercase(), errorMessage = null) }
    fun onLastNameChange(value: String) = _uiState.update { it.copy(lastName = value.uppercase(), errorMessage = null) }
    fun onAddressChange(value: String) = _uiState.update { it.copy(address = value.uppercase(), errorMessage = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onContactChange(value: String) = _uiState.update { it.copy(contact = value.uppercase(), errorMessage = null) }
    fun onCongregationSelected(id: String) = _uiState.update { it.copy(selectedCongregationId = id, errorMessage = null) }

    fun onGroupOverseerToggled(checked: Boolean) = _uiState.update { it.copy(isGroupOverseer = checked) }

    fun onPublisherCategoryToggled(category: PublisherCategory, checked: Boolean) = _uiState.update {
        it.copy(publisherCategory = if (checked) category else if (it.publisherCategory == category) null else it.publisherCategory)
    }

    suspend fun isEnrollerSuperAdmin(enrollingPersonId: String): Boolean =
        PermissionChecker.hasAdminRole(roleAssignmentRepository.observeForPerson(enrollingPersonId).first(), AdminRole.SUPER_ADMIN)

    fun save(enrollingPersonId: String) {
        val state = _uiState.value
        if (state.firstName.isBlank() || state.lastName.isBlank() || state.address.isBlank() || state.contact.isBlank()) {
            _uiState.update { it.copy(errorMessage = "First name, last name, address, and contact are required.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val enrollerAssignments = roleAssignmentRepository.observeForPerson(enrollingPersonId).first()
            val congregationId = if (PermissionChecker.hasAdminRole(enrollerAssignments, AdminRole.SUPER_ADMIN)) {
                state.selectedCongregationId
            } else {
                // Unlike Coordinator Elder enrollment (Admin only), this
                // screen is also reachable by a Coordinator Elder — so both
                // of their own-congregation roles need checking here.
                enrollerAssignments.firstOrNull {
                    val role = (it.resolvedRoleType() as? RoleType.Admin)?.role
                    role == AdminRole.ADMIN_PER_CONGREGATION || role == AdminRole.COORDINATOR_ELDER
                }?.congregationId
            }
            if (congregationId == null) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Select a congregation.") }
                return@launch
            }
            // "There must be 1 Service Overseer in every congregation" —
            // enforced as "at most one active" here, so a second one is
            // never created by accident for the same congregation.
            val allAssignments = roleAssignmentRepository.observeAll().first()
            val alreadyHasServiceOverseer = allAssignments.any {
                it.status == RoleAssignmentStatus.ACTIVE &&
                    it.congregationId == congregationId &&
                    (it.resolvedRoleTypeOrNull() as? RoleType.Admin)?.role == AdminRole.SERVICE_OVERSEER
            }
            if (alreadyHasServiceOverseer) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "This congregation already has an active Service Overseer.") }
                return@launch
            }
            val now = System.currentTimeMillis()
            val credentials = authRepository.createAccountWithTempCredentials(
                person = Person(
                    firstName = state.firstName.trim(),
                    lastName = state.lastName.trim(),
                    address = state.address.trim(),
                    email = state.email.trim().ifBlank { null },
                    contact = state.contact.trim(),
                ),
                roleAssignment = { personId ->
                    RoleAssignment(
                        personId = personId,
                        roleType = RoleType.serialize(RoleType.Admin(AdminRole.SERVICE_OVERSEER)),
                        congregationId = congregationId,
                        status = RoleAssignmentStatus.ACTIVE,
                        dateAssigned = now,
                        assignedByPersonId = enrollingPersonId,
                    )
                },
                enrollingPersonId = enrollingPersonId,
            )
            if (state.isGroupOverseer) {
                roleAssignmentRepository.save(
                    RoleAssignment(
                        personId = credentials.personId,
                        roleType = RoleType.serialize(RoleType.Admin(AdminRole.REGULAR_ELDER)),
                        congregationId = congregationId,
                        regularElderRole = RegularElderRole.GROUP_OVERSEER,
                        status = RoleAssignmentStatus.ACTIVE,
                        dateAssigned = now,
                        assignedByPersonId = enrollingPersonId,
                    )
                )
            }
            state.publisherCategory?.let { category ->
                roleAssignmentRepository.save(
                    RoleAssignment(
                        personId = credentials.personId,
                        roleType = RoleType.serialize(RoleType.Publisher(category)),
                        congregationId = congregationId,
                        status = RoleAssignmentStatus.ACTIVE,
                        dateAssigned = now,
                        assignedByPersonId = enrollingPersonId,
                    )
                )
            }
            _uiState.update { it.copy(isSaving = false, result = credentials) }
        }
    }
}
