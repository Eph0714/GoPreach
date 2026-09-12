package com.emfitsolutions.gopreach.ui.screens.territories

import android.Manifest
import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.emfitsolutions.gopreach.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.emfitsolutions.gopreach.data.location.LatLng
import com.emfitsolutions.gopreach.data.location.formatCoordinatesDms
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.ui.components.isValidLatitude
import com.emfitsolutions.gopreach.ui.components.isValidLongitude
import com.emfitsolutions.gopreach.ui.components.openCoordinatesInMaps
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.screens.pipeline.PipelinePersonDetailScreen
import com.emfitsolutions.gopreach.ui.screens.pipeline.PipelineViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.window.DialogProperties

private const val TAG = "TerritoryMap"

private enum class TerritoryViewMode(val label: String) { LIST("List View"), MAP("Map View") }

/** "Search By" dropdown — spec's exact required entries. Reuses the same
 * fields every record already carries ([TerritoryMapRow.person]); no new
 * data. */
private enum class TerritorySearchByField(val label: String) {
    ALL("All"),
    NAME("House Holder Name"),
    MUNICIPALITY("Municipalities"),
    BARANGAY("Barangay"),
    STATUS("Status"),
    PUBLISHER("Publisher Assigned"),
}

private fun TerritoryMapRow.matchesSearch(field: TerritorySearchByField, query: String, publisherName: String?): Boolean {
    if (query.isBlank()) return true
    fun String?.has() = this != null && contains(query, ignoreCase = true)
    return when (field) {
        TerritorySearchByField.ALL ->
            person.name.has() || person.cityMunicipality.has() || person.barangay.has() ||
                person.pipelineStage.statusLabel().has() || publisherName.has()
        TerritorySearchByField.NAME -> person.name.has()
        TerritorySearchByField.MUNICIPALITY -> person.cityMunicipality.has()
        TerritorySearchByField.BARANGAY -> person.barangay.has()
        TerritorySearchByField.STATUS -> person.pipelineStage.statusLabel().has()
        TerritorySearchByField.PUBLISHER -> publisherName.has()
    }
}

/** "Group By" — List View only (spec's exact two options); Map View markers
 * are never grouped/clustered by this, only List View's rows are. */
private enum class TerritoryGroupBy(val label: String) { MUNICIPALITY("Municipalities"), BARANGAY("Barangay") }

/** "LIST VIEW CONTROLS — Search by Status" — spec's exact five options.
 * [PUBLISHER] is listed for completeness (the spec names it explicitly
 * alongside the other four) but always yields zero-count groups/rows in
 * this geography-grouped directory: a live-sharing Publisher location
 * ([TerritoryPublisherRow]) has no Municipality/Barangay of its own the way
 * an [InterestedPerson] record does — Publishers were never part of this
 * directory's underlying data to begin with (see [TerritoryMapScreen]'s own
 * `filtered`, built purely from pipeline rows) — so there is nothing real to
 * count or list rather than a fabricated one. */
private enum class TerritoryDirectoryStatus(val label: String, val stage: PipelineStage?) {
    ALL("All", null),
    PUBLISHER("Publisher", null),
    BIBLE_STUDY("Bible Study", PipelineStage.BIBLE_STUDY),
    SEARCHING("Searching Interested Person", PipelineStage.SEARCHING),
    RETURN_VISIT("Return Visit", PipelineStage.RETURN_VISIT),
}

/** "The Territory Map → Map View search can be simplified into a single,
 * consistent cascading search system" — Map View's own "Search by" category,
 * entirely independent of List View's own Municipality/Barangay/Search-By/
 * Group-By/Search-by-Status controls (which are unchanged; this spec is
 * explicitly scoped to "Map View search" only). Each category drives what
 * the second dropdown ([TerritoryMapScreen]'s own `mapSelectionOptions`)
 * offers and what [PipelineStage] (if any) narrows the map's pipeline
 * markers — see [MapSearchCategory.stage]. */
/** Joins a (Municipality, Barangay) pair into one dropdown selection id —
 * Barangay names alone collide across towns ("Poblacion" in nearly every
 * one), so [MapSearchCategory.BARANGAY]'s selection can never be just the
 * bare Barangay name. `||` never appears in a real PSGC name. */
private const val BARANGAY_SELECTION_SEPARATOR = "||"

private enum class MapSearchCategory(
    val label: String,
    val stage: PipelineStage?,
    /** The second dropdown's own label — spec's exact worked examples
     * ("Select Municipality", "Select Publisher", ...). */
    val selectLabel: String,
    /** "'All' should always be available... All Municipalities / All
     * Barangays / All Interested Persons / All Return Visits / All Bible
     * Studies / All Publishers" — spec's exact wording per category. */
    val allLabel: String,
) {
    MUNICIPALITY("Municipalities", null, "Select Municipality", "All Municipalities"),
    BARANGAY("Barangay", null, "Select Barangay", "All Barangays"),
    INTERESTED_PERSON("Interested Person", PipelineStage.SEARCHING, "Select Interested Person", "All Interested Persons"),
    RETURN_VISIT("Return Visit", PipelineStage.RETURN_VISIT, "Select Return Visit", "All Return Visits"),
    BIBLE_STUDY("Bible Study", PipelineStage.BIBLE_STUDY, "Select Bible Study", "All Bible Studies"),
    PUBLISHER_TERRITORY("Publisher Territory", null, "Select Publisher", "All Publishers"),
}

