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

data class CoordinatorElderEnrollmentUiState(
    val firstName: String = "",
    val lastName: String = "",
    val address: String = "",
    val email: String = "",
    val contact: String = "",
    /** Only used when the enroller is a Super-Admin (spec §4.3 is otherwise
     * auto-assigned to the enrolling Admin's own congregation). */
    val selectedCongregationId: String? = null,
    /** "Select Role" checkboxes (additional, simultaneous roles on top of
     * the Coordinator Elder admin role itself) — independent of the three
     * publisher categories below, so a Coordinator Elder can also be a
     * Group Overseer *and* a Regular Pioneer at once. No specific Group is
     * picked here, deliberately: [com.emfitsolutions.gopreach.data.model
     * .RoleAssignment.groupId] is left unset here exactly like a freshly
     * enrolled Regular Elder's already is (see RegularElderEnrollmentViewModel) —
     * it's filled in later when an admin places them into one of a Group's
     * three slots via Manage Groups. */
    val isGroupOverseer: Boolean = false,
    /** Mutually exclusive — checking one clears the other two (spec: "If
     * checked, the others are disabled and unchecked"). `null` means none
     * selected; this Coordinator Elder gets no Publisher-category
     * RoleAssignment at all. */
    val publisherCategory: PublisherCategory? = null,
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

    fun onFirstNameChange(value: String) = _uiState.update { it.copy(firstName = value.uppercase(), errorMessage = null) }
    fun onLastNameChange(value: String) = _uiState.update { it.copy(lastName = value.uppercase(), errorMessage = null) }
    fun onAddressChange(value: String) = _uiState.update { it.copy(address = value.uppercase(), errorMessage = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onContactChange(value: String) = _uiState.update { it.copy(contact = value.uppercase(), errorMessage = null) }
    fun onCongregationSelected(id: String) = _uiState.update { it.copy(selectedCongregationId = id, errorMessage = null) }

    fun onGroupOverseerToggled(checked: Boolean) = _uiState.update { it.copy(isGroupOverseer = checked) }

    /** A single nullable field naturally gives the "checking one clears the
     * other two" behavior the spec asks for — checking one just replaces
     * whatever was selected; unchecking the currently-selected one clears it. */
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
                enrollerAssignments.firstOrNull {
                    (it.resolvedRoleType() as? RoleType.Admin)?.role == AdminRole.ADMIN_PER_CONGREGATION
                }?.congregationId
            }
            if (congregationId == null) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Select a congregation.") }
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
                        roleType = RoleType.serialize(RoleType.Admin(AdminRole.COORDINATOR_ELDER)),
                        congregationId = congregationId,
                        status = RoleAssignmentStatus.ACTIVE,
                        dateAssigned = now,
                        assignedByPersonId = enrollingPersonId,
                    )
                },
                enrollingPersonId = enrollingPersonId,
            )
            // "Select Role" checkboxes — additional, simultaneous
            // RoleAssignments on top of the Coordinator Elder role itself
            // created above. Neither carries a groupId yet, same as a
            // freshly enrolled Regular Elder's own RoleAssignment doesn't —
            // that's filled in later when an admin places this person into
            // one of a Group's three slots via Manage Groups.
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
