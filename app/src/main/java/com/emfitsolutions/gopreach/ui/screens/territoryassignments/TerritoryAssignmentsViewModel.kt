package com.emfitsolutions.gopreach.ui.screens.territoryassignments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.location.LatLng
import com.emfitsolutions.gopreach.data.location.LocationTracker
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.TerritoryAssignment
import com.emfitsolutions.gopreach.data.model.TerritoryAssignmentBarangay
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.TerritoryAssignmentRepository
import com.emfitsolutions.gopreach.data.repository.TerritoryAssignmentResult
import com.emfitsolutions.gopreach.data.repository.TerritoryBoundaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One dashboard row — [barangays] already sorted by name, [group] carried
 * whole (not just its name/color) so the row can keep reading more of it
 * later without a second lookup. */
data class TerritoryAssignmentRow(
    val assignment: TerritoryAssignment,
    val group: Group?,
    val barangays: List<TerritoryAssignmentBarangay>,
)

@HiltViewModel
class TerritoryAssignmentsViewModel @Inject constructor(
    private val territoryAssignmentRepository: TerritoryAssignmentRepository,
    private val groupRepository: GroupRepository,
    private val territoryBoundaryRepository: TerritoryBoundaryRepository,
    private val locationTracker: LocationTracker,
    congregationRepository: CongregationRepository,
) : ViewModel() {

    /** Only needed by a Super-Admin, who isn't scoped to one congregation
     * already — same pattern as [com.emfitsolutions.gopreach.ui.screens
     * .groups.ManageGroupsViewModel.congregations]. */
    val congregations: Flow<List<Congregation>> = congregationRepository.observeAll()

    /** [searchQuery] matches Group name, municipality name, or any assigned
     * barangay's name — "Search by FS Group, municipality, or barangay". */
    fun rowsFor(congregationId: String?, searchQuery: String): Flow<List<TerritoryAssignmentRow>> =
        combine(
            territoryAssignmentRepository.observeAssignments(),
            territoryAssignmentRepository.observeBarangayClaims(),
            groupRepository.observeAll(),
        ) { assignments, claims, groups ->
            assignments
                .filter { congregationId == null || it.congregationId == congregationId }
                .map { assignment ->
                    TerritoryAssignmentRow(
                        assignment = assignment,
                        group = groups.firstOrNull { it.id == assignment.groupId },
                        barangays = claims.filter { it.assignmentId == assignment.id }.sortedBy { it.barangayName },
                    )
                }
                .filter { row ->
                    searchQuery.isBlank() ||
                        row.group?.name?.contains(searchQuery, ignoreCase = true) == true ||
                        row.assignment.muncityName.contains(searchQuery, ignoreCase = true) ||
                        row.barangays.any { it.barangayName.contains(searchQuery, ignoreCase = true) }
                }
                .sortedWith(compareBy({ it.group?.name ?: "" }, { it.assignment.muncityName }))
        }

    /** Real polygon boundary for one claimed barangay, for the "tap a
     * barangay -> show its boundary" map preview — see
     * [com.emfitsolutions.gopreach.data.repository.TerritoryBoundaryRepository]'s
     * own doc comment for why this can legitimately come back null (only
     * Nueva Vizcaya is bundled today) and why that's a graceful "not
     * available yet," not an error. */
    suspend fun boundaryGeometry(municipality: String, barangay: String): String? =
        territoryBoundaryRepository.barangayGeometry(municipality, barangay)

    /** "Add my location, then compare the distance to the selected barangay"
     * — thin pass-through to the shared [LocationTracker] (same fused-location
     * approach Share Location already uses) so [BarangayBoundaryDialog] can
     * check/request permission and fetch a fix without reaching into
     * infrastructure directly from a Composable. */
    fun hasLocationPermission(): Boolean = locationTracker.hasLocationPermission()

    fun isLocationServicesEnabled(): Boolean = locationTracker.isLocationServicesEnabled()

    suspend fun currentLocation(): LatLng? = locationTracker.getCurrentLocation()

    private val _removeResult = MutableStateFlow<TerritoryAssignmentResult?>(null)
    val removeResult: StateFlow<TerritoryAssignmentResult?> = _removeResult

    fun consumeRemoveResult() {
        _removeResult.value = null
    }

    fun remove(assignmentId: String, congregationId: String, actorPersonId: String) {
        viewModelScope.launch {
            _removeResult.value = territoryAssignmentRepository.removeAssignment(assignmentId, congregationId, actorPersonId)
        }
    }
}