/**
 * "Territory Module" — a read-only directory of every Searching/Return
 * Visit/Bible Study record that has a saved GPS location, searchable by
 * name or location in [TerritoryViewMode.LIST]; [TerritoryViewMode.MAP] is
 * the primary, full-screen experience — see [TerritoryLiveMap]'s own doc
 * comment for the redesign this screen defers to it for. List View's rows
 * tap out to Google Maps (or whatever the device offers for a `geo:` URI),
 * same as every other saved coordinate in this app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerritoryMapScreen(
    fixedCongregationId: String?,
    currentPersonId: String,
    canSeePublisherLocations: Boolean,
    // "Clicking coordinates should open the Territory Map centered on the
    // Publisher's latest location" — non-null only when reached via Share
    // Location's own "open in Territory Map" action (see
    // Destinations.territoryMapFocusedOn); every other entry point leaves
    // these null and this screen behaves exactly as before.
    focusLat: Double? = null,
    focusLng: Double? = null,
    focusName: String? = null,
    // "Territory Map Congregation and Field Service Group Filters" —
    // effectively always `true` now (see GoPreachNavGraph's own call site);
    // kept as its own parameter rather than inlined since this screen still
    // shouldn't assume every future caller wants the filter row.
    showAdvancedFilter: Boolean = false,
    onBack: () -> Unit,
    viewModel: TerritoryMapViewModel = hiltViewModel(),
    pipelineViewModel: PipelineViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isSuperAdmin = fixedCongregationId == null
    val showToast = rememberActionToast()

    // "Territory Maps — Direct Return Visit Recording" — selecting a
    // Return Visit (or Bible Study/Searching) marker/row opens the same
    // full detail + Visit History screen the Pipeline module already uses
    // (Add/Edit/Delete Visit History, ownership rules, congregation display
    // — all unchanged), rather than a second, parallel implementation.
    // `null` means the map/list itself is showing.
    var selectedPersonForDetails by remember { mutableStateOf<InterestedPerson?>(null) }

    val current = selectedPersonForDetails
    if (current != null) {
        val congregationName by remember(current.congregationId) { pipelineViewModel.congregationName(current.congregationId) }.collectAsStateWithLifecycle(initialValue = null)
        PipelinePersonDetailScreen(
            person = current,
            currentPersonId = currentPersonId,
            congregationName = congregationName ?: "—",
            stage = current.pipelineStage,
            // A Publisher opening someone else's Return Visit from the map
            // never gets cross-Publisher Visit History management rights —
            // same standard ownership rules PipelineScreen's own routes
            // already enforce (see PipelineViewModel.saveVisit/deleteVisit).
            canManageAllVisitHistory = false,
            onBack = { selectedPersonForDetails = null },
            viewModel = pipelineViewModel,
        )
        return
    }

    // "Determine congregation membership from the authenticated user's
    // database record... do not rely solely on the congregation name sent
    // by the mobile client" — [fixedCongregationId] IS that server-resolved
    // value already (see GoPreachNavGraph: derived from the signed-in
    // session's own RoleAssignment, never client-editable); re-checked here,
    // defensively, against the specific record being opened, even though
    // every row already reaching this screen came from a congregation-
    // scoped query in the first place (see [rowsFor]) and should never fail
    // this. `null` (Super-Admin) always passes.
    fun tryOpenDetails(person: InterestedPerson) {
        if (fixedCongregationId != null && person.congregationId != fixedCongregationId) {
            showToast("You do not have access to this Return Visit.")
            return
        }
        selectedPersonForDetails = person
    }
    var advancedFilter by remember { mutableStateOf(TerritoryFilterState()) }

    // "Make the Super Admin... have the same territory map like the
    // Publishers Territory Map, the same features and functions. Just
    // observe the congregation restrictions" — every feature below (List
    // View default, Deep Search, Area Information Panel, real boundary
    // polygons, uniform markers) is already the exact same screen/state a
    // scoped Publisher/Admin/Elder uses; the one piece Super-Admin alone
    // needs — a way to actually choose "All Congregations" or one specific
    // congregation — was lost when the old Tune-icon [TerritoryFilterSheet]
    // was removed this session, even though [advancedFilter.congregationId]/
    // [effectiveCongregationId]/[TerritoryMapViewModel.congregationsFor]
    // were never touched and still fully wire up to it (see this screen's
    // own doc comments a few lines up). Re-adding just the picker itself,
    // in the same persistent filter bar every other role already sees,
    // restores that without reviving the removed sheet or its Field
    // Service Group/Publisher sub-filters (never part of this request).
    val congregationOptions by (if (isSuperAdmin) viewModel.congregationsFor(null) else flowOf(emptyList()))
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // "Territory Map Congregation and Field Service Group Filters" spec §1/
    // §2/§17 — Congregation is the first filter for every role, but only
    // Super-Admin's own choice ever actually varies it: every other role's
    // effective congregation is always [fixedCongregationId] — the same
    // server-resolved value [tryOpenDetails] above already trusts — never
    // anything read from [advancedFilter], which has no field a scoped
    // role's own client could even set to another congregation (see
    // [TerritoryFilterState]'s own doc comment). This is what actually makes
    // "cannot select/manipulate another congregation" true for them: the UI
    // has no control that could produce a different value in the first
    // place, on top of [rowsFor]'s own filtering below.
    val effectiveCongregationId = if (isSuperAdmin) advancedFilter.congregationId else fixedCongregationId
    val fixedCongregationName by remember(fixedCongregationId) {
        if (fixedCongregationId != null) viewModel.congregationById(fixedCongregationId).map { it?.name } else flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    val groupMemberIdsFlow = remember(advancedFilter.groupId) {
        advancedFilter.groupId?.let { viewModel.groupMemberPublisherIds(it) } ?: flowOf(emptySet())
    }
    val groupMemberIds by groupMemberIdsFlow.collectAsStateWithLifecycle(initialValue = emptySet())
    // "Province/City automatic base on their congregation enrollment" — set
    // once, the first time this screen's own fixed congregation resolves;
    // shown read-only in the filter sheet rather than editable, and applied
    // as a no-op alongside [fixedCongregationId]'s own scoping (every row a
    // scoped role ever sees already shares this same province/city).
    if (!isSuperAdmin) {
        LaunchedEffect(fixedCongregationId) {
            viewModel.congregationById(fixedCongregationId!!).collect { congregation ->
                if (congregation?.province != null && advancedFilter.province == null) {
                    advancedFilter = advancedFilter.copy(province = congregation.province)
                }
            }
        }
    } else {
        // "Province is automatic... For Super Admin... when viewing a
        // specific congregation, the Province should automatically
        // correspond to that congregation's Province" — re-derives every
        // time Super Admin's own Congregation choice changes (never a no-op
        // latch like the scoped-role effect above, since Super Admin's
        // congregation choice can itself change repeatedly in one session);
        // "All Congregations" (null) has no one Province to show, so it's
        // cleared back to null rather than left stuck on whichever
        // congregation was viewed previously. Changing the effective
        // Province always clears Municipality/Barangay too — spec's own
        // "Municipality = Based on that Province" cascade — since a
        // Municipality from the *previous* Province/congregation must never
        // silently carry over.
        LaunchedEffect(advancedFilter.congregationId) {
            val congId = advancedFilter.congregationId
            if (congId == null) {
                if (advancedFilter.province != null) {
                    advancedFilter = advancedFilter.copy(province = null, cityMunicipality = null, barangay = null)
                }
                return@LaunchedEffect
            }
            viewModel.congregationById(congId).collect { congregation ->
                val newProvince = congregation?.province
                if (newProvince != advancedFilter.province) {
                    advancedFilter = advancedFilter.copy(province = newProvince, cityMunicipality = null, barangay = null)
                }
            }
        }
    }
    // Spec §16 — [effectiveCongregationId] (the selected/assigned
    // congregation) is the primary restriction passed down to [rowsFor]/
    // [publisherRowsFor] themselves, same as [fixedCongregationId] always
    // was for a scoped role; Super-Admin's own selection now narrows the
    // exact same way once they pick one, instead of always pulling every
    // congregation's data first and only filtering it on-device afterward.
    val rowsFlow = remember(effectiveCongregationId) { viewModel.rowsFor(effectiveCongregationId) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // "For publisher account they can see other publishers that share their
    // location in the map" — only collected (and only ever asked of the
    // repository) when [canSeePublisherLocations] actually allows it, so an
    // unauthorized role's screen never even subscribes to every other
    // publisher's live position.
    val publisherRowsFlow = remember(effectiveCongregationId, currentPersonId, canSeePublisherLocations) {
        if (canSeePublisherLocations) viewModel.publisherRowsFor(effectiveCongregationId, currentPersonId) else flowOf(emptyList())
    }
    val publisherRows by publisherRowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // Spec §11 — the "Publisher" map-point kind is itself filterable by
    // Field Service Group too (a Publisher's own group membership, via their
    // RoleAssignment — same [groupMemberIds] the pipeline rows below use),
    // not just Searching/Return Visit/Bible Study records.
    val filteredPublisherRows = remember(publisherRows, advancedFilter.groupId, groupMemberIds) {
        if (advancedFilter.groupId != null) publisherRows.filter { it.person.id in groupMemberIds } else publisherRows
    }
    // "Add a filter in Territory Map" — Congregation/Field Service Group/
    // Publisher narrow first, which is also what the Location section's
    // Municipality/Barangay option lists are derived from (see
    // TerritoryFilterSheet), then the full filter (those plus Location and
    // Record Type) is what actually reaches List View/Map View below.
    val searchByRows = remember(rows, advancedFilter.congregationId, advancedFilter.groupId, advancedFilter.publisherPersonId, groupMemberIds) {
        applyTerritoryFilter(rows, advancedFilter.copy(province = null, cityMunicipality = null, barangay = null, innerFilter = TerritoryInnerFilter.ALL), groupMemberIds)
    }
    val advancedFilteredRows = remember(rows, advancedFilter, groupMemberIds) {
        applyTerritoryFilter(rows, advancedFilter, groupMemberIds)
    }
    var searchQuery by remember { mutableStateOf("") }
    var searchByField by remember { mutableStateOf(TerritorySearchByField.ALL) }
    var groupBy by remember { mutableStateOf(TerritoryGroupBy.MUNICIPALITY) }
    val personNames by viewModel.personNames.collectAsStateWithLifecycle()
    // "LIST VIEW MUST BE THE DEFAULT VIEW" — List View is now the initial
    // landing mode (previously Map View); Map View is still reachable via
    // the exact same top-bar toggle, unchanged.
    var viewMode by remember { mutableStateOf(TerritoryViewMode.LIST) }

    // "TERRITORY MAP – PHILIPPINES LOCATION SEARCH" — Congregation → Province
    // → Municipality/City → Barangay, using the real, complete Philippine
    // PSGC hierarchy ([TerritoryMapViewModel.municipalitiesInProvince]/
    // [barangaysInMuncity]/[barangaysInProvince]) rather than only whichever
    // names happen to already appear on a saved record. Province itself is
    // never a filter field here — [advancedFilter.province] is set
    // automatically (see the two effects above) from the scoped role's own
    // congregation, or Super Admin's currently-viewed one; "All
    // Congregations" (Super Admin, no single Province) is the one case with
    // no real PSGC province to resolve, so it falls back to whichever
    // Municipality/Barangay names actually appear on the records currently
    // in view — the same behavior this screen always had before this pass.
    val effectiveProvince = advancedFilter.province
    var municipalityOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var barangayOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    val provinceId = remember(effectiveProvince) { mutableStateOf<Int?>(null) }
    LaunchedEffect(effectiveProvince) {
        val province = effectiveProvince
        if (province == null) {
            provinceId.value = null
            municipalityOptions = searchByRows.mapNotNull { it.person.cityMunicipality }.distinct().sorted()
        } else {
            val id = viewModel.resolveProvinceId(province)
            provinceId.value = id
            municipalityOptions = if (id != null) {
                viewModel.municipalitiesInProvince(id)
            } else {
                // A congregation's own stored Province text didn't match any
                // real PSGC province (should be rare) — degrade to the old
                // records-derived list rather than showing nothing at all.
                searchByRows.mapNotNull { it.person.cityMunicipality }.distinct().sorted()
            }
        }
    }
    LaunchedEffect(effectiveProvince, provinceId.value, advancedFilter.cityMunicipality, searchByRows) {
        val id = provinceId.value
        val municipality = advancedFilter.cityMunicipality
        barangayOptions = when {
            id == null -> searchByRows
                .filter { municipality == null || it.person.cityMunicipality == municipality }
                .mapNotNull { it.person.barangay }.distinct().sorted()
            // "If Municipality: All Municipalities is selected, the Barangay
            // dropdown may display all Barangays available within the
            // automatically selected Province" (spec §3).
            municipality == null -> viewModel.barangaysInProvince(id)
            else -> viewModel.barangaysInMuncity(id, municipality)
        }
    }

    // "TERRITORY MAP – LIST VIEW REDESIGN AND GROUPING" — the full (municipality,
    // barangay) master directory for Barangay grouping, spanning every
    // municipality currently in scope (the one Municipality picked in the
    // persistent bar, or every municipality in the province when that's
    // "All Municipalities") — built once per scope change, not per keystroke,
    // since it's an N-query fan-out (one per municipality) rather than a
    // single indexed lookup. Barangay names alone collide across towns
    // nationwide (nearly every municipality has its own "Poblacion"), so
    // each entry is kept paired with its owning municipality rather than a
    // flat, ambiguous barangay-name list.
    var barangayDirectory by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(groupBy, provinceId.value, advancedFilter.cityMunicipality, municipalityOptions, searchByRows) {
        if (groupBy != TerritoryGroupBy.BARANGAY) return@LaunchedEffect
        val id = provinceId.value
        val selectedMuni = advancedFilter.cityMunicipality
        barangayDirectory = if (id == null) {
            // No real PSGC province resolved — degrade to whichever
            // (municipality, barangay) pairs actually appear on a record,
            // same fallback [municipalityOptions]/[barangayOptions] already use.
            searchByRows
                .filter { selectedMuni == null || it.person.cityMunicipality == selectedMuni }
                .mapNotNull { row -> row.person.cityMunicipality?.let { m -> row.person.barangay?.let { b -> m to b } } }
                .distinct()
        } else if (selectedMuni != null) {
            viewModel.barangaysInMuncity(id, selectedMuni).map { selectedMuni to it }
        } else {
            municipalityOptions.flatMap { muni -> viewModel.barangaysInMuncity(id, muni).map { muni to it } }
        }
    }

    // ============================================================
    // "TERRITORY MAP → MAP VIEW SEARCH SIMPLIFICATION" — a single,
    // consistent cascading search: Search Category → Specific Record/All →
    // Deeper Text Search → Map Scope. Entirely independent of List View's
    // own Municipality/Barangay/Search-By/Group-By/Search-by-Status controls
    // above (unchanged — this request is explicitly scoped to "Map View
    // search" only, and the two views' underlying mental models — a
    // geography-grouped directory vs. a category cascade — don't map onto
    // each other cleanly enough to share one control). [rows] (fully
    // congregation-scoped, nothing else) is Map View's own base dataset,
    // never [filtered]/[advancedFilteredRows] (both List-View-search-scoped).
    // ============================================================
    var mapSearchCategory by remember { mutableStateOf(MapSearchCategory.MUNICIPALITY) }
    // null means "All ..." for whichever category is active — the one
    // required option "always available" for every category (spec's own
    // "'All' should always be available" rule); its label is resolved
    // per-category by [TerritoryMapSearchBar] itself (e.g. "All
    // Municipalities" vs. "All Publishers"), never stored here.
    var mapSelectionId by remember { mutableStateOf<String?>(null) }
    var mapDeepSearchQuery by remember { mutableStateOf("") }
    // "Whenever the scope changes: Clear/recalculate the previous map
    // scope" — changing the category always resets both the specific-record
    // selection and the deeper search text, so a stale selection from one
    // category (e.g. a picked Barangay) never silently carries into another
    // (e.g. Publisher Territory) where it would mean nothing.
    fun onMapCategoryChange(category: MapSearchCategory) {
        mapSearchCategory = category
        mapSelectionId = null
        mapDeepSearchQuery = ""
    }
    // Picking a new specific record/"All" also clears the deeper search —
    // "Juan" found inside Publisher A's territory must never silently keep
    // matching once the user switches to Publisher B.
    fun onMapSelectionChange(id: String?) {
        mapSelectionId = id
        mapDeepSearchQuery = ""
    }

    // Second-dropdown option lists, one per category — only the currently
    // active category's list is ever actually computed/collected.
    // Municipalities: the exact same province-wide, real-PSGC-backed list
    // List View's own Municipality dropdown already uses — a legitimate
    // reuse, not a coincidence, since both are "every Municipality in the
    // current congregation-scope's Province" with no further narrowing.
    // Barangay: its own full (Municipality, Barangay) directory — kept
    // separate from List View's own [barangayDirectory] above (which is
    // additionally narrowed by List View's *own* selected Municipality) so
    // switching between List View and Map View's Barangay category can
    // never make one silently filter the other.
    var mapBarangayDirectory by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(mapSearchCategory, provinceId.value, municipalityOptions, rows) {
        if (mapSearchCategory != MapSearchCategory.BARANGAY) return@LaunchedEffect
        val id = provinceId.value
        mapBarangayDirectory = if (id == null) {
            rows.mapNotNull { row -> row.person.cityMunicipality?.let { m -> row.person.barangay?.let { b -> m to b } } }.distinct()
        } else {
            municipalityOptions.flatMap { muni -> viewModel.barangaysInMuncity(id, muni).map { muni to it } }
        }
    }
    // Interested Person / Return Visit / Bible Study: every named record
    // currently at that pipeline stage, in scope.
    val mapStageOptions = remember(rows, mapSearchCategory) {
        val stage = mapSearchCategory.stage ?: return@remember emptyList()
        rows.filter { it.person.pipelineStage == stage }.map { it.person.id to it.person.name }.sortedBy { it.second }
    }
    // Publisher Territory: every active Publisher in the current
    // congregation scope (not just whoever's currently live-sharing — a
    // Publisher's assigned records exist whether or not they're actively
    // sharing their location right now).
    val mapPublisherPersons by (
        if (mapSearchCategory == MapSearchCategory.PUBLISHER_TERRITORY) {
            viewModel.publishersFor(effectiveCongregationId?.let { setOf(it) })
        } else {
            flowOf(emptyList())
        }
        ).collectAsStateWithLifecycle(initialValue = emptyList())

    // "The map should show the complete territory scope associated with
    // that publisher" — every pipeline record currently assigned to them
    // (Searching/Return Visit/Bible Study all at once, spec's own list),
    // plus (below) their own live-shared and/or recorded home location.
    val mapScopedRows = remember(rows, mapSearchCategory, mapSelectionId) {
        val selection = mapSelectionId
        when (mapSearchCategory) {
            MapSearchCategory.MUNICIPALITY ->
                if (selection == null) rows else rows.filter { it.person.cityMunicipality == selection }
            MapSearchCategory.BARANGAY ->
                if (selection == null) rows else {
                    val muni = selection.substringBefore(BARANGAY_SELECTION_SEPARATOR)
                    val brgy = selection.substringAfter(BARANGAY_SELECTION_SEPARATOR)
                    rows.filter { it.person.cityMunicipality == muni && it.person.barangay == brgy }
                }
            MapSearchCategory.INTERESTED_PERSON, MapSearchCategory.RETURN_VISIT, MapSearchCategory.BIBLE_STUDY -> {
                val stage = mapSearchCategory.stage
                rows.filter { it.person.pipelineStage == stage && (selection == null || it.person.id == selection) }
            }
            // "Selecting All means the map displays the complete scope for
            // that category" — for Publisher Territory that's every record
            // with *any* assigned Publisher, i.e. every row already in scope.
            MapSearchCategory.PUBLISHER_TERRITORY ->
                if (selection == null) rows else rows.filter { it.person.publisherPersonId == selection }
        }
    }
    // "Search deeper... should perform a deeper search within the currently
    // selected scope" — [mapScopedRows] above, never the full [rows].
    val mapDeepSearchedRows = remember(mapScopedRows, mapDeepSearchQuery) {
        val query = mapDeepSearchQuery.trim()
        if (query.isEmpty()) mapScopedRows else mapScopedRows.filter { it.person.name.contains(query, ignoreCase = true) || it.person.address.contains(query, ignoreCase = true) }
    }
    // Publisher markers only ever appear under the Publisher Territory
    // category — every other category's "line barrier" is a geography/
    // pipeline-record scope, not a Publisher's, so showing an unrelated
    // Publisher's live location pin while browsing e.g. Bible Studies would
    // only contradict the category the user actually chose.
    val mapScopedLivePublisherRows = remember(publisherRows, mapSearchCategory, mapSelectionId) {
        when {
            mapSearchCategory != MapSearchCategory.PUBLISHER_TERRITORY -> emptyList()
            mapSelectionId == null -> publisherRows
            else -> publisherRows.filter { it.person.id == mapSelectionId }
        }
    }
    // "Publisher's address" — their own recorded home location (distinct
    // from [mapScopedLivePublisherRows]' live "Share Location while
    // Preaching" position above, which may not exist at all if they're not
    // currently sharing); only meaningful once one specific Publisher is
    // picked, and only when they actually have one on file.
    val mapSelectedPublisherHome: Person? = remember(mapPublisherPersons, mapSearchCategory, mapSelectionId) {
        if (mapSearchCategory != MapSearchCategory.PUBLISHER_TERRITORY || mapSelectionId == null) null
        else mapPublisherPersons.firstOrNull { it.id == mapSelectionId }?.takeIf { it.gpsLat != null && it.gpsLng != null }
    }
    // "the map should show the complete territory scope associated with
    // that publisher, including: Publisher's address / assigned area" — a
    // second, distinct marker (not folded silently into the boundary math
    // only) whenever their recorded home location isn't the exact same
    // point as an already-shown live-sharing pin.
    val mapScopedPublisherRows = remember(mapScopedLivePublisherRows, mapSelectedPublisherHome) {
        val home = mapSelectedPublisherHome
        if (home == null) {
            mapScopedLivePublisherRows
        } else if (mapScopedLivePublisherRows.any { it.lat == home.gpsLat && it.lng == home.gpsLng }) {
            mapScopedLivePublisherRows
        } else {
            mapScopedLivePublisherRows + TerritoryPublisherRow(
                person = home,
                lat = home.gpsLat!!,
                lng = home.gpsLng!!,
                category = null,
                congregationName = fixedCongregationName ?: "—",
                updatedAt = 0L,
                isCurrentlySharing = false,
            )
        }
    }

    // "Search by Status" — List View's own directory-scoped status filter
    // (spec §1/§3/§7/§9), separate from the "Search By" field-picker above
    // (which chooses *what text field* the Search box matches, not which
    // records count). Deliberately never written back into [advancedFilter]
    // — this only ever affects how [directoryGroups] below counts/lists
    // records, never Map View's own markers.
    var directoryStatus by remember { mutableStateOf(TerritoryDirectoryStatus.ALL) }
    // Which Municipality/Barangay directory rows are currently expanded —
    // keyed by the same group label the header/row below uses, so toggling
    // one never affects any other, and re-collapsing then re-expanding a
    // group is a no-op round trip (not a fresh, jarring layout jump).
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }
    val visitsByPerson by viewModel.visitsByPerson.collectAsStateWithLifecycle()

    // "Sort By: Name A-Z" is spec's only offered option — always applied,
    // same as every list this app already sorts alphabetically by default;
    // the dropdown still exists in the filter bar below purely so the
    // control itself is present per spec, with nothing else to switch to.
    val filtered = remember(advancedFilteredRows, searchQuery, searchByField, personNames) {
        val query = searchQuery.trim()
        advancedFilteredRows
            .filter { row -> row.matchesSearch(searchByField, query, row.person.publisherPersonId?.let { personNames[it] }) }
            .sortedBy { it.person.name }
    }

    // Map View's markers/publishers are now driven entirely by the cascading
    // search above ([mapDeepSearchedRows]/[mapScopedPublisherRows]), never
    // by List View's own [filtered]/[directoryStatus] — the two views'
    // search models are independent (see that block's own doc comment).
    val mapRows = mapDeepSearchedRows
    val mapPublisherRows = mapScopedPublisherRows
    // The human-readable label for whichever second-dropdown option is
    // currently selected — used for both [noRecordsAreaLabel] below and
    // [TerritoryMapSearchBar]'s own display, resolved once here so the two
    // never disagree about what a given id actually means.
    val mapSelectionLabel: String = remember(mapSearchCategory, mapSelectionId, mapBarangayDirectory, mapStageOptions, mapPublisherPersons) {
        val selection = mapSelectionId
        if (selection == null) {
            mapSearchCategory.allLabel
        } else {
            when (mapSearchCategory) {
                MapSearchCategory.MUNICIPALITY -> selection
                MapSearchCategory.BARANGAY -> {
                    val brgy = selection.substringAfter(BARANGAY_SELECTION_SEPARATOR)
                    val muni = selection.substringBefore(BARANGAY_SELECTION_SEPARATOR)
                    "$brgy, $muni"
                }
                MapSearchCategory.INTERESTED_PERSON, MapSearchCategory.RETURN_VISIT, MapSearchCategory.BIBLE_STUDY ->
                    mapStageOptions.firstOrNull { it.first == selection }?.second ?: selection
                MapSearchCategory.PUBLISHER_TERRITORY ->
                    mapPublisherPersons.firstOrNull { it.id == selection }?.fullName ?: selection
            }
        }
    }
    // "If the selected Municipality/Barangay has no records, still show the
    // [area]'s geographic area" (spec §19/§20) — a forward-geocode query
    // string only when a specific Municipality/Barangay is actually selected
    // (the real-boundary lookup below needs plain names either way; a
    // Geocoder fallback only makes sense for those two location categories
    // in the first place — every other category's "scope" is a set of
    // records, not a place name a Geocoder could ever resolve). Barangay's
    // zoom is tighter than a whole Municipality's (spec's own "zoom closer
    // to the selected Barangay").
    val selectedAreaQuery: Pair<String, Float>? = remember(effectiveProvince, mapSearchCategory, mapSelectionId) {
        val selection = mapSelectionId
        when {
            mapSearchCategory == MapSearchCategory.BARANGAY && selection != null -> {
                val muni = selection.substringBefore(BARANGAY_SELECTION_SEPARATOR)
                val brgy = selection.substringAfter(BARANGAY_SELECTION_SEPARATOR)
                "$brgy, $muni, ${effectiveProvince ?: ""}, Philippines" to 15f
            }
            mapSearchCategory == MapSearchCategory.MUNICIPALITY && selection != null ->
                "$selection, ${effectiveProvince ?: ""}, Philippines" to 12.5f
            else -> null
        }
    }
    // Raw (un-templated) Municipality/Barangay names for the real-boundary
    // lookup ([TerritoryBoundaryRepository] keys by exact name, not a
    // geocoder query string) — same gating as [selectedAreaQuery] above.
    val selectedAreaNames: Pair<String, String?>? = remember(mapSearchCategory, mapSelectionId) {
        val selection = mapSelectionId
        when {
            mapSearchCategory == MapSearchCategory.MUNICIPALITY && selection != null -> selection to null
            mapSearchCategory == MapSearchCategory.BARANGAY && selection != null ->
                selection.substringBefore(BARANGAY_SELECTION_SEPARATOR) to selection.substringAfter(BARANGAY_SELECTION_SEPARATOR)
            else -> null
        }
    }
    // "The line barrier should always be displayed regardless of the search
    // level" — Municipality/Barangay get the real administrative polygon
    // when one specific place is selected and this app has bundled data for
    // it (see [selectedAreaNames]/[TerritoryBoundaryRepository] above,
    // unchanged); every other case — an "All ..." selection, a specific
    // Interested Person/Return Visit/Bible Study/Publisher, or a place this
    // app has no bundled boundary for — instead gets a best-effort "scope
    // boundary" hugging whatever's actually visible right now (see
    // [TerritoryLiveMap]'s own `window.setScopeBoundary`), rather than no
    // boundary at all.
    val scopeBoundaryPoints: List<LatLng> = remember(mapDeepSearchedRows, mapScopedPublisherRows) {
        buildList {
            mapDeepSearchedRows.forEach { row ->
                val lat = row.person.gpsLat
                val lng = row.person.gpsLng
                if (lat != null && lng != null) add(LatLng(lat, lng, null))
            }
            mapScopedPublisherRows.forEach { add(LatLng(it.lat, it.lng, null)) }
        }
    }
    // Only null at the screen's absolute default (Municipalities → All) —
    // see this card's own render site for why.
    val noRecordsAreaLabel: String? = remember(mapSearchCategory, mapSelectionId, mapSelectionLabel) {
        if (mapSearchCategory == MapSearchCategory.MUNICIPALITY && mapSelectionId == null) null
        else "${mapSearchCategory.label}: $mapSelectionLabel"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Territory Map") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewMode = if (viewMode == TerritoryViewMode.MAP) TerritoryViewMode.LIST else TerritoryViewMode.MAP }) {
                        Icon(
                            if (viewMode == TerritoryViewMode.MAP) Icons.Rounded.ViewList else Icons.Rounded.Map,
                            contentDescription = if (viewMode == TerritoryViewMode.MAP) "Switch to List View" else "Switch to Map View",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (viewMode == TerritoryViewMode.MAP) {
                // "TERRITORY MAP → MAP VIEW SEARCH SIMPLIFICATION" — Map
                // View's own cascading Search Category → Specific Record/
                // All → Deeper Text Search bar, replacing the shared
                // persistent filter bar for this view only (List View keeps
                // it, unchanged, in the else-branch below).
                TerritoryMapSearchBar(
                    category = mapSearchCategory,
                    onCategoryChange = ::onMapCategoryChange,
                    selectionId = mapSelectionId,
                    onSelectionChange = ::onMapSelectionChange,
                    selectionOptions = when (mapSearchCategory) {
                        MapSearchCategory.MUNICIPALITY -> municipalityOptions.map { it to it }
                        MapSearchCategory.BARANGAY -> mapBarangayDirectory.map { (muni, brgy) -> "$muni$BARANGAY_SELECTION_SEPARATOR$brgy" to "$brgy, $muni" }
                        MapSearchCategory.INTERESTED_PERSON, MapSearchCategory.RETURN_VISIT, MapSearchCategory.BIBLE_STUDY -> mapStageOptions
                        MapSearchCategory.PUBLISHER_TERRITORY -> mapPublisherPersons.map { it.id to it.fullName }
                    },
                    deepSearchQuery = mapDeepSearchQuery,
                    onDeepSearchQueryChange = { mapDeepSearchQuery = it },
                    resultCount = mapDeepSearchedRows.size,
                )

                // "AREA/SCOPE INFORMATION PANEL" — Map View's own version,
                // keyed by the cascading search above rather than List
                // View's Municipality/Barangay selection; shown at every
                // level (including "All ...") so it always summarizes
                // exactly what the map is currently showing.
                val bsCount = mapDeepSearchedRows.count { it.person.pipelineStage == PipelineStage.BIBLE_STUDY }
                val rvCount = mapDeepSearchedRows.count { it.person.pipelineStage == PipelineStage.RETURN_VISIT }
                val ipCount = mapDeepSearchedRows.count { it.person.pipelineStage == PipelineStage.SEARCHING }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${mapSearchCategory.label}: $mapSelectionLabel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Bible Study: $bsCount   Return Visit: $rvCount   Interested Person: $ipCount",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                // "Add a persistent filter area" — unchanged; List View's own
                // Municipality/Barangay/Search-By/Group-By controls, entirely
                // independent of Map View's cascading search above.
                TerritoryPersistentFilterBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchByField = searchByField,
                    onSearchByFieldChange = { searchByField = it },
                    groupBy = groupBy,
                    onGroupByChange = { groupBy = it },
                    // "Congregation is the first filter for every role, but only
                    // Super-Admin's own choice ever actually varies it" — every
                    // other role has nothing to show here at all (their own
                    // congregation is already implied, exactly like a Publisher's
                    // Territory Map today), so the field is entirely absent for
                    // them rather than shown, disabled, and stuck on one value.
                    showCongregation = isSuperAdmin,
                    congregationId = advancedFilter.congregationId,
                    congregationOptions = congregationOptions,
                    onCongregationChange = { advancedFilter = advancedFilter.copy(congregationId = it) },
                    province = effectiveProvince,
                    municipality = advancedFilter.cityMunicipality,
                    municipalityOptions = municipalityOptions,
                    onMunicipalityChange = { advancedFilter = advancedFilter.copy(cityMunicipality = it, barangay = null) },
                    barangay = advancedFilter.barangay,
                    barangayOptions = barangayOptions,
                    onBarangayChange = { advancedFilter = advancedFilter.copy(barangay = it) },
                    resultCount = filtered.size,
                    showGroupBy = true,
                )

                // "AREA INFORMATION PANEL — when a specific Municipality/Barangay
                // is selected, display a summary of Return Visit/Bible Study/
                // Interested Person counts for that area" (spec §10) — List
                // View's own version, unchanged.
                if (advancedFilter.cityMunicipality != null) {
                    val areaLabel = advancedFilter.barangay?.let { "${it}, ${advancedFilter.cityMunicipality}" }
                        ?: advancedFilter.cityMunicipality.orEmpty()
                    val bsCount = filtered.count { it.person.pipelineStage == PipelineStage.BIBLE_STUDY }
                    val rvCount = filtered.count { it.person.pipelineStage == PipelineStage.RETURN_VISIT }
                    val ipCount = filtered.count { it.person.pipelineStage == PipelineStage.SEARCHING }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(areaLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Bible Study: $bsCount   Return Visit: $rvCount   Interested Person: $ipCount",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (viewMode == TerritoryViewMode.MAP) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    TerritoryLiveMap(
                        rows = mapRows,
                        publisherRows = mapPublisherRows,
                        canSeePublisherLocations = canSeePublisherLocations,
                        getCurrentLocation = viewModel::currentLocation,
                        hasLocationPermission = viewModel::hasLocationPermission,
                        geocodeArea = viewModel::geocodeArea,
                        boundaryGeometry = viewModel::boundaryGeometry,
                        selectedAreaQuery = selectedAreaQuery,
                        selectedAreaNames = selectedAreaNames,
                        scopeBoundaryPoints = scopeBoundaryPoints,
                        noRecordsAreaLabel = noRecordsAreaLabel,
                        focusLat = focusLat,
                        focusLng = focusLng,
                        focusName = focusName,
                        publisherNames = personNames,
                        onRecordVisit = { personId ->
                            mapRows.firstOrNull { it.person.id == personId }?.let { tryOpenDetails(it.person) }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                // "TERRITORY MAP – LIST VIEW REDESIGN AND GROUPING" — a
                // complete geographic directory built from the real PSGC
                // Municipality/Barangay master data ([municipalityOptions]/
                // [barangayDirectory], both already PSGC-driven — see those
                // states' own doc comments), never from whichever records
                // happen to exist. A Municipality/Barangay with zero matching
                // records still gets its own row, at 0 — spec §14/§15's own
                // "master location data controls the displayed list; household
                // records control the counts" pipeline.
                val directoryGroups = remember(filtered, groupBy, municipalityOptions, barangayDirectory) {
                    val groups = when (groupBy) {
                        TerritoryGroupBy.MUNICIPALITY -> municipalityOptions.map { muni ->
                            DirectoryGroup(key = muni, label = muni, rows = filtered.filter { it.person.cityMunicipality == muni })
                        }
                        TerritoryGroupBy.BARANGAY -> barangayDirectory.map { (muni, brgy) ->
                            DirectoryGroup(key = "$muni|$brgy", label = "$brgy, $muni", rows = filtered.filter { it.person.cityMunicipality == muni && it.person.barangay == brgy })
                        }
                    }
                    // A record with no Municipality/Barangay at all (an old,
                    // free-text-only address predating the PSGC picker) has no
                    // master-data row to fall under — kept reachable here
                    // rather than silently dropped from the directory, same
                    // "never lose a record" principle §14 states for a
                    // location with zero *matching records*, just the mirror
                    // case (a *record* with no matching location).
                    val unassigned = when (groupBy) {
                        TerritoryGroupBy.MUNICIPALITY -> filtered.filter { it.person.cityMunicipality == null }
                        TerritoryGroupBy.BARANGAY -> filtered.filter { it.person.barangay == null }
                    }
                    (if (unassigned.isEmpty()) groups else groups + DirectoryGroup("—unassigned—", "Unassigned", unassigned))
                        .sortedBy { if (it.key == "—unassigned—") "￿" else it.label }
                }

                // "DEEP SEARCH ... must automatically expand the matching
                // Municipality/Barangay and highlight the result" (spec §5/§6,
                // e.g. searching "Rafael Guntang" must open Solano → Brgy.
                // Quirino on its own). [filtered] (which every [DirectoryGroup]
                // above is built from) is already the full-dataset search
                // result — not merely whichever rows happened to be visible —
                // since it is computed from [advancedFilteredRows] + [searchQuery]
                // with no dependency on [expandedGroups]. This effect only adds
                // to [expandedGroups]; it never removes a group the user opened
                // by hand, and it does nothing once [searchQuery] is cleared
                // rather than force-collapsing anything.
                LaunchedEffect(directoryGroups, searchQuery) {
                    val query = searchQuery.trim()
                    if (query.isNotEmpty()) {
                        val matchKeys = directoryGroups
                            .filter { group -> group.rows.any { it.person.name.contains(query, ignoreCase = true) } }
                            .map { it.key }
                            .toSet()
                        if (matchKeys.isNotEmpty()) {
                            expandedGroups = expandedGroups + matchKeys
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // "LIST VIEW CONTROLS — Search by Status" — List-View-only,
                    // never written back into [advancedFilter] (see that
                    // state's own doc comment); drives every count/expanded-row
                    // list below.
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        var statusMenuExpanded by remember { mutableStateOf(false) }
                        androidx.compose.material3.ExposedDropdownMenuBox(expanded = statusMenuExpanded, onExpandedChange = { statusMenuExpanded = it }) {
                            OutlinedTextField(
                                value = directoryStatus.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Search by Status") },
                                trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusMenuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                            )
                            ExposedDropdownMenu(expanded = statusMenuExpanded, onDismissRequest = { statusMenuExpanded = false }) {
                                TerritoryDirectoryStatus.entries.forEach { option ->
                                    DropdownMenuItem(text = { Text(option.label) }, onClick = { directoryStatus = option; statusMenuExpanded = false })
                                }
                            }
                        }
                    }

                    if (directoryGroups.isEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                if (effectiveProvince == null) "No Municipalities to show yet." else "No matches for \"$searchQuery\".",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(directoryGroups, key = { it.key }) { group ->
                                val expanded = group.key in expandedGroups
                                val bsCount = group.rows.count { it.person.pipelineStage == PipelineStage.BIBLE_STUDY }
                                val siCount = group.rows.count { it.person.pipelineStage == PipelineStage.SEARCHING }
                                val rvCount = group.rows.count { it.person.pipelineStage == PipelineStage.RETURN_VISIT }
                                // "Only records matching that status should
                                // appear inside the expanded [group]" —
                                // [TerritoryDirectoryStatus.PUBLISHER] has no
                                // real member of [group.rows] to match (see
                                // that enum's own doc comment), so it always
                                // resolves to an empty list here, same as any
                                // other status with zero matches.
                                val visibleRows = when (directoryStatus) {
                                    TerritoryDirectoryStatus.ALL -> group.rows
                                    TerritoryDirectoryStatus.PUBLISHER -> emptyList()
                                    else -> group.rows.filter { it.person.pipelineStage == directoryStatus.stage }
                                }
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable { expandedGroups = if (expanded) expandedGroups - group.key else expandedGroups + group.key }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(group.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Text(
                                                    when (directoryStatus) {
                                                        TerritoryDirectoryStatus.ALL -> "BS: $bsCount   Interested: $siCount   RV: $rvCount"
                                                        TerritoryDirectoryStatus.PUBLISHER -> "0"
                                                        TerritoryDirectoryStatus.BIBLE_STUDY -> "BS: $bsCount"
                                                        TerritoryDirectoryStatus.SEARCHING -> "Interested: $siCount"
                                                        TerritoryDirectoryStatus.RETURN_VISIT -> "RV: $rvCount"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            // Nothing to expand into for an
                                            // all-zero group — no chevron, same
                                            // as the spec's own "San Nicolas,
                                            // S.V. BS: 0 Interested: 0 RV: 0"
                                            // worked example (no ▶ shown for it).
                                            if (group.rows.isNotEmpty()) {
                                                Icon(
                                                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                    contentDescription = if (expanded) "Collapse" else "Expand",
                                                )
                                            }
                                        }
                                        if (expanded && visibleRows.isNotEmpty()) {
                                            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                                                visibleRows.forEachIndexed { index, row ->
                                                    if (index > 0) {
                                                        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                                    }
                                                    val visits = visitsByPerson[row.person.id].orEmpty()
                                                    val lastVisit = visits.firstOrNull()
                                                    // "...and highlight the result" — the same [searchQuery] that
                                                    // drove the auto-expand above also marks the matching row(s)
                                                    // once the group is open, so the user isn't left to re-scan
                                                    // an expanded group by eye to find what they searched for.
                                                    val isSearchMatch = searchQuery.isNotBlank() &&
                                                        row.person.name.contains(searchQuery.trim(), ignoreCase = true)
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth()
                                                            .then(
                                                                if (isSearchMatch) {
                                                                    Modifier.background(
                                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                                        shape = MaterialTheme.shapes.small,
                                                                    )
                                                                } else Modifier
                                                            )
                                                            .clickable { tryOpenDetails(row.person) }
                                                            .padding(if (isSearchMatch) 4.dp else 0.dp),
                                                    ) {
                                                        // "The Householder Name must be clickable" — opens the exact
                                                        // same full detail + Visit History + Add Visit screen every
                                                        // other Territory Map entry point already reuses (see
                                                        // [tryOpenDetails]) — never a second, parallel implementation.
                                                        Text(row.person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                        Text(row.person.pipelineStage.statusLabel(), style = MaterialTheme.typography.bodySmall)
                                                        if (groupBy == TerritoryGroupBy.MUNICIPALITY) {
                                                            Text("Brgy. ${row.person.barangay ?: "—"}", style = MaterialTheme.typography.bodySmall)
                                                        }
                                                        // "Super Admin deep search must clearly label each
                                                        // result's congregation" — every record in view is
                                                        // already congregation-scoped for anyone else (a plain
                                                        // Publisher's own congregation only), so this only ever
                                                        // has more than one possible value — and is only worth
                                                        // showing at all — when Super-Admin is viewing "All
                                                        // Congregations" at once.
                                                        if (isSuperAdmin && effectiveCongregationId == null) {
                                                            Text(
                                                                row.congregationName,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.tertiary,
                                                                fontWeight = FontWeight.Medium,
                                                            )
                                                        }
                                                        Text(
                                                            "Last Visit: ${lastVisit?.let { formatVisitDate(it.visitDate) } ?: "—"}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                        Text(
                                                            "Visited By: ${lastVisit?.publisherPersonId?.let { personNames[it] } ?: "—"}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}

/** "Add a persistent filter area" — Search + Search By, Group By (List View
 * only), Municipality, Barangay, and a live "Showing XX Records" count;
 * shown above whichever view (Map or List) is currently active so toggling
 * never drops a filter already applied. Every field here is a thin UI layer
 * over state [TerritoryMapScreen] already owns — no new filtering logic
 * lives in this composable itself. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TerritoryPersistentFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchByField: TerritorySearchByField,
    onSearchByFieldChange: (TerritorySearchByField) -> Unit,
    groupBy: TerritoryGroupBy,
    onGroupByChange: (TerritoryGroupBy) -> Unit,
    // "Province: Nueva Vizcaya (Automatically determined — not editable)" —
    // spec §9's own worked example; shown as plain context, never a
    // dropdown/editable field (spec §1/§7: "Do not display a separate
    // Province dropdown... The user should not be able to change the
    // Province manually"). `null` only for Super Admin's "All Congregations"
    // (no single Province applies), in which case this row is hidden
    // entirely rather than showing a misleading "—".
    province: String?,
    municipality: String?,
    municipalityOptions: List<String>,
    onMunicipalityChange: (String?) -> Unit,
    barangay: String?,
    barangayOptions: List<String>,
    onBarangayChange: (String?) -> Unit,
    // "Congregation is the first filter for every role, but only Super-
    // Admin's own choice ever actually varies it" — absent entirely
    // ([showCongregation] false) for every other role, exactly like a plain
    // Publisher's Territory Map already looked before this field existed.
    showCongregation: Boolean,
    congregationId: String?,
    congregationOptions: List<Congregation>,
    onCongregationChange: (String?) -> Unit,
    resultCount: Int,
    showGroupBy: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Search") },
                    singleLine = true,
                    // "The List View must contain: Search text box, Search
                    // button, Clear button/icon" — filtering is already live
                    // as the user types (every keystroke already re-runs the
                    // exact same search this button would trigger), so
                    // "Search" here is a real, always-present control that
                    // simply confirms/dismisses the keyboard rather than a
                    // second, redundant query path; "Clear" resets the box
                    // (and, via [onSearchQueryChange], collapses the deep-
                    // search auto-expand this same query drove — see
                    // [TerritoryMapScreen]'s own `expandedGroups` effect).
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                            IconButton(onClick = { focusManager.clearFocus() }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search")
                            }
                        }
                    },
                    visualTransformation = VisualTransformation.None,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Hide filters" else "Show filters",
                    )
                }
            }
            if (expanded) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    if (showCongregation) {
                        Box(modifier = Modifier.widthIn(min = 160.dp)) {
                            FilterDropdownField(
                                label = "Congregation",
                                options = congregationOptions.map { it.id to it.name },
                                selectedId = congregationId,
                                onSelected = onCongregationChange,
                                allLabel = "All Congregations",
                            )
                        }
                    }
                    Box(modifier = Modifier.widthIn(min = 160.dp)) {
                        var searchByExpanded by remember { mutableStateOf(false) }
                        androidx.compose.material3.ExposedDropdownMenuBox(expanded = searchByExpanded, onExpandedChange = { searchByExpanded = it }) {
                            OutlinedTextField(
                                value = searchByField.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Search By") },
                                trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = searchByExpanded) },
                                modifier = Modifier.menuAnchor(),
                            )
                            ExposedDropdownMenu(expanded = searchByExpanded, onDismissRequest = { searchByExpanded = false }) {
                                TerritorySearchByField.entries.forEach { option ->
                                    DropdownMenuItem(text = { Text(option.label) }, onClick = { onSearchByFieldChange(option); searchByExpanded = false })
                                }
                            }
                        }
                    }
                    if (showGroupBy) {
                        Box(modifier = Modifier.widthIn(min = 160.dp)) {
                            var groupByExpanded by remember { mutableStateOf(false) }
                            androidx.compose.material3.ExposedDropdownMenuBox(expanded = groupByExpanded, onExpandedChange = { groupByExpanded = it }) {
                                OutlinedTextField(
                                    value = groupBy.label,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Group By") },
                                    trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupByExpanded) },
                                    modifier = Modifier.menuAnchor(),
                                )
                                ExposedDropdownMenu(expanded = groupByExpanded, onDismissRequest = { groupByExpanded = false }) {
                                    TerritoryGroupBy.entries.forEach { option ->
                                        DropdownMenuItem(text = { Text(option.label) }, onClick = { onGroupByChange(option); groupByExpanded = false })
                                    }
                                }
                            }
                        }
                    }
                    // "Sort By: Name A-Z" — spec's only offered value; the
                    // list is already always sorted that way (see
                    // TerritoryMapScreen's own `filtered`), so this is a
                    // fixed, read-only field rather than an editable
                    // dropdown with nothing else to pick.
                    ReadOnlyField("Sort By", "Name A-Z")
                    // "Province: Nueva Vizcaya (Automatically determined —
                    // not editable)" — plain context, not a filter control;
                    // see this parameter's own doc comment for why it's
                    // hidden entirely rather than shown as "—" when Super
                    // Admin has no single congregation in view.
                    if (province != null) {
                        ReadOnlyField("Province", province)
                    }
                    Box(modifier = Modifier.widthIn(min = 160.dp)) {
                        FilterDropdownField(
                            label = "Municipalities",
                            options = municipalityOptions.map { it to it },
                            selectedId = municipality,
                            onSelected = onMunicipalityChange,
                            allLabel = "All Municipalities",
                        )
                    }
                    Box(modifier = Modifier.widthIn(min = 160.dp)) {
                        FilterDropdownField(
                            label = "Barangays",
                            options = barangayOptions.map { it to it },
                            selectedId = barangay,
                            onSelected = onBarangayChange,
                            allLabel = "All Barangays",
                        )
                    }
                }
            }
            // "Showing XX Records" — live, always visible regardless of
            // whether the filter row itself is expanded.
            Text(
                "Showing $resultCount Record${if (resultCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun PipelineStage.statusLabel(): String = when (this) {
    PipelineStage.SEARCHING -> "Searching"
    PipelineStage.RETURN_VISIT -> "Return Visit"
    PipelineStage.BIBLE_STUDY -> "Bible Study"
}

/** "Territory Map → Map View search can be simplified into a single,
 * consistent cascading search system" — Search Category → Specific Record/
 * All → Deeper Text Search, spec's own exact worked layout. Replaces
 * [TerritoryPersistentFilterBar] for Map View only; List View keeps that
 * one, unchanged. Every field here is a thin UI layer over state
 * [TerritoryMapScreen] already owns — no filtering logic lives in this
 * composable itself, same convention [TerritoryPersistentFilterBar] uses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerritoryMapSearchBar(
    category: MapSearchCategory,
    onCategoryChange: (MapSearchCategory) -> Unit,
    selectionId: String?,
    onSelectionChange: (String?) -> Unit,
    selectionOptions: List<Pair<String, String>>,
    deepSearchQuery: String,
    onDeepSearchQueryChange: (String) -> Unit,
    resultCount: Int,
) {
    Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var categoryExpanded by remember { mutableStateOf(false) }
            androidx.compose.material3.ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                OutlinedTextField(
                    value = category.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Search by") },
                    trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    MapSearchCategory.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { onCategoryChange(option); categoryExpanded = false })
                    }
                }
            }
            // "After selecting the first option, display a second dropdown
            // containing the applicable records" — [selectionOptions] is
            // already whichever list applies to [category] (built by
            // [TerritoryMapScreen] itself); "All" is always this dropdown's
            // own leading option (see [FilterDropdownField]'s own doc
            // comment), never a separate control.
            FilterDropdownField(
                label = category.selectLabel,
                options = selectionOptions,
                selectedId = selectionId,
                onSelected = onSelectionChange,
                allLabel = category.allLabel,
            )
            // "The third field (search textbox) should perform a deeper
            // search within the currently selected scope" — restricted to
            // [selectionOptions]' own current scope by [TerritoryMapScreen]'s
            // own `mapDeepSearchedRows`, never the full dataset.
            OutlinedTextField(
                value = deepSearchQuery,
                onValueChange = onDeepSearchQueryChange,
                label = { Text("Search deeper") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (deepSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { onDeepSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Showing $resultCount Record${if (resultCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One row of the List View's Municipality/Barangay directory — [label] is
 * what's shown ("Solano" for a Municipality group, "Quirino, Solano" for a
 * Barangay one); [key] is what [TerritoryMapScreen]'s own `expandedGroups`
 * toggles on (Barangay names alone collide across municipalities, so a
 * Barangay group's key is "municipality|barangay", never the barangay name
 * alone). [rows] is every currently-[filtered] record physically located
 * here — always the *unfiltered-by-status* set, so [TerritoryDirectoryStatus]
 * can compute all three BS/Interested/RV counts from the same list rather
 * than one differently-scoped list per status. */
