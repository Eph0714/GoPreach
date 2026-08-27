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

data class RegularElderEnrollmentUiState(
    val firstName: String = "",
    val lastName: String = "",
    val address: String = "",
    val email: String = "",
    val contact: String = "",
    /** Only used when the enroller is a Super-Admin — Admin/Coordinator
     * Elder are otherwise auto-assigned to their own congregation (spec:
     * "The Admin can only Create Regular Elder under the congregation he
     * belong"). */
    val selectedCongregationId: String? = null,
    /** "Select Role" checkboxes. [isGroupOverseer] sets
     * [RegularElderRole.GROUP_OVERSEER] directly on this Regular Elder's own
     * primary RoleAssignment — unlike Coordinator Elder/Ministerial Servant
     * enrollment, there's no separate "additional role" here since Regular
     * Elder *is* the primary role already. Group Servant/Group Assistant
     * aren't offered on this screen (spec lists only Group Overseer here) —
     * those two slots are filled via Ministerial Servant enrollment or later
     * through Manage Groups. */
    val isGroupOverseer: Boolean = false,
    /** Mutually exclusive — checking one clears the other two (spec: "If
     * Check, [the others] is disabled and uncheck"). `null` means none
     * selected; this Regular Elder gets no Publisher-category RoleAssignment
     * at all. */
    val publisherCategory: PublisherCategory? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val result: TempCredentials? = null,
)

/**
 * Regular Elder enrollment — reachable by Super-Admin, Admin (own
 * congregation only), and Coordinator Elder (own congregation), same access
 * set as Coordinator Elder/Service Overseer/Ministerial Servant enrollment.
 * There is no per-congregation cap: "there can be multiple Regular Elder in
 * every congregation."
 */
@HiltViewModel
class RegularElderEnrollmentViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    congregationRepository: CongregationRepository,
) : ViewModel() {

    val congregations: StateFlow<List<Congregation>> =
        congregationRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(RegularElderEnrollmentUiState())
    val uiState: StateFlow<RegularElderEnrollmentUiState> = _uiState.asStateFlow()

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
                        roleType = RoleType.serialize(RoleType.Admin(AdminRole.REGULAR_ELDER)),
                        congregationId = congregationId,
                        regularElderRole = if (state.isGroupOverseer) RegularElderRole.GROUP_OVERSEER else null,
                        status = RoleAssignmentStatus.ACTIVE,
                        dateAssigned = now,
                        assignedByPersonId = enrollingPersonId,
                    )
                },
                enrollingPersonId = enrollingPersonId,
            )
            state.publisherCategory?.let { category ->
                // saveNow, not save — same reasoning as the primary role
                // assignment in AuthRepository.createAccountWithTempCredentials:
                // this new account may sign in on a different device before
                // this one's next manual sync, and their role should already
                // be correct the moment they do.
                roleAssignmentRepository.saveNow(
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
