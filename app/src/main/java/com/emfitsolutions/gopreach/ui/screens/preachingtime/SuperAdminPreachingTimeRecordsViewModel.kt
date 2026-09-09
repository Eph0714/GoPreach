package com.emfitsolutions.gopreach.ui.screens.preachingtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PreachingTimeRecord
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.PreachingTimeRecordRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import com.emfitsolutions.gopreach.ui.components.DateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** One row of the Super-Admin table — a [PreachingTimeRecord] joined against
 * its owning Publisher and Congregation, plus that Publisher's *current*
 * Status Category (spec §1: "Auxiliary/Regular Pioneer status, if
 * applicable") — this per-day log has no category field of its own (see
 * [PreachingTimeRecord]'s own doc comment on why it's a separate, simpler
 * module than [com.emfitsolutions.gopreach.data.model.MonthlyReport]), so
 * this is resolved live off the Publisher's [RoleType.Publisher], same as
 * every other screen that shows a Publisher's category alongside a record
 * that doesn't itself carry one. */
data class SuperAdminPreachingTimeRow(
    val record: PreachingTimeRecord,
    val person: Person,
    val congregationName: String,
    val category: PublisherCategory?,
)

data class SuperAdminPreachingTimeUiState(
    val rows: List<SuperAdminPreachingTimeRow> = emptyList(),
    val congregations: List<Congregation> = emptyList(),
    val dateRange: DateRange = DateRange.thisMonth(),
    val selectedCongregationId: String? = null,
    val searchQuery: String = "",
    val showInactive: Boolean = false,
    val isLoading: Boolean = true,
) {
    val totalHours: Double get() = rows.sumOf { it.record.hoursConsumed }
}

/**
 * "Preaching Time Records — Super Admin Management Module" — a Super-Admin-
 * only, cross-congregation counterpart to
 * [com.emfitsolutions.gopreach.ui.screens.preachingtime.PreachingTimeRecordScreen],
 * which is always scoped to one signed-in Publisher's own records. Same
 * repositories, save/setStatus/permanentlyDelete logic, and Firestore
 * collection as that screen (spec §14: "reuse existing structures... instead
 * of creating duplicate systems") — the only things genuinely new here are
 * the unscoped, joined-with-Publisher-and-Congregation data source, the
 * Congregation -> Publisher cascading picker a brand-new record needs (a
 * Super-Admin has no "own congregation" to default to), and the separate,
 * more strongly-worded Force Delete action (spec §7-§9) alongside the
 * existing soft/hard [com.emfitsolutions.gopreach.ui.components.DeleteChoiceDialog]
 * pair every other Manage screen already uses.
 *
 * Real congregation-based data isolation for every non-Super-Admin role
 * (spec §10) is unaffected by anything here — this screen (and its nav
 * route) is only ever reachable by a Super-Admin session to begin with (see
 * SidePanel's `isSuperAdmin` gate), and the one truly server-enforced part
 * of spec §8 (`delete` requiring Super-Admin) lives in firestore.rules'
 * `preachingTimeRecords` rule, not here — this ViewModel has no way to make
 * a client-side check unbypassable on its own.
 */
