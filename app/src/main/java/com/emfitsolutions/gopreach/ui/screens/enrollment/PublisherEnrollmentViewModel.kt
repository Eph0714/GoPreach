package com.emfitsolutions.gopreach.ui.screens.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.location.LocationTracker
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.AuthRepository
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.PhilippineLocationRepository
import com.emfitsolutions.gopreach.data.repository.TempCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PublisherEnrollmentUiState(
    val lastName: String = "",
    val firstName: String = "",
    val address: String = "",
    /** "Add a dropdown for City, Municipalities, Town Barangay" — see
     * [Person.province]'s own doc comment; [isCapturingLocation]/
     * [locationError] back the optional "Use Current Location" button that
     * fills these three automatically (best-effort, still editable). */
    val province: String? = null,
    val cityMunicipality: String? = null,
    val barangay: String? = null,
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val isCapturingLocation: Boolean = false,
    val locationError: String? = null,
    val contact: String = "",
    val email: String = "",
    val category: PublisherCategory? = null,
    /** Super-Admin-only — narrows [PublisherEnrollmentViewModel.groups] to
     * that congregation's groups. Anyone scoped to a single fixed
     * congregation (Admin/Coordinator Elder/Service Overseer) never sets
     * this directly; it's derived from
     * [PublisherEnrollmentViewModel.fixedCongregationId] instead. */
    val selectedCongregationId: String? = null,
    val selectedGroupId: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val result: TempCredentials? = null,
)

/**
 * "CREATING PUBLISHER" spec — created by Super-Admin, Admin (own
 * congregation), Coordinator Elder, or Service Overseer (own congregation) —
 * no per-congregation cap.
 *
 * [fixedCongregationId] is the actual scope/security boundary, resolved once
 * by the caller (see GoPreachNavGraph) from the enrolling session's own
 * role, exactly like every other Manage screen's `fixedCongregationId`/
 * `visibleCongregationId` convention: `null` means "Super-Admin, may enroll
 * into any congregation" and shows the Select Congregation field; a real id
 * means "restricted to this one congregation."
 */