private data class DirectoryGroup(val key: String, val label: String, val rows: List<TerritoryMapRow>)

/** "Last Visit: May 5, 2025" — spec's own worked example date format. */
private fun formatVisitDate(epochMillis: Long): String =
    java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.US).format(java.util.Date(epochMillis))

/**
 * "Map View" — a real, full-screen, pinch-zoomable embedded map. A `WebView`
 * running Leaflet over OpenStreetMap tiles — switched back from Google Maps
 * Compose per explicit instruction ("change the map to Leaflet.js"). Needs
 * no API key/billing/Google Play Services at all (unlike the brief Google
 * Maps period this app went through), which is also why it renders reliably
 * on a device without genuine, licensed Google Play Services (a real,
 * confirmed failure mode the Google Maps period hit live).
 *
 * "RESPONSIVE MAP FILTERING BY MUNICIPALITY AND BARANGAY" — [rows]/
 * [publisherRows] are already fully narrowed by Municipality/Barangay/Status
 * before they ever reach here (see [TerritoryMapScreen]'s own `mapRows`/
 * `mapPublisherRows`), so this composable needs no separate filtering logic
 * of its own — it only needs to know, when that narrowing leaves nothing to
 * show, which real-world area to center on instead of leaving the camera
 * wherever it was (spec §19/§20/§24's own "still show the ... geographic
 * area"). [selectedAreaQuery] is that forward-geocode query plus the zoom
 * level appropriate for how specific it is (Barangay tighter than
 * Municipality); `null` means no one area is selected ("All Municipalities"/
 * "All Barangays", where an empty [rows] genuinely means "no authorized
 * records at all" instead).
 *
 * "Publishers, Bible Studies, and Return Visits are separate
 * classifications. A person having a GPS location does not automatically
 * make that person a Publisher." — enforced structurally: [MapPoint.kind]
 * is set once, at construction, from the record's *own* type ([pipelinePoints]
 * from [TerritoryMapRow.person]'s [PipelineStage], [publisherPoints] only
 * from an actively-sharing [TerritoryPublisherRow] — see
 * [TerritoryMapViewModel.publisherRowsFor]'s own doc comment for that
 * filter). "Do not use different marker icons for different statuses" (spec
 * §26) — every one of those kinds still renders as the same official red pin
 * on the map itself now; only the Legend and the tap-through detail sheet
 * still distinguish them by color/emoji.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TerritoryLiveMap(
    rows: List<TerritoryMapRow>,
    publisherRows: List<TerritoryPublisherRow>,
    canSeePublisherLocations: Boolean,
    getCurrentLocation: suspend () -> LatLng?,
    hasLocationPermission: () -> Boolean,
    geocodeArea: suspend (String) -> LatLng?,
    // "Do not use an arbitrary circle. Use the actual geographic boundary
    // available from the map/geographic data source" — real polygon lookup
    // (municipality name, barangay name-or-null) -> GeoJSON geometry string,
    // or null when this province isn't covered by the bundled boundary asset
    // yet (see [TerritoryBoundaryRepository]'s own doc comment); that null
    // case is the only time the circle below is still drawn.
    boundaryGeometry: suspend (String, String?) -> String?,
    selectedAreaQuery: Pair<String, Float>?,
    selectedAreaNames: Pair<String, String?>?,
    // "The line barrier should always be displayed regardless of the search
    // level" — every point currently visible on the map (pipeline records +
    // Publisher markers), used to draw a best-effort "scope boundary" (see
    // `window.setScopeBoundary`) whenever [selectedAreaNames] doesn't
    // resolve to a real administrative polygon — an "All ..." selection, a
    // specific Interested Person/Return Visit/Bible Study/Publisher, or a
    // Municipality/Barangay this app has no bundled boundary for.
    scopeBoundaryPoints: List<LatLng>,
    noRecordsAreaLabel: String?,
    focusLat: Double? = null,
    focusLng: Double? = null,
    focusName: String? = null,
    // "Territory Maps — Direct Return Visit Recording" — invoked with the
    // tapped pipeline point's own personId (Searching/Return Visit/Bible
    // Study only, never a Publisher/Me marker — see MapPointDetailsSheet's
    // own gating) when the sheet's Record Visit/View Details button is
    // tapped; the caller re-validates congregation access and owns the
    // actual detail screen (this composable knows nothing about Pipeline).
    // "Record Details... Assigned Publisher" — id -> full name, same map
    // TerritoryMapScreen's own List View "Publisher Assigned" row already
    // reads (see [TerritoryMapViewModel.personNames]); passed in rather than
    // collected here so this composable stays a pure rendering layer.
    publisherNames: Map<String, String>,
    onRecordVisit: (personId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // STEP 4 — validate every coordinate before it ever reaches the map:
    // not null, numeric (guaranteed by the Double type itself, but NaN/
    // Infinite still slip through arithmetic and aren't valid geographic
    // points), and within real lat/lng range. A record that fails this
    // never reaches Leaflet at all — it's counted separately instead, so
    // one bad row can't take the whole map down.
    val pipelinePoints = remember(rows, publisherNames) {
        rows.mapNotNull { row ->
            val lat = row.person.gpsLat
            val lng = row.person.gpsLng
            if (lat != null && lng != null && lat.isFinite() && lng.isFinite() && isValidLatitude(lat) && isValidLongitude(lng)) {
                MapPoint(
                    id = "pipeline_${row.person.id}",
                    // "Status must control the marker" — read fresh from the
                    // record's own current pipelineStage every time [rows]
                    // recomposes (a live Firestore listener), so a reverse/
                    // forward status move immediately swaps the marker's
                    // label rather than ever retaining a stale one.
                    kind = row.person.pipelineStage.toMapPointKind(),
                    lat = lat,
                    lng = lng,
                    name = row.person.name,
                    status = row.person.pipelineStage.statusLabel(),
                    location = row.resolvedLocation ?: formatCoordinatesDms(lat, lng),
                    congregation = row.congregationName,
                    coords = formatCoordinatesDms(lat, lng),
                    updatedAt = null,
                    isCurrentlySharing = null,
                    address = row.person.address.ifBlank { null },
                    province = row.person.province,
                    cityMunicipality = row.person.cityMunicipality,
                    barangay = row.person.barangay,
                    publisherName = row.person.publisherPersonId.takeIf { it.isNotBlank() }?.let { publisherNames[it] },
                )
            } else {
                null
            }
        }
    }
    // "Only Regular Publishers enrolled in the congregation and actively
    // sharing their location may appear" — [publisherRows] already enforces
    // that upstream (see TerritoryMapViewModel.publisherRowsFor).
    val publisherPoints = remember(publisherRows) {
        publisherRows.mapNotNull { row ->
            val lat = row.lat
            val lng = row.lng
            if (lat.isFinite() && lng.isFinite() && isValidLatitude(lat) && isValidLongitude(lng)) {
                MapPoint(
                    id = "publisher_${row.person.id}",
                    kind = MapPointKind.PUBLISHER,
                    lat = lat,
                    lng = lng,
                    name = row.person.fullName,
                    status = row.category?.name?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Publisher",
                    location = "—",
                    congregation = row.congregationName,
                    coords = formatCoordinatesDms(lat, lng),
                    updatedAt = row.updatedAt,
                    isCurrentlySharing = row.isCurrentlySharing,
                )
            } else {
                null
            }
        }
    }
    val points = remember(pipelinePoints, publisherPoints) { pipelinePoints + publisherPoints }
    val invalidCount = rows.size - pipelinePoints.size

    // STEP 8 — diagnostics: same information a `debug` panel would show,
    // just in Logcat rather than on-screen.
    LaunchedEffect(rows, publisherRows) {
        Log.d(TAG, "Records retrieved: ${rows.size}; valid GPS: ${pipelinePoints.size}; invalid/missing GPS: $invalidCount; publishers sharing: ${publisherPoints.size}")
    }

    // "Add the user current location in the map view" — fetched on demand,
    // not on every screen open, so this never surprises anyone with a
    // permission prompt they didn't ask for.
    var myLocation by remember { mutableStateOf<LatLng?>(null) }
    var selectedPointId by remember { mutableStateOf<String?>(null) }
    // "Show details in all categories in territory map even 'My Location'" —
    // a synthetic point built from the live GPS fix, id "me", so tapping the
    // "you are here" dot opens the exact same bottom sheet every other
    // marker already does, without making it a real member of [points].
    val myLocationPoint = remember(myLocation) {
        myLocation?.let { fix ->
            MapPoint(
                id = "me",
                kind = MapPointKind.ME,
                lat = fix.lat,
                lng = fix.lng,
                name = "My Location",
                status = "Your Current Location",
                location = "—",
                congregation = "",
                coords = formatCoordinatesDms(fix.lat, fix.lng),
                updatedAt = null,
                isCurrentlySharing = null,
            )
        }
    }
    val selectedPoint = remember(selectedPointId, points, myLocationPoint) {
        if (selectedPointId == "me") myLocationPoint else points.firstOrNull { it.id == selectedPointId }
    }

    val html = remember(points) { buildTerritoryMapHtml(points) }
    // Bumped on a manual "Retry" tap to force a reload of the same [html].
    var reloadToken by remember { mutableIntStateOf(0) }
    var loadState by remember { mutableStateOf(MapLoadState.LOADING) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // "Add debugging checks" — captured on-device (no adb/Logcat access
    // needed to report back what actually happened) rather than only
    // written to Logcat.
    val consoleMessages = remember { mutableStateListOf<String>() }
    var showDiagnostics by remember { mutableStateOf(false) }

    // Fetches a fresh GPS fix the first time "My Location" is requested —
    // shared so a fix obtained once during this screen's lifetime is never
    // asked for twice. Surfaces the two specific failure states as
    // Snackbars rather than silently doing nothing. Declared after
    // [webViewRef] (a plain local function still reads the *current* value
    // of a `by remember` var, since it isn't itself snapshotted — it only
    // needs to be declared textually after so the name resolves).
    suspend fun ensureMyLocation(): LatLng? {
        myLocation?.let { return it }
        if (!hasLocationPermission()) {
            snackbarHostState.showSnackbar("Location permission is required to display your current position.")
            return null
        }
        val fix = getCurrentLocation()
        if (fix == null) {
            snackbarHostState.showSnackbar("GPS is currently disabled. Please enable location services.")
            return null
        }
        myLocation = fix
        webViewRef?.evaluateJavascript("if (window.setMyLocation) { window.setMyLocation(${fix.lat}, ${fix.lng}); }", null)
        return fix
    }

    // Bug fix history (confirmed live, across multiple real devices, in an
    // earlier pass at this exact WebView+Leaflet setup): loading used to
    // happen directly inside AndroidView's `update` lambda, which Compose
    // re-runs on every recomposition — writing `loadState` there (itself
    // read by the loading/error overlay) triggered another recomposition,
    // re-ran `update`, reloaded the page again, forever, indistinguishable
    // from "nothing renders." A `LaunchedEffect` keyed on the content that
    // should actually trigger a (re)load — not on every recomposition —
    // fixes that; `containerReady` (the AndroidView's first real, non-zero
    // Compose-measured size) gates the very first load so Chromium's first
    // layout pass already sees the correct size, the same way a properly-
    // sized browser window would (a second, deeper WebView-internal-
    // viewport bug is worked around separately in `onPageFinished` below).
    var containerReady by remember { mutableStateOf(false) }

    LaunchedEffect(html, reloadToken, webViewRef, containerReady) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (!containerReady) return@LaunchedEffect
        loadState = MapLoadState.LOADING
        webView.post {
            // A stable https base URL (rather than null/about:blank) so the
            // CDN script/tile requests aren't treated as mixed content.
            webView.loadDataWithBaseURL("https://gopreach.app/", html, "text/html", "UTF-8", null)
        }
    }

    // "RESPONSIVE MAP FILTERING... AUTOMATIC MAP ZOOM" — once the (re)loaded
    // page is actually ready for JS calls, push the live GPS fix back in
    // (if any) and either fit the map to whatever's currently visible, or —
    // when the current Municipality/Barangay + Status combination matches
    // nothing at all — forward-geocode the selected area ([selectedAreaQuery],
    // non-null only when a specific Municipality/Barangay is actually
    // selected) and center there instead of leaving the camera on whatever
    // was visible before the filter changed (spec §19/§20's own "still show
    // the Municipality's/Barangay's geographic area").
    LaunchedEffect(loadState, webViewRef, points, selectedAreaQuery, selectedAreaNames, scopeBoundaryPoints) {
        if (loadState != MapLoadState.LOADED) return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect
        myLocation?.let { fix -> webView.evaluateJavascript("if (window.setMyLocation) { window.setMyLocation(${fix.lat}, ${fix.lng}); }", null) }
        // Real polygon geometry first (no network dependency at all — see
        // [TerritoryBoundaryRepository]); only reach for the unreliable
        // on-device Geocoder (already found flaky on this session's own
        // non-genuine-GMS test device) as a last resort, once there isn't
        // even a point on screen to build a scope boundary from.
        val geometry = selectedAreaNames?.let { (muni, brgy) -> boundaryGeometry(muni, brgy) }
        when {
            geometry != null -> {
                // Draw the area boundary whenever a specific Municipality/
                // Barangay is selected, regardless of whether it has any
                // records — spec ties the boundary to the *selection*, not
                // to an empty result. [setAreaBoundaryGeoJson] itself frames
                // the camera to these exact bounds, taking priority over any
                // marker elsewhere in the province (a Publisher's shared
                // location, say) — see that JS function's own doc comment.
                webView.evaluateJavascript(
                    "if (window.setAreaBoundaryGeoJson) { window.setAreaBoundaryGeoJson($geometry); }",
                    null,
                )
            }
            scopeBoundaryPoints.isNotEmpty() -> {
                // "The line barrier should always be displayed regardless of
                // the search level" — no real administrative polygon applies
                // here (an "All ..." selection, a specific Interested Person/
                // Return Visit/Bible Study/Publisher, or a Municipality/
                // Barangay this app has no bundled boundary for), so hug
                // whatever's actually visible instead; [setScopeBoundary]
                // itself frames the camera to these points, same priority
                // [setAreaBoundaryGeoJson] already gives the real polygon.
                val pointsJson = scopeBoundaryPoints.joinToString(prefix = "[", postfix = "]") { "[${it.lat},${it.lng}]" }
                webView.evaluateJavascript("if (window.setScopeBoundary) { window.setScopeBoundary($pointsJson); }", null)
            }
            selectedAreaQuery != null -> {
                // Nothing visible at all *and* no real polygon — best-effort
                // circle fallback, forward-geocoded from the selected area's
                // name, so an empty Municipality/Barangay still shows
                // *something* real rather than a blank map.
                val (query, zoom) = selectedAreaQuery
                val fix = geocodeArea(query)
                if (fix != null) {
                    // Barangay's own zoom (15f) is tighter than a whole
                    // Municipality's (12.5f) — mirror that into a smaller radius.
                    val radiusMeters = if (zoom >= 15f) 1200.0 else 4000.0
                    webView.evaluateJavascript(
                        "if (window.setAreaBoundary) { window.setAreaBoundary(${fix.lat}, ${fix.lng}, $radiusMeters); }",
                        null,
                    )
                    webView.evaluateJavascript("if (window.territoryMap) { window.territoryMap.setView([${fix.lat}, ${fix.lng}], $zoom); }", null)
                } else {
                    webView.evaluateJavascript("if (window.clearAreaBoundary) { window.clearAreaBoundary(); }", null)
                }
            }
            else -> {
                // Truly nothing selected and nothing visible — no one area/
                // scope to outline at all.
                webView.evaluateJavascript("if (window.clearAreaBoundary) { window.clearAreaBoundary(); }", null)
            }
        }
    }

    // "Clicking coordinates should open the Territory Map centered on the
    // Publisher's latest location" — runs once, the first time the map
    // finishes loading with a focus target actually present (see
    // TerritoryMapScreen's own `focusLat`/`focusLng`, only ever non-null
    // when reached via Share Location's "open in Territory Map" action).
    var hasAppliedFocus by remember { mutableStateOf(false) }
    LaunchedEffect(loadState, webViewRef, focusLat, focusLng) {
        if (loadState != MapLoadState.LOADED || hasAppliedFocus) return@LaunchedEffect
        val lat = focusLat
        val lng = focusLng
        if (lat == null || lng == null) return@LaunchedEffect
        hasAppliedFocus = true
        val nearest = points.filter { it.kind == MapPointKind.PUBLISHER }.minByOrNull { haversineMeters(lat, lng, it.lat, it.lng) }
        if (nearest != null && haversineMeters(lat, lng, nearest.lat, nearest.lng) < 100) {
            selectedPointId = nearest.id
        }
        webViewRef?.evaluateJavascript("if (window.territoryMap) { window.territoryMap.setView([$lat, $lng], 17); }", null)
    }

    // "Highlight the selected marker" — covers both a manual tap (which
    // already highlights itself immediately in JS) and the focus-effect's
    // own auto-selection above, plus clears the highlight the instant the
    // bottom sheet is dismissed (selectedPointId -> null).
    LaunchedEffect(selectedPointId, webViewRef, loadState) {
        if (loadState == MapLoadState.LOADED) {
            val idJs = selectedPointId?.let { "'${jsEscape(it)}'" } ?: "null"
            webViewRef?.evaluateJavascript("if (window.setSelectedMarker) { window.setSelectedMarker($idJs); }", null)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            // Bug fix (confirmed live): Leaflet measures its container's
            // pixel size exactly once, the moment `L.map(...)` runs, and
            // never re-measures on its own — `onSizeChanged` fires with this
            // View's *actual* settled pixel size every time Compose lays it
            // out (including the first time), and telling Leaflet to
            // `invalidateSize()` right then makes it re-measure and actually
            // start requesting tiles.
            modifier = Modifier.fillMaxSize().onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    containerReady = true
                    webViewRef?.evaluateJavascript("if (window.territoryMap) { window.territoryMap.invalidateSize(); }", null)
                }
            },
            factory = { ctx ->
                if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Leaflet handles pinch/double-tap zoom itself via touch
                    // events — the WebView's own native zoom would otherwise
                    // fight Leaflet's for the same gesture.
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    // A WebView embedded via Compose interop can render
                    // solid black/blank on some devices under hardware-
                    // accelerated layering. Software layering is slightly
                    // slower to draw but reliably shows the actual page.
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    // A marker tap calls back into Kotlin with just its id;
                    // the native ModalBottomSheet below looks the rest up
                    // from [points] itself, rather than round-tripping every
                    // field back out through the bridge as strings.
                    val self = this
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun showDetails(id: String) {
                                self.post { selectedPointId = id }
                            }
                        },
                        "AndroidBridge",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            loadState = MapLoadState.LOADED
                            view?.evaluateJavascript("if (window.territoryMap) { window.territoryMap.invalidateSize(); }", null)
                            // Belt-and-suspenders for a deeper WebView-internal-
                            // viewport bug confirmed live on-device (Java-side
                            // View bounds correct via accessibility dump, yet
                            // Leaflet's own container still measured 0 height
                            // even minutes later, even after invalidateSize()):
                            // Chromium's out-of-process renderer never actually
                            // received the real size at all. Forcing a real
                            // (if momentary) size change is the documented fix
                            // for exactly this symptom.
                            view?.let { wv ->
                                val realHeight = wv.height
                                val lp = wv.layoutParams
                                if (realHeight > 0 && lp != null) {
                                    lp.height = realHeight - 1
                                    wv.layoutParams = lp
                                    wv.requestLayout()
                                    wv.post {
                                        lp.height = realHeight
                                        wv.layoutParams = lp
                                        wv.requestLayout()
                                        wv.postDelayed({
                                            wv.evaluateJavascript("if (window.territoryMap) { window.territoryMap.invalidateSize(); }", null)
                                        }, 50)
                                    }
                                }
                            }
                        }
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            // Only the top-level page failing counts as the
                            // map itself failing — a single missed sub-
                            // resource (one map tile timing out, say)
                            // shouldn't flip the whole view into an error
                            // state when the map is otherwise usable.
                            if (request?.isForMainFrame == true) {
                                loadState = MapLoadState.FAILED
                                val detail = "Main frame load error: ${error?.errorCode} ${error?.description} (${request.url})"
                                Log.e(TAG, detail)
                                consoleMessages.add(detail)
                            }
                        }
                    }
                    // Surfaces real browser-side JS errors (a CDN script that
                    // 404'd, a Leaflet exception, anything) directly on-device.
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            val line = "${consoleMessage.messageLevel()}: ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                            Log.d(TAG, "WebView console: $line")
                            consoleMessages.add(line)
                            if (consoleMessages.size > 30) consoleMessages.removeAt(0)
                            return true
                        }
                    }
                }.also { webViewRef = it }
            },
            update = {},
        )

        if (loadState == MapLoadState.LOADING) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (loadState == MapLoadState.FAILED) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Unable to load the map. Check your internet connection and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                consoleMessages.lastOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { reloadToken++ }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Retry")
                    }
                    OutlinedButton(onClick = { showDiagnostics = true }) { Text("Details") }
                }
            }
        }

        // "If the selected [scope] has no records, still show its geographic
        // area and display: 'No records found for [scope].'" — generalized
        // to every Map View search category (spec's own cascading-search
        // request), not just Municipality/Barangay; [noRecordsAreaLabel] is
        // null only at the screen's absolute default (Municipalities → All),
        // so this never alarms a brand-new congregation with zero territory
        // records yet before they've actually searched for anything.
        if (loadState == MapLoadState.LOADED && points.isEmpty() && noRecordsAreaLabel != null) {
            Card(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            ) {
                Text(
                    "No records found for $noRecordsAreaLabel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }

        if (loadState == MapLoadState.LOADED && invalidCount > 0) {
            Card(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            ) {
                Text(
                    "$invalidCount record${if (invalidCount == 1) "" else "s"} without a valid GPS location ${if (invalidCount == 1) "isn't" else "aren't"} shown on the map.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        // "Add a floating Refresh button" — re-centers on whatever's
        // currently visible (or the selected area, if nothing matches) and
        // re-fetches location data.
        SmallFloatingActionButton(
            onClick = {
                myLocation = null
                scope.launch {
                    reloadToken++
                    snackbarHostState.showSnackbar("Territory map updated successfully.")
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 76.dp),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh map")
        }

        // "Add the user current location in the map view" — a small
        // floating action button rather than a dropdown entry now that the
        // old category dropdown is gone; still opt-in, still asks for
        // permission the first time.
        SmallFloatingActionButton(
            onClick = { scope.launch { ensureMyLocation() } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 140.dp),
        ) {
            Icon(Icons.Rounded.Map, contentDescription = "Show my location")
        }

        // Always available, not just on failure — lets whoever's testing
        // this confirm exactly what's happening (records/points counts,
        // load state, any console error) even when the map *looks* like
        // it's working but markers still aren't showing up right, without
        // needing adb/Logcat access to report it back accurately.
        IconButton(
            onClick = { showDiagnostics = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        ) {
            Icon(Icons.Rounded.Info, contentDescription = "Map diagnostics", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }

    if (selectedPoint != null) {
        MapPointDetailsSheet(
            point = selectedPoint,
            myLocation = myLocation,
            onDismiss = { selectedPointId = null },
            onOpenInMaps = { openCoordinatesInMaps(context, selectedPoint.lat, selectedPoint.lng, selectedPoint.name) },
            onRecordVisit = {
                selectedPointId = null
                onRecordVisit(selectedPoint.id.removePrefix("pipeline_"))
            },
        )
    }

    if (showDiagnostics) {
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
            onDismissRequest = { showDiagnostics = false },
            title = { Text("Map Diagnostics") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Records retrieved: ${rows.size}", style = MaterialTheme.typography.bodySmall)
                    Text("Valid GPS locations: ${pipelinePoints.size}", style = MaterialTheme.typography.bodySmall)
                    Text("Invalid/missing GPS: $invalidCount", style = MaterialTheme.typography.bodySmall)
                    Text("Publishers sharing location: ${publisherPoints.size}", style = MaterialTheme.typography.bodySmall)
                    Text("Map status: ${loadState.name}", style = MaterialTheme.typography.bodySmall)
                    if (consoleMessages.isEmpty()) {
                        Text("No console messages yet.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Console log:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        consoleMessages.forEach { line ->
                            Text(line, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnostics = false }) { Text("Close") }
            },
        )
    }
}

/** Builds the Leaflet+OpenStreetMap page — no API key, no billing, no Google
 * Play Services dependency at all (this is exactly the point of switching
 * back to Leaflet.js). [points] arrives already fully filtered by
 * Municipality/Barangay/Status (see [TerritoryMapScreen]'s own `mapRows`),
 * so unlike the pre-Google-Maps version of this function there is no
 * `applyFilter`/category-dropdown/"Nearest X" JS machinery left to build —
 * every point given here is simply plotted. */
