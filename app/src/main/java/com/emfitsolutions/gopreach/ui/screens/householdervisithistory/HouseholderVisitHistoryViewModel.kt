package com.emfitsolutions.gopreach.ui.screens.householdervisithistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.Visit
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Record Type" filter — spec's exact three categories plus "All", backed by
 * the existing [PipelineStage] a householder already sits at (no new field). */
enum class RecordTypeFilter(val label: String) {
    ALL("All"),
    FOUND_INTERESTED("Found Interested"),
    RETURN_VISITS("Return Visits"),
    BIBLE_STUDIES("Bible Studies"),
}

private fun RecordTypeFilter.matches(stage: PipelineStage): Boolean = when (this) {
    RecordTypeFilter.ALL -> true
    RecordTypeFilter.FOUND_INTERESTED -> stage == PipelineStage.SEARCHING
    RecordTypeFilter.RETURN_VISITS -> stage == PipelineStage.RETURN_VISIT
    RecordTypeFilter.BIBLE_STUDIES -> stage == PipelineStage.BIBLE_STUDY
}

/** One householder row this module shows — [visits] already sorted newest
 * first (spec §10: "ALWAYS ... descending date order", visit date primary,
 * [Visit.createdAt] as the tie-break for a same-date pair). [publisherName]/
 * [congregationName] are resolved once here so the screen never needs to
 * re-look them up per recomposition. */
data class HouseholderRow(
    val person: InterestedPerson,
    val publisherName: String?,
    val congregationName: String,
    val visits: List<Visit>,
)

data class HouseholderVisitHistoryUiState(
    val isLoading: Boolean = true,
    val congregations: List<Congregation> = emptyList(),
    val recordType: RecordTypeFilter = RecordTypeFilter.ALL,
    val searchQuery: String = "",
    val province: String? = null,
    val municipality: String? = null,
    val barangay: String? = null,
    /** Every row this session is authorized to see, before the Province/
     * Municipality/Barangay/search/record-type filters above narrow it —
     * kept separate from [rows] so the filter dropdowns' own option lists
     * (see [HouseholderVisitHistoryScreen]) can offer every value actually
     * *reachable* from the current record-type/search selection, not just
     * whatever's left after every filter (a Province picked, then a
     * Municipality dropdown scoped to it, is the point of a cascading
     * filter — it must not also disappear once a Barangay narrows further). */
    val scopedRows: List<HouseholderRow> = emptyList(),
    val rows: List<HouseholderRow> = emptyList(),
)

/**
 * "House Holder Visit History" module — a read-only, consolidated view over
 * the *existing* Searching/Return Visit/Bible Study records
 * ([InterestedPersonRepository]) and their [Visit] history
 * ([VisitRepository]), for Super-Admin (every authorized congregation) and
 * Publisher (their own congregation) accounts. Deliberately builds nothing
 * new: every field this screen shows already exists on [InterestedPerson]/
 * [Visit]; this view only filters, sorts, and displays them (spec §25: "Do
 * not modify existing... simply by viewing, filtering, or exporting").
 *
 * [restrictTo] is the actual security boundary (spec §16), same convention
 * every other congregation-scoped screen in this app uses (see
 * [com.emfitsolutions.gopreach.ui.screens.pipeline.ElderInterestedRecordsScreen]) —
 * `null` means Super-Admin's unscoped "every congregation," a real id means
 * exactly that one congregation and nothing else; the UI never exposes a way
 * to escape whichever of the two the caller passed in.
 */