@HiltViewModel
class PublisherEnrollmentViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val locationTracker: LocationTracker,
    private val philippineLocationRepository: PhilippineLocationRepository,
    congregationRepository: CongregationRepository,
) : ViewModel() {

    private val allGroups: StateFlow<List<Group>> =
        groupRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Super-Admin only — the full Congregation list for the "Select
     * Congregation" dropdown. Unused (and never rendered) when
     * [fixedCongregationId] is non-null. */
    val congregations: StateFlow<List<Congregation>> =
        congregationRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Set once, from the nav graph, before this screen is ever composed —
     * see the class doc. */
    var fixedCongregationId: String? = null
        private set

    fun restrictTo(congregationId: String?) {
        fixedCongregationId = congregationId
    }

    private val _uiState = MutableStateFlow(PublisherEnrollmentUiState())
    val uiState: StateFlow<PublisherEnrollmentUiState> = _uiState.asStateFlow()

    /** The Group dropdown's actual options — "groups only associated in the
     * congregation" (spec): every group when nothing scopes it yet is
     * deliberately *not* one of them — an Admin/Coordinator Elder/Service
     * Overseer is always scoped ([fixedCongregationId] non-null) so this is
     * immediately narrowed for them; a Super-Admin only sees groups once
     * they've picked a Congregation. */
    val groups: StateFlow<List<Group>> = combine(allGroups, _uiState) { all, state ->
        val congregationId = fixedCongregationId ?: state.selectedCongregationId
        if (congregationId == null) emptyList() else all.filter { it.congregationId == congregationId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onLastNameChange(v: String) = _uiState.update { it.copy(lastName = v.uppercase(), errorMessage = null) }
    fun onFirstNameChange(v: String) = _uiState.update { it.copy(firstName = v.uppercase(), errorMessage = null) }
    fun onAddressChange(v: String) = _uiState.update { it.copy(address = v.uppercase(), errorMessage = null) }
    fun onContactChange(v: String) = _uiState.update { it.copy(contact = v.uppercase(), errorMessage = null) }
    fun onEmailChange(v: String) = _uiState.update { it.copy(email = v, errorMessage = null) }

    /** "Add a dropdown for City, Municipalities, Town Barangay. The
     * publisher will browse manually" — the manual half, wired to
     * [com.emfitsolutions.gopreach.ui.components.PhilippineAddressPicker]. */
    fun onAddressLevelsChanged(province: String?, cityMunicipality: String?, barangay: String?) = _uiState.update {
        it.copy(province = province, cityMunicipality = cityMunicipality, barangay = barangay, errorMessage = null)
    }

    fun hasLocationPermission(): Boolean = locationTracker.hasLocationPermission()

    /** "It can be automatic if the publisher will capture the coordinates,
     * the system will automatically fill-up the City, Municipalities, Town
     * and barangay" — same reverse-geocode-then-match-against-PSGC approach
     * as [com.emfitsolutions.gopreach.ui.screens.pipeline.PipelineViewModel
     * .saveGpsLocation]; only overwrites a level that actually resolved. */
    fun captureLocation() {
        _uiState.update { it.copy(isCapturingLocation = true, locationError = null) }
        viewModelScope.launch {
            val location = locationTracker.getCurrentLocation()
            if (location == null) {
                _uiState.update { it.copy(isCapturingLocation = false, locationError = "Could not get a GPS fix. Make sure location is turned on and try again.") }
                return@launch
            }
            val geocoded = runCatching { locationTracker.reverseGeocodeAddress(location.lat, location.lng) }.getOrNull()
            val resolved = geocoded?.let { philippineLocationRepository.resolveFromGeocode(it) }
            _uiState.update {
                it.copy(
                    isCapturingLocation = false,
                    gpsLat = location.lat,
                    gpsLng = location.lng,
                    province = resolved?.provinceName ?: it.province,
                    cityMunicipality = resolved?.muncityName ?: it.cityMunicipality,
                    barangay = resolved?.barangayName ?: it.barangay,
                )
            }
        }
    }

    /** STATUS is a single choice among all eight categories (spec: checking
     * one disables and unchecks every other one) — a single nullable field
     * naturally gives that behavior, same pattern used by every other
     * enrollment screen's mutually-exclusive checkbox group. */
    fun onCategoryToggled(category: PublisherCategory, checked: Boolean) = _uiState.update {
        it.copy(category = if (checked) category else if (it.category == category) null else it.category, errorMessage = null)
    }

    /** Super-Admin only — picking a different Congregation clears whatever
     * Group was already selected, since it almost certainly belonged to the
     * previous congregation and silently keeping it would let a Publisher
     * end up in a Group that doesn't match their selected Congregation. */
    fun onCongregationSelected(id: String) = _uiState.update {
        it.copy(selectedCongregationId = id, selectedGroupId = null, errorMessage = null)
    }

    fun onGroupSelected(id: String) = _uiState.update { it.copy(selectedGroupId = id, errorMessage = null) }

    fun save(enrollingPersonId: String) {
        val state = _uiState.value
        // Super-Admin (fixedCongregationId == null) must pick a Congregation
        // before a Group even becomes selectable in the UI, but re-check here
        // too rather than trust that alone.
        if (fixedCongregationId == null && state.selectedCongregationId == null) {
            _uiState.update { it.copy(errorMessage = "Select a congregation.") }
            return
        }
        if (state.lastName.isBlank() || state.firstName.isBlank() || state.address.isBlank() || state.contact.isBlank() ||
            state.selectedGroupId == null || state.category == null ||
            state.province.isNullOrBlank() || state.cityMunicipality.isNullOrBlank() || state.barangay.isNullOrBlank()
        ) {
            _uiState.update { it.copy(errorMessage = "Last name, first name, address, Province/City, Municipality, Barangay, contact, group, and status are all required.") }
            return
        }
        val group = groups.value.firstOrNull { it.id == state.selectedGroupId }
        if (group == null || (fixedCongregationId != null && group.congregationId != fixedCongregationId)) {
            // The second half of that check is a defense-in-depth guard, not
            // just a UI nicety: it's the same "never trust a caller-supplied
            // congregation/group id without re-verifying it against the
            // session's own authorized scope" rule this app applies
            // everywhere else (see PermissionChecker/DashboardStatsViewModel).
            _uiState.update { it.copy(errorMessage = "Selected group not found.") }
            return
        }
        val category = state.category
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val credentials = authRepository.createAccountWithTempCredentials(
                person = Person(
                    lastName = state.lastName.trim(),
                    firstName = state.firstName.trim(),
                    address = state.address.trim(),
                    province = state.province,
                    cityMunicipality = state.cityMunicipality,
                    barangay = state.barangay,
                    gpsLat = state.gpsLat,
                    gpsLng = state.gpsLng,
                    contact = state.contact.trim(),
                    email = state.email.trim().ifBlank { null },
                ),
                roleAssignment = { personId ->
                    RoleAssignment(
                        personId = personId,
                        roleType = RoleType.serialize(RoleType.Publisher(category)),
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