@HiltViewModel
class SuperAdminPreachingTimeRecordsViewModel @Inject constructor(
    private val recordRepository: PreachingTimeRecordRepository,
    private val personRepository: PersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val congregationRepository: CongregationRepository,
    private val auditLogRepository: AuditLogRepository,
) : ViewModel() {

    private val _dateRange = MutableStateFlow(DateRange.thisMonth())
    private val _selectedCongregationId = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _showInactive = MutableStateFlow(false)

    fun setDateRange(range: DateRange) = _dateRange.update { range }
    fun selectCongregation(congregationId: String?) = _selectedCongregationId.update { congregationId }
    fun setSearchQuery(query: String) = _searchQuery.update { query }
    fun setShowInactive(show: Boolean) = _showInactive.update { show }

    private data class RawData(
        val records: List<PreachingTimeRecord>,
        val people: List<Person>,
        val congregations: List<Congregation>,
        val categoryByPersonId: Map<String, PublisherCategory>,
    )

    private data class Filters(
        val dateRange: DateRange,
        val congregationId: String?,
        val searchQuery: String,
        val showInactive: Boolean,
    )

    private val rawData = combine(
        recordRepository.observeAll(),
        personRepository.observeAll(),
        congregationRepository.observeAll(),
        roleAssignmentRepository.observeAll(),
    ) { records, people, congregations, assignments ->
        val categoryByPersonId = assignments
            .filter { it.status == RoleAssignmentStatus.ACTIVE }
            .mapNotNull { a -> (a.resolvedRoleTypeOrNull() as? RoleType.Publisher)?.let { a.personId to it.category } }
            .toMap()
        RawData(records, people, congregations, categoryByPersonId)
    }

    private val filters = combine(_dateRange, _selectedCongregationId, _searchQuery, _showInactive) {
            dateRange, congregationId, query, showInactive ->
        Filters(dateRange, congregationId, query, showInactive)
    }

    /** Publishers belonging to [congregationId] — feeds the Add dialog's
     * cascading Congregation -> Publisher picker (spec §4). Empty until a
     * Congregation is actually picked, same "nothing to show yet" contract
     * as every other cascading picker in this app. */
    fun publishersFor(congregationId: String?): Flow<List<Person>> = combine(
        roleAssignmentRepository.observeAll(),
        personRepository.observeAll(),
    ) { assignments, people ->
        if (congregationId == null) return@combine emptyList()
        assignments
            .filter { it.status == RoleAssignmentStatus.ACTIVE && it.congregationId == congregationId && it.resolvedRoleTypeOrNull() is RoleType.Publisher }
            .mapNotNull { a -> people.firstOrNull { it.id == a.personId } }
            .distinctBy { it.id }
            .sortedBy { it.fullName }
    }

    val uiState: StateFlow<SuperAdminPreachingTimeUiState> = combine(rawData, filters) { raw, f ->
        val rows = raw.records
            .filter { f.showInactive || it.status == RecordStatus.ACTIVE }
            .filter { f.congregationId == null || it.congregationId == f.congregationId }
            .filter { f.dateRange.contains(it.date) }
            .mapNotNull { record ->
                val person = raw.people.firstOrNull { it.id == record.publisherPersonId } ?: return@mapNotNull null
                val congregationName = raw.congregations.firstOrNull { it.id == record.congregationId }?.name ?: "—"
                SuperAdminPreachingTimeRow(record, person, congregationName, raw.categoryByPersonId[record.publisherPersonId])
            }
            .filter { row ->
                f.searchQuery.isBlank() ||
                    row.person.fullName.contains(f.searchQuery, ignoreCase = true) ||
                    row.person.id.contains(f.searchQuery, ignoreCase = true) ||
                    row.congregationName.contains(f.searchQuery, ignoreCase = true)
            }
            .sortedWith(compareBy<SuperAdminPreachingTimeRow> { it.congregationName }.thenBy { it.person.fullName }.thenByDescending { it.record.date })

        SuperAdminPreachingTimeUiState(
            rows = rows,
            congregations = raw.congregations.sortedBy { it.name },
            dateRange = f.dateRange,
            selectedCongregationId = f.congregationId,
            searchQuery = f.searchQuery,
            showInactive = f.showInactive,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SuperAdminPreachingTimeUiState())

    /** Add or Edit (spec §4/§5) — the same [PreachingTimeRecordRepository
     * .save] a Publisher's own screen already uses; [isNew] only decides
     * which audit action gets logged, never anything about the write
     * itself. Congregation/Publisher are set once at creation and never
     * re-pointed by an edit (spec §5: "preserve the original record ID... do
     * not create a duplicate record"). */
    fun save(record: PreachingTimeRecord, actorPersonId: String, isNew: Boolean) {
        viewModelScope.launch {
            recordRepository.save(record)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = if (isNew) "SUPER_ADMIN_ADD_PREACHING_TIME_RECORD" else "SUPER_ADMIN_EDIT_PREACHING_TIME_RECORD",
                targetType = "PreachingTimeRecord",
                targetId = record.id,
                congregationId = record.congregationId,
                details = "publisher=${record.publisherPersonId}, date=${formatAuditDate(record.date)}, hours=${record.hoursConsumed}",
            )
        }
    }

    /** Normal Delete (spec §6) — same Move-to-Inactive/Delete-Permanently
     * pair every other Manage screen's [com.emfitsolutions.gopreach.ui
     * .components.DeleteChoiceDialog] already offers; not the separate
     * [forceDelete] below. */
    fun setStatus(record: PreachingTimeRecord, status: RecordStatus, actorPersonId: String) {
        viewModelScope.launch {
            recordRepository.setStatus(record, status)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = if (status == RecordStatus.INACTIVE) "DELETE_PREACHING_TIME_RECORD" else "REACTIVATE_PREACHING_TIME_RECORD",
                targetType = "PreachingTimeRecord",
                targetId = record.id,
                congregationId = record.congregationId,
            )
        }
    }

    fun permanentlyDelete(record: PreachingTimeRecord, actorPersonId: String) {
        viewModelScope.launch {
            recordRepository.permanentlyDelete(record.id)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "PERMANENT_DELETE_PREACHING_TIME_RECORD",
                targetType = "PreachingTimeRecord",
                targetId = record.id,
                congregationId = record.congregationId,
            )
        }
    }

    /** Force Delete (spec §7-§9) — Super-Admin-only both here (the whole
     * screen is unreachable by anyone else, see this class's own doc
     * comment) AND, unbypassably, at firestore.rules' `preachingTimeRecords`
     * `delete` rule (spec §8's actual "server-side" requirement — this app
     * has no other backend to enforce it in). Functionally the same
     * permanent-delete call as [permanentlyDelete] — a per-day preaching
     * time entry has no child/related records of its own to also clean up —
     * but kept as a fully separate action with its own confirmation, its
     * own distinctly-logged audit action, and full audit detail (spec §9),
     * so it's independently traceable from an ordinary permanent delete. */
    fun forceDelete(record: PreachingTimeRecord, person: Person, congregationName: String, actorPersonId: String, actorRoleLabel: String) {
        viewModelScope.launch {
            recordRepository.permanentlyDelete(record.id)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "FORCE_DELETE_PREACHING_TIME_RECORD",
                targetType = "PreachingTimeRecord",
                targetId = record.id,
                congregationId = record.congregationId,
                details = "publisher=${person.fullName} (${person.id}), congregation=$congregationName, " +
                    "date=${formatAuditDate(record.date)}, hoursConsumed=${record.hoursConsumed}, " +
                    "deletedByRole=$actorRoleLabel",
            )
        }
    }

    private fun formatAuditDate(millis: Long): String =
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(millis))
}