private fun buildTerritoryMapHtml(points: List<MapPoint>): String {
    val pointsJson = points.joinToString(",", prefix = "[", postfix = "]") { p ->
        """{id:"${jsEscape(p.id)}",lat:${p.lat},lng:${p.lng},name:"${jsEscape(p.name)}",status:"${jsEscape(p.status)}"}"""
    }
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/MarkerCluster.css">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/MarkerCluster.Default.css">
        <style>
          /* height:100% cascading from html->body->#map depends on every
             ancestor resolving to a *definite* pixel height; anchoring #map
             with all four absolute offsets sizes it directly from the
             viewport instead (see TerritoryLiveMap's own bug-fix history). */
          html, body { height: 100%; margin: 0; padding: 0; }
          #map { position: absolute; top: 0; left: 0; right: 0; bottom: 0; }
          .territory-label { background: rgba(255,255,255,0.92); border: none; box-shadow: 0 1px 3px rgba(0,0,0,0.3); padding: 1px 6px; font-size: 12px; white-space: nowrap; }
          .territory-marker { background: transparent; border: none; }
        </style>
        </head>
        <body>
        <div id="map"></div>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet.markercluster/1.5.3/leaflet.markercluster.js"></script>
        <script>
        try {
          var points = $pointsJson;
          var map = L.map('map', { zoomControl: true });
          window.territoryMap = map;
          // "Change the map to OpenFreeMap" was tried and reverted: its
          // Liberty style only renders through MapLibre GL, which needs real
          // WebGL — confirmed live, twice, that this same session's own
          // Huawei/HMS-less test device's WebView reports a working WebGL
          // context and even fires MapLibre's own "style loaded" event
          // successfully, yet never actually paints a single pixel (a
          // broken GPU pipeline lying about working, invisible to any
          // JS-level check). Plain raster OpenStreetMap tiles have no WebGL
          // dependency at all and are proven reliable on every device this
          // app has been tested on all session — kept as the one tile source.
          var tiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; OpenStreetMap contributors',
          }).addTo(map);
          var tileErrorCount = 0;
          tiles.on('tileerror', function(e) { tileErrorCount++; console.error('Tile load failed (' + tileErrorCount + ')'); });

          // "Continue using the ... official red 3D location pin icon for
          // all statuses/categories ... Do not use different marker icons
          // for different statuses" (spec §26) — one shape for every
          // record; a selected one just grows and turns gold, the same
          // "which one's highlighted" convention Google Maps' own default
          // marker uses.
          function buildPinIcon(selected) {
            var w = selected ? 38 : 30, h = selected ? 53 : 42;
            var fill = selected ? '#FFC107' : '#EA4335';
            var html = '<svg width="' + w + '" height="' + h + '" viewBox="0 0 30 42" xmlns="http://www.w3.org/2000/svg">' +
              '<path d="M15 0C6.7 0 0 6.7 0 15c0 11 15 27 15 27s15-16 15-27C30 6.7 23.3 0 15 0z" fill="' + fill + '" stroke="#8a1c14" stroke-width="1"/>' +
              '<circle cx="15" cy="15" r="6.5" fill="#ffffff"/></svg>';
            return L.divIcon({ className: 'territory-marker', html: html, iconSize: [w, h], iconAnchor: [w / 2, h] });
          }
          // "My Location" isn't a record status at all, so it keeps its own
          // distinct look (a plain blue dot) instead of looking like just
          // another red pin.
          function buildMeIcon(selected) {
            var s = selected ? 26 : 20;
            var html = '<div style="width:' + s + 'px;height:' + s + 'px;border-radius:50%;background:#1a73e8;border:3px solid #ffffff;box-shadow:0 1px 4px rgba(0,0,0,.5);"></div>';
            return L.divIcon({ className: 'territory-marker', html: html, iconSize: [s, s], iconAnchor: [s / 2, s / 2] });
          }

          // "Highlight the selected marker" — tracks whichever marker (or
          // 'me') was last tapped/auto-selected and swaps only that one's
          // icon back and forth between its normal and selected style.
          var selectedMarkerId = null;
          window.setSelectedMarker = function(id) {
            if (selectedMarkerId && selectedMarkerId !== id) {
              var prev = selectedMarkerId === 'me' ? window.myLocationMarker : markersById[selectedMarkerId];
              if (prev) prev.setIcon(prev._isMe ? buildMeIcon(false) : buildPinIcon(false));
            }
            selectedMarkerId = id || null;
            if (id) {
              var current = id === 'me' ? window.myLocationMarker : markersById[id];
              if (current) current.setIcon(current._isMe ? buildMeIcon(true) : buildPinIcon(true));
            }
          };

          // Clusters nearby markers into one numbered bubble that expands on
          // tap/zoom — keeps a dense subdivision from turning into an
          // unreadable pile of overlapping pins. "You are here" is a
          // separate marker outside this cluster entirely.
          var cluster = L.markerClusterGroup();
          var markers = [];
          var markersById = {};
          points.forEach(function(p) {
            var marker = L.marker([p.lat, p.lng], { icon: buildPinIcon(false) });
            // "Every visible marker must display: Name (Status)" (spec §26).
            marker.bindTooltip(p.name + ' (' + p.status + ')', { permanent: true, direction: 'right', offset: [10, 0], className: 'territory-label' });
            marker.on('click', function() {
              window.setSelectedMarker(p.id);
              if (window.AndroidBridge) { AndroidBridge.showDetails(p.id); }
            });
            cluster.addLayer(marker);
            markers.push(marker);
            markersById[p.id] = marker;
          });
          map.addLayer(cluster);

          // "RESPONSIVE MAP FILTERING... AUTOMATIC MAP ZOOM" — a single
          // point gets a comfortable street-level zoom (a 0-span "bounds"
          // around one coordinate would otherwise throw); more than one
          // fits the smallest bounds containing all of them, padded so no
          // marker lands clipped at the screen edge. Kotlin calls this
          // (via window.fitToMarkers) every time the already-filtered
          // [points] set actually changes shape.
          window.fitToMarkers = function() {
            if (markers.length === 1) {
              map.setView(markers[0].getLatLng(), 16);
            } else if (markers.length > 1) {
              map.fitBounds(L.featureGroup(markers).getBounds().pad(0.2));
            }
          };
          if (markers.length > 0) {
            window.fitToMarkers();
          } else {
            // Philippines-wide fallback until Kotlin's own geocode (for a
            // selected-but-empty Municipality/Barangay) or a fresh filter
            // (with real markers) repositions it.
            map.setView([12.8797, 121.7740], 6);
          }

          // "Add the user current location in the map view" — a distinct
          // dot, outside the cluster group (always visible), added once
          // Android actually has a GPS fix.
          window.setMyLocation = function(lat, lng) {
            if (window.myLocationMarker) { map.removeLayer(window.myLocationMarker); }
            window.myLocationMarker = L.marker([lat, lng], { icon: buildMeIcon(selectedMarkerId === 'me'), zIndexOffset: 1000 }).addTo(map);
            window.myLocationMarker._isMe = true;
            window.myLocationMarker.bindTooltip('My Location', { permanent: true, direction: 'right', offset: [10, 0], className: 'territory-label' });
            window.myLocationMarker.on('click', function() {
              window.setSelectedMarker('me');
              if (window.AndroidBridge) { AndroidBridge.showDetails('me'); }
            });
          };

          // "Draw a professional geographic boundary around the selected
          // Municipality/Barangay" (spec §9). No real polygon/GeoJSON
          // boundary dataset ships with this app (the bundled PSGC table is
          // names/hierarchy only — see PsgcDao — with no shape data), so
          // fabricating a precise outline is not honest; this draws a plain
          // dashed circle around the area's geocoded center instead, sized
          // to roughly the area's real footprint (Barangay vs. Municipality
          // get different Kotlin-supplied radii — see [selectedAreaQuery]'s
          // own zoom levels), which is disclosed to the user as an
          // approximate area indicator, not a true administrative boundary.
          window.areaBoundary = null;
          window.setAreaBoundary = function(lat, lng, radiusMeters) {
            window.clearAreaBoundary();
            window.areaBoundary = L.circle([lat, lng], {
              radius: radiusMeters,
              color: '#1a73e8',
              weight: 2,
              dashArray: '6,6',
              fill: true,
              fillColor: '#1a73e8',
              fillOpacity: 0.06,
              interactive: false,
            }).addTo(map);
          };
          // The real thing: an actual Municipality/Barangay polygon (from
          // [TerritoryBoundaryRepository]'s bundled NAMRIA/PSA/OCHA boundary
          // data), drawn the same dashed-blue style as the circle fallback
          // above so a real boundary and an approximate one never look
          // meaningfully different to the user in areas where real data
          // exists vs. doesn't.
          window.setAreaBoundaryGeoJson = function(geometry) {
            window.clearAreaBoundary();
            window.areaBoundary = L.geoJSON(geometry, {
              style: { color: '#1a73e8', weight: 2, dashArray: '6,6', fill: true, fillColor: '#1a73e8', fillOpacity: 0.06 },
              interactive: false,
            }).addTo(map);
            // Frame the real polygon directly from its own bounds — no
            // dependency on the on-device Geocoder (already found unreliable
            // on this session's own non-genuine-GMS test device) just to
            // center the camera on it. Always wins over [window.fitToMarkers]
            // here: selecting a specific Municipality/Barangay is a
            // deliberate "focus on this area" action, and any of its own
            // records are still inside these bounds regardless — a marker
            // from some other, unrelated area (e.g. a Publisher sharing
            // location elsewhere in the province) must never keep the camera
            // away from the area the user actually selected.
            map.fitBounds(window.areaBoundary.getBounds().pad(0.15));
          };
          window.clearAreaBoundary = function() {
            if (window.areaBoundary) { map.removeLayer(window.areaBoundary); window.areaBoundary = null; }
          };

          // "The line barrier should always be displayed regardless of the
          // search level" (Map View cascading-search spec) — a best-effort
          // "scope boundary" hugging whatever points are actually visible
          // right now, for every case with no real administrative polygon:
          // an "All ..." selection, a specific Interested Person/Return
          // Visit/Bible Study/Publisher, or a Municipality/Barangay this app
          // has no bundled boundary for. A single point gets a small circle
          // (a polygon needs at least 3); two or more get the convex hull —
          // the tightest real polygon around every point, giving something
          // closer to an actual "territory" shape than a rectangle would —
          // via a plain monotone-chain implementation (no extra library).
          // Same dashed-blue style as the two boundary kinds above so none
          // of the three ever look meaningfully different to the user.
          function convexHull(pts) {
            var points = pts.slice().sort(function(a, b) { return a[0] - b[0] || a[1] - b[1]; });
            function cross(o, a, b) { return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]); }
            var lower = [];
            for (var i = 0; i < points.length; i++) {
              while (lower.length >= 2 && cross(lower[lower.length - 2], lower[lower.length - 1], points[i]) <= 0) lower.pop();
              lower.push(points[i]);
            }
            var upper = [];
            for (var j = points.length - 1; j >= 0; j--) {
              while (upper.length >= 2 && cross(upper[upper.length - 2], upper[upper.length - 1], points[j]) <= 0) upper.pop();
              upper.push(points[j]);
            }
            lower.pop(); upper.pop();
            return lower.concat(upper);
          }
          window.setScopeBoundary = function(points) {
            window.clearAreaBoundary();
            if (!points || points.length === 0) { return; }
            if (points.length === 1) {
              window.areaBoundary = L.circle(points[0], {
                radius: 400,
                color: '#1a73e8', weight: 2, dashArray: '6,6',
                fill: true, fillColor: '#1a73e8', fillOpacity: 0.06,
                interactive: false,
              }).addTo(map);
              map.setView(points[0], 16);
              return;
            }
            var unique = [];
            var seen = {};
            points.forEach(function(p) {
              var key = p[0] + ',' + p[1];
              if (!seen[key]) { seen[key] = true; unique.push(p); }
            });
            var hull = unique.length >= 3 ? convexHull(unique) : unique;
            if (hull.length < 3) {
              // Every point collinear/identical after dedup — a polygon
              // would be degenerate; fall back to a circle around the
              // group's own bounds instead of drawing nothing.
              var bounds = L.latLngBounds(unique);
              window.areaBoundary = L.circle(bounds.getCenter(), {
                radius: Math.max(300, bounds.getCenter().distanceTo(bounds.getNorthEast())),
                color: '#1a73e8', weight: 2, dashArray: '6,6',
                fill: true, fillColor: '#1a73e8', fillOpacity: 0.06,
                interactive: false,
              }).addTo(map);
              map.fitBounds(bounds.pad(0.25));
              return;
            }
            window.areaBoundary = L.polygon(hull, {
              color: '#1a73e8', weight: 2, dashArray: '6,6',
              fill: true, fillColor: '#1a73e8', fillOpacity: 0.06,
              interactive: false,
            }).addTo(map);
            map.fitBounds(window.areaBoundary.getBounds().pad(0.2));
          };

          // JS-side fallback for the exact same "container wasn't its final
          // size yet when L.map() ran" issue the Android side's own hooks
          // already cover.
          window.addEventListener('resize', function() { map.invalidateSize(); });
          setTimeout(function() {
            map.invalidateSize();
            var size = map.getSize();
            var container = document.getElementById('map');
            console.log('Diag: map size=' + size.x + 'x' + size.y + ', container clientWidth/Height=' + container.clientWidth + '/' + container.clientHeight + ', markers=' + markers.length);
          }, 100);
          setTimeout(function() { map.invalidateSize(); }, 500);
          setTimeout(function() { map.invalidateSize(); }, 1500);
        } catch (e) {
          console.error('Territory map script threw: ' + (e && (e.stack || e.message)) + ' [' + (typeof e) + ']');
        }
        </script>
        </body>
        </html>
    """.trimIndent()
}

private fun jsEscape(text: String): String =
    text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")

/** "Display a compact information card/bottom sheet" — the exact field set
 * and wording from the spec's own three worked examples, built from
 * whichever [MapPoint] was last tapped ([TerritoryLiveMap.selectedPointId]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapPointDetailsSheet(
    point: MapPoint,
    myLocation: LatLng?,
    onDismiss: () -> Unit,
    onOpenInMaps: () -> Unit,
    onRecordVisit: () -> Unit,
) {
    // "Clearly distinguish Return Visit locations from other territory
    // locations" — already true structurally (see [MapPoint.kind]'s own doc
    // comment); this just decides which kinds get a Record Visit button at
    // all — a Publisher marker/your own location was never a pipeline
    // record to record a visit against.
    val isPipelinePoint = point.kind == MapPointKind.SEARCHING || point.kind == MapPointKind.RETURN_VISIT || point.kind == MapPointKind.BIBLE_STUDY
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 24.dp)) {
            // "Keep the category icon visible" — the same emoji as the
            // marker itself, legend, and dropdown, shown large above the
            // name (spec's own worked examples: "👤 / Juan Dela Cruz").
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(markerColorFor(point.kind)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emojiFor(point.kind), style = MaterialTheme.typography.titleLarge)
                }
                Column {
                    Text(point.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(categoryLabelFor(point.kind), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(point.status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))

            if (point.kind == MapPointKind.ME) {
                // "You are here" — its own coordinates in place of the
                // Location/Distance rows every other kind shows (a distance
                // from yourself to yourself is meaningless).
                DetailRow(label = "Coordinates", value = point.coords)
            } else {
                DetailRow(
                    label = "Location",
                    value = when {
                        point.kind == MapPointKind.PUBLISHER && point.isCurrentlySharing == true -> "Currently Sharing"
                        point.kind == MapPointKind.PUBLISHER -> "Last Known Location"
                        else -> "Registered Location"
                    },
                )
                if (myLocation != null) {
                    DetailRow(label = "Distance", value = formatDistance(haversineMeters(myLocation.lat, myLocation.lng, point.lat, point.lng)))
                }
                if (point.kind == MapPointKind.PUBLISHER && point.updatedAt != null) {
                    DetailRow(label = "Last Updated", value = formatRelativeTime(point.updatedAt))
                }
                if (isPipelinePoint) {
                    DetailRow(label = "Congregation", value = point.congregation)
                    // "Record Details" spec — Assigned Publisher, Province,
                    // Municipality, Barangay, Complete Address, all straight
                    // from the same InterestedPerson fields House Holder
                    // Visit History's own List View/print already surface;
                    // each is its own row only when actually set (spec's
                    // "where applicable"), never a blank/"—" row for a field
                    // nobody filled in. "Related Visit History" and any
                    // remaining fields stay behind "View Details / Record
                    // Visit" below — the full Pipeline detail screen this
                    // reuses is where that history (and its own Add/Edit/
                    // Delete ownership rules) already lives, rather than a
                    // second, parallel summary of it here.
                    point.publisherName?.let { DetailRow(label = "Assigned Publisher", value = it) }
                    point.province?.let { DetailRow(label = "Province", value = it) }
                    point.cityMunicipality?.let { DetailRow(label = "Municipality", value = it) }
                    point.barangay?.let { DetailRow(label = "Barangay", value = it) }
                    point.address?.let { DetailRow(label = "Complete Address", value = it) }
                }
            }

            // "Territory Map → Tap Return Visit Marker → Return Visit
            // Details → Record Visit" — the primary action for a pipeline
            // point; opens the full details + Visit History screen (see
            // TerritoryMapScreen's own onRecordVisit wiring), which is also
            // where the complete Visit History and "Log Visit" FAB already
            // live (same screen the Pipeline module itself uses).
            if (isPipelinePoint) {
                Button(onClick = onRecordVisit, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("View Details / Record Visit")
                }
            }
            OutlinedButton(onClick = onOpenInMaps, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Open in Maps")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun markerColorFor(kind: MapPointKind): Color = when (kind) {
    MapPointKind.PUBLISHER -> Color(0xFF1A73E8)
    MapPointKind.BIBLE_STUDY -> Color(0xFF8E24AA)
    MapPointKind.RETURN_VISIT -> Color(0xFFFB8C00)
    MapPointKind.SEARCHING -> Color(0xFF616161)
    MapPointKind.ME -> Color(0xFF34A853)
}

/** "The icon identifies the person's current classification; GPS location
 * does not determine classification" — the one place every one of the five
 * category emoji is defined; the Legend and the bottom sheet both reuse it
 * verbatim (the map's own pins are all the same official red pin now — see
 * [buildTerritoryMapHtml]'s own doc comment — so this no longer feeds the
 * marker itself, only the reference key and the tap-through detail view). */
private fun emojiFor(kind: MapPointKind): String = when (kind) {
    MapPointKind.PUBLISHER -> "👤"
    MapPointKind.BIBLE_STUDY -> "📖"
    MapPointKind.RETURN_VISIT -> "🔄"
    MapPointKind.SEARCHING -> "⭐"
    MapPointKind.ME -> "📍"
}

private fun categoryLabelFor(kind: MapPointKind): String = when (kind) {
    MapPointKind.PUBLISHER -> "Publisher"
    MapPointKind.BIBLE_STUDY -> "Bible Study"
    MapPointKind.RETURN_VISIT -> "Return Visit"
    MapPointKind.SEARCHING -> "Searching Interested Person"
    MapPointKind.ME -> "My Location"
}

private fun PipelineStage.toMapPointKind(): MapPointKind = when (this) {
    PipelineStage.SEARCHING -> MapPointKind.SEARCHING
    PipelineStage.RETURN_VISIT -> MapPointKind.RETURN_VISIT
    PipelineStage.BIBLE_STUDY -> MapPointKind.BIBLE_STUDY
}

/** "Juan Dela Cruz — 350 meters away" — meters under 1km, one decimal of
 * kilometers beyond that. */
private fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m" else "${"%.1f".format(meters / 1000)} km"

/** "Last Updated: Just now" — coarse, human buckets rather than a raw
 * timestamp; matches the spec's own worked example verbatim for the first
 * bucket. */
private fun formatRelativeTime(updatedAtMillis: Long): String {
    val minutes = (System.currentTimeMillis() - updatedAtMillis) / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
        minutes < 24 * 60 -> "${minutes / 60} hour${if (minutes / 60 == 1L) "" else "s"} ago"
        else -> "${minutes / (24 * 60)} day${if (minutes / (24 * 60) == 1L) "" else "s"} ago"
    }
}

private enum class MapLoadState { LOADING, LOADED, FAILED }

/** What kind of thing a [MapPoint] represents. "Publishers, Bible Studies,
 * and Return Visits are separate
 * classifications" — this is the one place that classification is decided,
 * at construction (see [TerritoryLiveMap]'s `pipelinePoints`/`publisherPoints`),
 * never inferred later from "has coordinates." */
private enum class MapPointKind {
    SEARCHING,
    RETURN_VISIT,
    BIBLE_STUDY,
    PUBLISHER,
    /** "Show details in all categories in territory map even 'My Location'"
     * — the "you are here" dot is otherwise never a member of [points] at
     * all (see [TerritoryLiveMap]'s own "do not treat the logged-in user's
     * location as a Bible Study or Return Visit"); this exists purely so
     * tapping it can open the same bottom sheet every other marker already
     * does, via a synthetic point built from [TerritoryLiveMap.myLocation]
     * (see `myLocationPoint`) rather than a real [TerritoryMapRow]/
     * [TerritoryPublisherRow]. */
    ME,
}

/** One plottable dot on the Territory Map — a pipeline record ([MapPointKind.SEARCHING]/
 * [MapPointKind.RETURN_VISIT]/[MapPointKind.BIBLE_STUDY]) or a publisher
 * currently sharing their location ([MapPointKind.PUBLISHER]), unified into
 * one shape so the map/list/bottom-sheet only ever have to know one shape.
 * [updatedAt]/[isCurrentlySharing] are only ever non-null for [MapPointKind.PUBLISHER]. */
private data class MapPoint(
    val id: String,
    val kind: MapPointKind,
    val lat: Double,
    val lng: Double,
    val name: String,
    val status: String,
    val location: String,
    val congregation: String,
    val coords: String,
    val updatedAt: Long?,
    val isCurrentlySharing: Boolean?,
    // "Record Details" spec — the bottom sheet's own extra fields for a
    // pipeline point (Searching/Return Visit/Bible Study); always null for
    // PUBLISHER/ME, which have no InterestedPerson behind them at all.
    // [address]/[province]/[cityMunicipality]/[barangay] mirror the same
    // three-level Philippine location fields House Holder Visit History's
    // own print view already surfaces (see that screen's own doc comment on
    // why "Contact" is deliberately never one of these — InterestedPerson
    // has no such field in the data model, so it was never invented here
    // either); [publisherName] is the assigned Publisher's full name.
    val address: String? = null,
    val province: String? = null,
    val cityMunicipality: String? = null,
    val barangay: String? = null,
    val publisherName: String? = null,
)

/** Great-circle distance in meters — plenty accurate for "which of these
 * handful of nearby records is closest," not meant for long-range routing. */
private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}


/** One Search By/Sub Filter dropdown — [options] is (id, displayName) pairs;
 * a leading "All" entry clears the selection back to null. Every one of
 * these dropdowns is optional, so "nothing picked" is always a real,
 * reachable state, not just its initial one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdownField(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
    // "Municipalities: [All Municipalities ▼]" / "Barangays: [All Barangays
    // ▼]" — spec's own worked examples show the "nothing narrowed" choice as
    // an actual, always-visible selected value ("All Municipalities"), not a
    // blank field with a generic "All" hint; every other caller of this
    // shared dropdown keeps the plain "All" default.
    allLabel: String = "All",
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.first == selectedId }?.second ?: allLabel
    androidx.compose.material3.ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelected(null); expanded = false })
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelected(id); expanded = false })
            }
        }
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        visualTransformation = VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}