@HiltViewModel
class HouseholderVisitHistoryViewModel @Inject constructor(
    private val interestedPersonRepository: InterestedPersonRepository,
    private val visitRepository: VisitRepository,
    private val personRepository: PersonRepository,
    private val congregationRepository: CongregationRepository,
) : ViewModel() {

    private val congregationId = MutableStateFlow<String?>(null)
    private val recordType = MutableStateFlow(RecordTypeFilter.ALL)
    private val searchQuery = MutableStateFlow("")
    private val province = MutableStateFlow<String?>(null)
    private val municipality = MutableStateFlow<String?>(null)
    private val barangay = MutableStateFlow<String?>(null)
    private var restricted = false

    /** Spec §14/§15/§16 — called once from the nav graph with the signed-in
     * session's actual authorized scope; a no-op on every later recomposition
     * (`restricted` latches) so a stray recomposition can never silently
     * re-widen an already-narrowed Publisher session back to `null`/unscoped. */
    fun restrictTo(scopedCongregationId: String?) {
        if (restricted) return
        restricted = true
        congregationId.value = scopedCongregationId
    }

    fun setRecordType(value: RecordTypeFilter) { recordType.value = value }
    fun setSearchQuery(value: String) { searchQuery.value = value }
    fun setProvince(value: String?) { province.value = value; municipality.value = null; barangay.value = null }
    fun setMunicipality(value: String?) { municipality.value = value; barangay.value = null }
    fun setBarangay(value: String?) { barangay.value = value }

    init {
        // Same broad, screen-lifetime collection-group listener the
        // Consolidated Report already starts for "every Visit, every
        // Interested Person" — reused rather than duplicated (spec §26).
        viewModelScope.launch { visitRepository.startRemoteSyncAllForCongregationView().collect {} }
    }

    val congregations: StateFlow<List<Congregation>> =
        congregationRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val scopedRowsFlow = combine(
        interestedPersonRepository.observeAll(),
        visitRepository.observeAllVisits(),
        personRepository.observeAll(),
        congregationRepository.observeAll(),
        congregationId,
    ) { people, visits, persons, congregations, scopedCongregationId ->
        val personNameById = persons.associate { it.id to it.fullName }
        val congregationNameById = congregations.associate { it.id to it.name }
        val visitsByPerson = visits.groupBy { it.interestedPersonId }
        people
            .filter { it.status == RecordStatus.ACTIVE }
            .filter { scopedCongregationId == null || it.congregationId == scopedCongregationId }
            .map { person ->
                HouseholderRow(
                    person = person,
                    publisherName = personNameById[person.publisherPersonId],
                    congregationName = congregationNameById[person.congregationId] ?: "—",
                    visits = (visitsByPerson[person.id].orEmpty())
                        .sortedWith(compareByDescending<Visit> { it.visitDate }.thenByDescending { it.createdAt }),
                )
            }
    }

    private data class FilterState(
        val recordType: RecordTypeFilter,
        val searchQuery: String,
        val province: String?,
        val municipality: String?,
        val barangay: String?,
    )

    private val filterState = combine(recordType, searchQuery, province, municipality, barangay) { rt, q, p, m, b ->
        FilterState(rt, q, p, m, b)
    }

    val uiState: StateFlow<HouseholderVisitHistoryUiState> = combine(
        scopedRowsFlow, congregations, filterState,
    ) { scopedRows, congregations, filter ->
        val (recordType, searchQuery, province, municipality, barangay) = filter
        val filteredRows = scopedRows
            .filter { recordType.matches(it.person.pipelineStage) }
            .filter { searchQuery.isBlank() || it.person.name.contains(searchQuery, ignoreCase = true) }
            .filter { province == null || it.person.province == province }
            .filter { municipality == null || it.person.cityMunicipality == municipality }
            .filter { barangay == null || it.person.barangay == barangay }
            .sortedBy { it.person.name }
        HouseholderVisitHistoryUiState(
            isLoading = false,
            congregations = congregations,
            recordType = recordType,
            searchQuery = searchQuery,
            province = province,
            municipality = municipality,
            barangay = barangay,
            scopedRows = scopedRows,
            rows = filteredRows,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HouseholderVisitHistoryUiState())

    /** Province options — spec §5/§18: drawn from the householders actually
     * reachable at the current Record Type/search selection (not the full
     * PSGC master list, and not narrowed by Province/Municipality/Barangay
     * themselves — picking a Province must not make *other* provinces
     * disappear from this same dropdown). */
    fun provinceOptions(state: HouseholderVisitHistoryUiState): List<String> =
        state.scopedRows
            .filter { state.recordType.matches(it.person.pipelineStage) }
            .filter { state.searchQuery.isBlank() || it.person.name.contains(state.searchQuery, ignoreCase = true) }
            .mapNotNull { it.person.province }
            .distinct()
            .sorted()

    fun municipalityOptions(state: HouseholderVisitHistoryUiState): List<String> =
        state.scopedRows
            .filter { state.recordType.matches(it.person.pipelineStage) }
            .filter { state.searchQuery.isBlank() || it.person.name.contains(state.searchQuery, ignoreCase = true) }
            .filter { state.province == null || it.person.province == state.province }
            .mapNotNull { it.person.cityMunicipality }
            .distinct()
            .sorted()

    fun barangayOptions(state: HouseholderVisitHistoryUiState): List<String> =
        state.scopedRows
            .filter { state.recordType.matches(it.person.pipelineStage) }
            .filter { state.searchQuery.isBlank() || it.person.name.contains(state.searchQuery, ignoreCase = true) }
            .filter { state.province == null || it.person.province == state.province }
            .filter { state.municipality == null || it.person.cityMunicipality == state.municipality }
            .mapNotNull { it.person.barangay }
            .distinct()
            .sorted()
}
