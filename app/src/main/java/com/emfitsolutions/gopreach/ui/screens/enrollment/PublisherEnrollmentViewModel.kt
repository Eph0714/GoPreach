package com.emfitsolutions.gopreach.ui.screens.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.Gender
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.AuthRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.TempCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PublisherEnrollmentUiState(
    val lastName: String = "",
    val firstName: String = "",
    val middleInitial: String = "",
    val extensionName: String = "",
    val address: String = "",
    val gender: Gender? = null,
    val contact: String = "",
    val contactPerson: String = "",
    val contactPersonNumber: String = "",
    val category: PublisherCategory = PublisherCategory.REGULAR_PUBLISHER,
    val selectedGroupId: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val result: TempCredentials? = null,
)

/**
 * Spec §4.6 — Publisher Master File. Created by Super-Admin, Admin, or
 * Coordinator Elder; the Coordinator Elder maintains the congregation's full
 * file. The group's overseeing Regular Elder ([Group.regularElderPersonId]) comes
 * along with the group choice rather than being picked separately.
 */
@HiltViewModel
class PublisherEnrollmentViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    groupRepository: GroupRepository,
) : ViewModel() {

    val groups: StateFlow<List<Group>> =
        groupRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(PublisherEnrollmentUiState())
    val uiState: StateFlow<PublisherEnrollmentUiState> = _uiState.asStateFlow()

    fun onLastNameChange(v: String) = _uiState.update { it.copy(lastName = v.uppercase(), errorMessage = null) }
    fun onFirstNameChange(v: String) = _uiState.update { it.copy(firstName = v.uppercase(), errorMessage = null) }
    fun onMiddleInitialChange(v: String) = _uiState.update { it.copy(middleInitial = v.uppercase()) }
    fun onExtensionNameChange(v: String) = _uiState.update { it.copy(extensionName = v.uppercase()) }
    fun onAddressChange(v: String) = _uiState.update { it.copy(address = v.uppercase(), errorMessage = null) }
    fun onGenderChange(v: Gender) = _uiState.update { it.copy(gender = v, errorMessage = null) }
    fun onContactChange(v: String) = _uiState.update { it.copy(contact = v.uppercase(), errorMessage = null) }
    fun onContactPersonChange(v: String) = _uiState.update { it.copy(contactPerson = v.uppercase()) }
    fun onContactPersonNumberChange(v: String) = _uiState.update { it.copy(contactPersonNumber = v.uppercase()) }
    fun onCategoryChange(v: PublisherCategory) = _uiState.update { it.copy(category = v) }
    fun onGroupSelected(id: String) = _uiState.update { it.copy(selectedGroupId = id, errorMessage = null) }

    fun save(enrollingPersonId: String) {
        val state = _uiState.value
        if (state.lastName.isBlank() || state.firstName.isBlank() || state.address.isBlank() ||
            state.gender == null || state.contact.isBlank() || state.selectedGroupId == null
        ) {
            _uiState.update { it.copy(errorMessage = "Last name, first name, address, gender, contact, and group are required.") }
            return
        }
        val group = groups.value.firstOrNull { it.id == state.selectedGroupId }
        if (group == null) {
            _uiState.update { it.copy(errorMessage = "Selected group not found.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val credentials = authRepository.createAccountWithTempCredentials(
                person = Person(
                    lastName = state.lastName.trim(),
                    firstName = state.firstName.trim(),
                    middleInitial = state.middleInitial.trim().ifBlank { null },
                    extensionName = state.extensionName.trim().ifBlank { null },
                    address = state.address.trim(),
                    gender = state.gender,
                    contact = state.contact.trim(),
                    contactPerson = state.contactPerson.trim().ifBlank { null },
                    contactPersonNumber = state.contactPersonNumber.trim().ifBlank { null },
                ),
                roleAssignment = { personId ->
                    RoleAssignment(
                        personId = personId,
                        roleType = RoleType.serialize(RoleType.Publisher(state.category)),
                        congregationId = group.congregationId,
                        groupId = group.id,
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
