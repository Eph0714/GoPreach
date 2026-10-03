package com.emfitsolutions.gopreach.ui.screens.territoryassignments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.TerritoryAssignment
import com.emfitsolutions.gopreach.data.model.TerritoryAssignmentBarangay
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.PhilippineLocationRepository
import com.emfitsolutions.gopreach.data.repository.PsgcOption
import com.emfitsolutions.gopreach.data.repository.TerritoryAssignmentRepository
import com.emfitsolutions.gopreach.data.repository.TerritoryAssignmentResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A barangay row for the Step 3 checklist — [takenByGroupName] non-null
 * means another assignment already claims it (null when editing and it's
 * claimed by *this same* assignment — see [TerritoryAssignmentWizardViewModel
 * .takenBarangays]'s own doc comment). */
data class BarangayChecklistRow(val option: PsgcOption, val takenByGroupName: String?)

data class WizardUiState(
    val isSaving: Boolean = false,
    /** The result of the last Save attempt — [TerritoryAssignmentResult
     * .Success] dismisses the wizard; [Conflict]/[Offline]/[Error] render as
     * a blocking inline message on the confirm step, same role [FormDialog]'s
     * own `errorMessage` plays elsewhere in this app. */
    val saveResult: TerritoryAssignmentResult? = null,
)

@HiltViewModel
class TerritoryAssignmentWizardViewModel @Inject constructor(
    private val territoryAssignmentRepository: TerritoryAssignmentRepository,
    private val groupRepository: GroupRepository,
    private val philippineLocationRepository: PhilippineLocationRepository,
    congregationRepository: CongregationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WizardUiState())
    val uiState: StateFlow<WizardUiState> = _uiState

    /** Super-Admin only — picking which congregation a brand-new assignment
     * belongs to; unused (and hidden by the screen) once [fixedCongregationId]
     * is non-null, or when editing an existing assignment (congregationId is
     * immutable on edit, matching TerritoryAssignmentRepository.updateAssignment). */
    val congregations: Flow<List<Congregation>> = congregationRepository.observeAll()

    suspend fun getAssignment(assignmentId: String): TerritoryAssignment? =
        territoryAssignmentRepository.observeAssignments().first().firstOrNull { it.id == assignmentId }

    fun groupsFor(congregationId: String): Flow<List<Group>> =
        groupRepository.observeAll().map { groups ->
            groups.filter { it.congregationId == congregationId && it.status == RecordStatus.ACTIVE }.sortedBy { it.name }
        }

    suspend fun searchProvinces(query: String): List<PsgcOption> = philippineLocationRepository.searchProvinces(query)
    suspend fun searchMunicipalities(provinceId: Int, query: String): List<PsgcOption> =
        philippineLocationRepository.searchCitiesMunicipalities(provinceId, query)
    suspend fun searchBarangays(muncityId: Int, query: String): List<PsgcOption> =
        philippineLocationRepository.searchBarangays(muncityId, query)
    suspend fun provinceOf(muncityId: Int): PsgcOption? = philippineLocationRepository.provinceOfMuncity(muncityId)

    /** Which of [barangays] are already claimed by a DIFFERENT assignment in
     * this congregation — client-side "already taken, disabled" hint for the
     * Step 3 checklist. [excludeAssignmentId] is this same wizard's own
     * assignment when editing, so its own already-claimed barangays don't
     * show as unavailable to itself. This is UX only; the actual,
     * unbypassable guarantee is the transaction inside
     * [TerritoryAssignmentRepository] at Save. */
    suspend fun takenBarangays(congregationId: String, excludeAssignmentId: String?): Map<Int, String> =
        territoryAssignmentRepository.observeBarangayClaims().first()
            .filter { it.congregationId == congregationId && it.assignmentId != excludeAssignmentId }
            .associate { it.barangayId to it.groupName }

    suspend fun existingBarangaysFor(assignmentId: String): List<TerritoryAssignmentBarangay> =
        territoryAssignmentRepository.observeBarangayClaims().first().filter { it.assignmentId == assignmentId }

    fun createAssignment(
        congregationId: String, groupId: String, groupName: String,
        provinceId: Int, provinceName: String, muncityId: Int, muncityName: String,
        barangays: List<PsgcOption>, actorPersonId: String,
    ) {
        _uiState.update { it.copy(isSaving = true, saveResult = null) }
        viewModelScope.launch {
            val result = territoryAssignmentRepository.createAssignment(
                congregationId, groupId, groupName, provinceId, provinceName, muncityId, muncityName, barangays, actorPersonId,
            )
            _uiState.update { it.copy(isSaving = false, saveResult = result) }
        }
    }

    fun updateAssignment(
        assignmentId: String, congregationId: String, groupId: String, groupName: String,
        provinceId: Int, provinceName: String, muncityId: Int, muncityName: String,
        barangays: List<PsgcOption>, actorPersonId: String,
    ) {
        _uiState.update { it.copy(isSaving = true, saveResult = null) }
        viewModelScope.launch {
            val result = territoryAssignmentRepository.updateAssignment(
                assignmentId, congregationId, groupId, groupName, provinceId, provinceName, muncityId, muncityName, barangays, actorPersonId,
            )
            _uiState.update { it.copy(isSaving = false, saveResult = result) }
        }
    }

    fun consumeSaveResult() {
        _uiState.update { it.copy(saveResult = null) }
    }
}
