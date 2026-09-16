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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.ui.components.GroupColorPalette
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

private fun TerritoryMapRow.matchesSearch(field: TerritorySearchByField, rawQuery: String, publisherName: String?, groupName: String? = null): Boolean {
    if (rawQuery.isBlank()) return true
    // "TERRITORY MAP LIST VIEW – GLOBAL SEARCH" §6's own worked example
    // types the Publisher's displayed "RP <name>" form (the same "RP "
    // prefix every label/tooltip on this map's own JS side already adds —
    // see `publisherLabelHtml`) straight into the search box, but the
    // stored/matched [publisherName] itself never carries that prefix — a
    // literal `"RP Evarose Fernandez" in "Evarose Fernandez"` contains-check
    // would otherwise always fail. Stripped once, up front, so every field
    // below matches on the query a user actually typed either way.
    val trimmed = rawQuery.trim()
    val query = if (trimmed.startsWith("RP ", ignoreCase = true)) trimmed.substring(3).trim().ifBlank { trimmed } else trimmed
    fun String?.has() = this != null && contains(query, ignoreCase = true)
    return when (field) {
        // "Include Group name or number in Search All" — [groupName] is the
        // Congregation Group this record's own assigned Publisher belongs
        // to; typing "Group 5" or just "5" both match since the name itself
        // ("Group 5") already contains the number.
        TerritorySearchByField.ALL ->
            person.name.has() || person.cityMunicipality.has() || person.barangay.has() ||
                person.pipelineStage.statusLabel().has() || publisherName.has() || groupName.has()
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
     * ("Select Municipality", "Select Publisher", ...); blank for [ALL],
     * which has no second dropdown at all (see [TerritoryMapSearchBar]'s own
     * gating) — there's nothing narrower than "every category" to pick. */
    val selectLabel: String,
    /** "'All' should always be available... All Municipalities / All
     * Barangays / All Interested Persons / All Return Visits / All Bible
     * Studies / All Publishers" — spec's exact wording per category. */
    val allLabel: String,
) {
    /** "Add a new 'All' option [to the Search by dropdown]... set it as the
     * default... search across all available territory-related categories"
     * — the one category with no [stage] narrowing and no second dropdown;
     * [mapScopedRows] passes every congregation-scoped row straight through
     * unfiltered for it, same as every other category's own "All ..."
     * second-dropdown option already does for *its* narrower scope. Declared
     * first so it's also the first, and default, entry in the dropdown list
     * itself (see [TerritoryMapScreen]'s own `mapSearchCategory` initial
     * state and [MapSearchCategory.entries]'s iteration order below).
     */
    ALL("All", null, "", "All Records"),
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
    // Bug fix ("TERRITORY MAP LIST VIEW – GLOBAL SEARCH" — confirmed live: a
    // real, same-congregation Bible Study record in Bayombong never appeared
    // in List View, searched or not, even though the exact same record was
    // directly reachable from Map View) — [advancedFilter.province] is
    // auto-set once from the congregation's own [Congregation.province] (see
    // the two effects near the top of this composable) purely so Super-
    // Admin's Municipality dropdown has a Province to scope against; it was
    // never meant to be a real narrowing filter, since [rows] itself is
    // already fully congregation-scoped via [rowsFor(effectiveCongregationId)]
    // before this ever runs. Matching it exactly against each record's own
    // [InterestedPerson.province] silently dropped every record whose own
    // `province` field was never populated (an older/partial address, saved
    // before that field existed on this model) even though its
    // `cityMunicipality`/`barangay` were both present and correct — exactly
    // this Bayombong case. Excluded here, the same way [searchByRows] above
    // already excludes it from the Municipality/Barangay *option list*
    // computation, for the identical reason.
    val advancedFilteredRows = remember(rows, advancedFilter, groupMemberIds) {
        applyTerritoryFilter(rows, advancedFilter.copy(province = null), groupMemberIds)
    }
    var searchQuery by remember { mutableStateOf("") }
    var searchByField by remember { mutableStateOf(TerritorySearchByField.ALL) }
    var groupBy by remember { mutableStateOf(TerritoryGroupBy.MUNICIPALITY) }
    val personNames by viewModel.personNames.collectAsStateWithLifecycle()
    // "LIST VIEW MUST BE THE DEFAULT VIEW" — List View is now the initial
    // landing mode (previously Map View); Map View is still reachable via
    // the exact same top-bar toggle, unchanged.
    var viewMode by remember { mutableStateOf(TerritoryViewMode.LIST) }
    // "Full-Screen Map" (spec §20) — hides this Scaffold's own TopAppBar so
    // the map claims that space too; Map Layers/Search/Current Location
    // stay reachable since those all float over the map itself inside
    // [TerritoryLiveMap], never inside the TopAppBar being hidden here.
    var isFullScreen by remember { mutableStateOf(false) }
    // "Make the Map Full Screen... Make the search expand and shrink to view
    // full screen. Make shrink as default" — Map View's own search/filter
    // controls now float on top of the map (see the Map View render block
    // below) instead of pushing it down the screen; shrunk (collapsed) by
    // default so the map itself always starts genuinely full-screen, with
    // only a slim always-usable search strip on top of it.
    var mapControlsExpanded by remember { mutableStateOf(false) }

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
    // "Set 'All' as the default selected option whenever the Map View is
    // opened or initialized" — [MapSearchCategory.ALL] itself, not just its
    // label; see that entry's own doc comment for what it actually shows.
    var mapSearchCategory by remember { mutableStateOf(MapSearchCategory.ALL) }
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

    // ============================================================
    // "Congregation Group Filter, color-coding, territory barrier, and
    // reporting system" — Map View only, orthogonal to [mapSearchCategory]
    // above (works "together with all existing search types", spec §1);
    // always collected regardless of category, since the Group filter,
    // color-coding, and Group Report all need it active at every level, not
    // just under Publisher Territory. [mapGroups] is the dynamic, "however
    // many there are" list itself; [publisherGroupIds] is the only way to
    // resolve *which* Group a given household record belongs to (through
    // its assigned Publisher's own RoleAssignment — an InterestedPerson has
    // no Group field of its own, same reason List View's own Field Service
    // Group filter needs the mirror-image [groupMemberPublisherIds]).
    // ============================================================
    val mapGroups by viewModel.groupsFor(effectiveCongregationId?.let { setOf(it) })
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val publisherGroupIds by viewModel.publisherGroupIds(effectiveCongregationId?.let { setOf(it) })
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    // null means "All Groups" — same convention every other Map View
    // dropdown's own null-selection already uses.
    var mapGroupFilterId by remember { mutableStateOf<String?>(null) }
    // Bug fix (confirmed live: the Group Report showing three different
    // Groups all with the same green dot) — the previous hash-based
    // continuous-hue formula was a *statistical* approximation with no
    // actual guarantee two unrelated Group ids wouldn't land on
    // similar-looking hues, and with as few as 5 Groups that collision was
    // easy to hit in practice. [CURATED_GROUP_PALETTE] instead hand-picks
    // colors that are guaranteed visually distinct from one another (never
    // a formula that *might* produce two greens), assigned by each Group's
    // own creation order — stable and permanent for that Group's lifetime
    // (a newly created Group always appends at the end, so an *existing*
    // Group's own color never moves just because another one was added;
    // deleting a Group can only ever shift the colors of Groups created
    // *after* it, not before — a far smaller, rarer disruption than the
    // guaranteed-collision-prone alternative). [colorForGroupId]'s own
    // hash-based generator is kept only as the overflow fallback once a
    // congregation has more Groups than the curated palette has colors for.
    //
    // "The color must always come from the Group Record assignment and
    // must never be determined randomly or individually for each member" —
    // [Group.color] (set by GroupDialog, editable by an admin at any time)
    // is now the actual source of truth here; the creation-order palette/
    // hash fallback below only fires for a Group saved before that field
    // existed (`color == null`), so old data keeps a stable color until
    // it's next edited and picks up a real assigned one.
    val groupColorById: Map<String, String> = remember(mapGroups) {
        mapGroups.sortedBy { it.createdAt }.mapIndexed { index, group ->
            group.id to (group.color?.takeIf { it.isNotBlank() } ?: CURATED_GROUP_PALETTE.getOrNull(index) ?: colorForGroupId(group.id))
        }.toMap()
    }
    fun TerritoryMapRow.resolvedGroupId(): String? = person.publisherPersonId?.let { publisherGroupIds[it] }
    fun TerritoryMapRow.resolvedGroupColor(): String = resolvedGroupId()?.let { groupColorById[it] } ?: UNASSIGNED_GROUP_COLOR

    // "The map should show the complete territory scope associated with
    // that publisher" — every pipeline record currently assigned to them
    // (Searching/Return Visit/Bible Study all at once, spec's own list),
    // plus (below) their own live-shared and/or recorded home location.
    val mapScopedRows = remember(rows, mapSearchCategory, mapSelectionId) {
        val selection = mapSelectionId
        when (mapSearchCategory) {
            // "When 'All' is selected, the search should search across all
            // available territory-related categories" — every congregation-
            // scoped Interested Person/Return Visit/Bible Study record,
            // completely unfiltered by place or Publisher; [mapDeepSearchQuery]
            // below is what actually narrows this down as the user types.
            MapSearchCategory.ALL -> rows
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
    // selected scope" — [mapScopedRows] above, never the full [rows]. Under
    // [MapSearchCategory.ALL] specifically, "search across all available
    // territory-related categories" means matching every field a record
    // could plausibly be found by — name, address, Municipality, Barangay,
    // status, and assigned Publisher — not just name/address.
    //
    // "Searches for a specific publisher using Search Deeper [under
    // Publisher's Territory]... identify the selected publisher and load all
    // [Bible Study/Interested Person/Return Visit] records associated with
    // that publisher" — [MapSearchCategory.PUBLISHER_TERRITORY]'s own
    // second dropdown already lets a Publisher be picked by hand, but typing
    // their name into "Search Deeper" (with no dropdown selection made) used
    // to search the *household records'* own name/address, never the
    // assigned Publisher's — matching nothing, since a Publisher's name
    // never appears in either field. Matching the assigned Publisher's own
    // name here as well is what actually makes this work: every one of that
    // matched Publisher's own Bible Study/Interested Person/Return Visit
    // records surfaces (regardless of pipeline stage), and — since
    // [scopeBoundaryPoints] below is built straight from this same result —
    // the barrier automatically hugs exactly that territory and nothing else.
    //
    // Every other category already narrows [mapScopedRows] to its own kind
    // of record, so plain name/address matching stays exactly as it was.
    val mapDeepSearchedRows = remember(mapScopedRows, mapDeepSearchQuery, mapSearchCategory, personNames, mapGroups, publisherGroupIds) {
        val query = mapDeepSearchQuery.trim()
        when {
            query.isEmpty() -> mapScopedRows
            mapSearchCategory == MapSearchCategory.ALL -> mapScopedRows.filter { row ->
                row.person.name.contains(query, ignoreCase = true) ||
                    row.person.address.contains(query, ignoreCase = true) ||
                    row.person.cityMunicipality?.contains(query, ignoreCase = true) == true ||
                    row.person.barangay?.contains(query, ignoreCase = true) == true ||
                    row.person.pipelineStage.statusLabel().contains(query, ignoreCase = true) ||
                    row.person.publisherPersonId?.let { personNames[it] }?.contains(query, ignoreCase = true) == true ||
                    // "Include Group name or number in Search All" — the
                    // Congregation Group this record's own assigned
                    // Publisher belongs to (resolved the same way every
                    // other Group lookup on this map already does — through
                    // [publisherGroupIds], since a record has no Group field
                    // of its own); typing "Group 5" or just "5" both match
                    // since the name itself ("Group 5") already contains the
                    // number.
                    row.person.publisherPersonId?.let { publisherGroupIds[it] }
                        ?.let { groupId -> mapGroups.firstOrNull { it.id == groupId }?.name }
                        ?.contains(query, ignoreCase = true) == true
            }
            // Matches the assigned Publisher's own name only, deliberately
            // never the household record's — "do not display unrelated...
            // records... outside the publisher's assigned scope" means a
            // query that happens to also match some unrelated household's
            // own name must never let that household leak into a result
            // that's supposed to be scoped to one specific Publisher.
            mapSearchCategory == MapSearchCategory.PUBLISHER_TERRITORY -> mapScopedRows.filter { row ->
                row.person.publisherPersonId?.let { personNames[it] }?.contains(query, ignoreCase = true) == true
            }
            else -> mapScopedRows.filter { it.person.name.contains(query, ignoreCase = true) || it.person.address.contains(query, ignoreCase = true) }
        }
    }
    // "The [Congregation Group] filter must work together with all existing
    // search types" — applied as its own, independent narrowing step after
    // category + deep search, exactly the same way [mapSearchCategory] and
    // [mapDeepSearchQuery] already stack; `null` means "All Groups" (every
    // record in [mapDeepSearchedRows], unassigned ones included) same as
    // every other Map View selection's own null-means-All convention.
    val mapGroupFilteredRows = remember(mapDeepSearchedRows, mapGroupFilterId, publisherGroupIds) {
        val groupId = mapGroupFilterId
        if (groupId == null) mapDeepSearchedRows else mapDeepSearchedRows.filter { it.resolvedGroupId() == groupId }
    }
    // "Under Publisher's Territory, do not include the last known location
    // of that Publisher — only the record of his Searched [Interested
    // Person], Return Visit, and Bible Study [records], based on the
    // filters provided." — Publisher Territory's own marker set used to
    // additionally plot the Publisher's live "Share Location while
    // Preaching" position and/or their own recorded home address; both
    // removed. [mapScopedRows]'s own `PUBLISHER_TERRITORY` branch already
    // filters purely by `publisherPersonId` — every pipeline record actually
    // assigned to them, regardless of stage — which is exactly, and only,
    // what should ever show up here.
    val mapPublisherRows: List<TerritoryPublisherRow> = emptyList()

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
    val filtered = remember(advancedFilteredRows, searchQuery, searchByField, personNames, mapGroups, publisherGroupIds) {
        val query = searchQuery.trim()
        advancedFilteredRows
            .filter { row ->
                val groupName = row.person.publisherPersonId?.let { publisherGroupIds[it] }
                    ?.let { groupId -> mapGroups.firstOrNull { it.id == groupId }?.name }
                row.matchesSearch(searchByField, query, row.person.publisherPersonId?.let { personNames[it] }, groupName)
            }
            .sortedBy { it.person.name }
    }

    // Map View's markers are now driven entirely by the cascading search
    // above plus the Congregation Group filter ([mapGroupFilteredRows]),
    // never by List View's own [filtered]/[directoryStatus] — the two
    // views' search models are independent (see that block's own doc
    // comment). [mapPublisherRows] (declared above) stays empty for every
    // category, Publisher Territory included — see its own doc comment for
    // why.
    val mapRows = mapGroupFilteredRows
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
                // Unreachable in practice — [ALL] has no second dropdown, so
                // [mapSelectionId] never leaves null for it (see the `if`
                // above); kept only so this `when` stays exhaustive.
                MapSearchCategory.ALL -> mapSearchCategory.allLabel
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
    // "In Search All, it must search all Municipalities, Barangay..." — the
    // deep-search query under [MapSearchCategory.ALL] already matches a
    // record's own cityMunicipality/barangay text (see [mapDeepSearchedRows]
    // below), but that alone never triggered the real boundary+zoom a
    // deliberate Municipality/Barangay *category* search gets — typing
    // "Solano" under "All" found the right records but never drew Solano's
    // own real polygon or centered on it. These recognize when the typed
    // query itself names a real Municipality/Barangay (not just some
    // record's own field happening to contain it) so that case gets the
    // exact same treatment as manually switching to that category and
    // picking it — Barangay checked first since a name match there is more
    // specific than a same-named Municipality.
    val impliedBarangay: Pair<String, String>? = remember(mapSearchCategory, mapDeepSearchQuery, mapBarangayDirectory) {
        if (mapSearchCategory != MapSearchCategory.ALL) return@remember null
        val q = mapDeepSearchQuery.trim()
        if (q.isEmpty()) null else mapBarangayDirectory.firstOrNull { (_, brgy) -> brgy.equals(q, ignoreCase = true) }
    }
    val impliedMunicipality: String? = remember(mapSearchCategory, mapDeepSearchQuery, municipalityOptions, impliedBarangay) {
        if (mapSearchCategory != MapSearchCategory.ALL || impliedBarangay != null) return@remember null
        val q = mapDeepSearchQuery.trim()
        if (q.isEmpty()) null else municipalityOptions.firstOrNull { it.equals(q, ignoreCase = true) }
    }
    val selectedAreaQuery: Pair<String, Float>? = remember(effectiveProvince, mapSearchCategory, mapSelectionId, impliedMunicipality, impliedBarangay) {
        val selection = mapSelectionId
        when {
            mapSearchCategory == MapSearchCategory.BARANGAY && selection != null -> {
                val muni = selection.substringBefore(BARANGAY_SELECTION_SEPARATOR)
                val brgy = selection.substringAfter(BARANGAY_SELECTION_SEPARATOR)
                "$brgy, $muni, ${effectiveProvince ?: ""}, Philippines" to 15f
            }
            mapSearchCategory == MapSearchCategory.MUNICIPALITY && selection != null ->
                "$selection, ${effectiveProvince ?: ""}, Philippines" to 12.5f
            impliedBarangay != null ->
                "${impliedBarangay.second}, ${impliedBarangay.first}, ${effectiveProvince ?: ""}, Philippines" to 15f
            impliedMunicipality != null -> "$impliedMunicipality, ${effectiveProvince ?: ""}, Philippines" to 12.5f
            else -> null
        }
    }
    // "The Municipality barrier must be shown always" — true only when the
    // user actually picked a specific Municipality/Barangay via search
    // (spec's own "zoom closer to the selected area" deliberate-focus
    // action) — including now the implied "All"-category name match above,
    // which is just as deliberate a "focus on this area" as switching
    // categories by hand; the *implicit* single-Municipality fallback
    // [selectedAreaNames] falls back to below must never force the camera
    // to jump there the same way, or every map open would zoom straight to
    // Municipality level instead of staying Province-wide.
    val explicitAreaSelection: Boolean =
        (mapSearchCategory == MapSearchCategory.MUNICIPALITY || mapSearchCategory == MapSearchCategory.BARANGAY) && mapSelectionId != null ||
            impliedMunicipality != null || impliedBarangay != null
    // Raw (un-templated) Municipality/Barangay names for the real-boundary
    // lookup ([TerritoryBoundaryRepository] keys by exact name, not a
    // geocoder query string) — same gating as [selectedAreaQuery] above for
    // an explicit selection. Bug fix (explicit instruction, confirmed live:
    // browsing records under "All"/a record-type category — never a
    // Municipality/Barangay search — showed no Municipality line barrier at
    // all) — "the line barrier should always be displayed regardless of the
    // search level" was already this file's own stated intent (see the doc
    // comment a few lines down) but was never actually implemented for
    // every other category; nothing explicitly selected now falls back to
    // whichever single Municipality every currently-visible record shares
    // (the common case for one congregation/territory — real, ambiguous
    // multi-Municipality spans still show none rather than guessing which
    // one to pick).
    val selectedAreaNames: Pair<String, String?>? = remember(mapSearchCategory, mapSelectionId, mapGroupFilteredRows, impliedMunicipality, impliedBarangay) {
        val selection = mapSelectionId
        when {
            mapSearchCategory == MapSearchCategory.MUNICIPALITY && selection != null -> selection to null
            mapSearchCategory == MapSearchCategory.BARANGAY && selection != null ->
                selection.substringBefore(BARANGAY_SELECTION_SEPARATOR) to selection.substringAfter(BARANGAY_SELECTION_SEPARATOR)
            impliedBarangay != null -> impliedBarangay.first to impliedBarangay.second
            impliedMunicipality != null -> impliedMunicipality to null
            else -> mapGroupFilteredRows.mapNotNull { it.person.cityMunicipality }.distinct().singleOrNull()?.let { it to null }
        }
    }
    // "The line barrier should always be displayed regardless of the search
    // level" — Municipality/Barangay get the real administrative polygon
    // when one specific place is selected and this app has bundled data for
    // it (see [selectedAreaNames]/[TerritoryBoundaryRepository] above,
    // unchanged, drawn as its own separate overlay); every case — including
    // that one — additionally gets a best-effort "scope boundary" per
    // Congregation Group, hugging whatever's actually visible for that Group
    // right now (see [TerritoryLiveMap]'s own `window.setGroupScopeBoundaries`).
    // "Create a separate barrier line for every Congregation Group... do not
    // merge different groups into one barrier" — grouped by
    // [TerritoryMapRow.resolvedGroupId] (an "Unassigned" bucket for a record
    // whose Publisher has no Group, keyed `null`, same as every other
    // grouping in this file), never a single flat point list the way this
    // used to work before Congregation Groups existed. Built from
    // [mapGroupFilteredRows] (post Congregation-Group-filter, so picking one
    // specific Group here also means only *that* Group's barrier is drawn —
    // spec §1's own "show only... records belonging to that group") — never
    // [mapPublisherRows] (see that val's own doc comment for why a
    // Publisher's own last-known/live location must never skew any barrier).
    // "Show group names in every territory map" — [MapGroupBoundary.name]
    // is drawn as a label at the center of that Group's own territory
    // shape (see [TerritoryLiveMap]'s own `setGroupScopeBoundaries`), same
    // real Group name the filter dropdown/Group Report already show,
    // "Unassigned" for the one pseudo-group covering a record whose
    // Publisher has no Group at all.
    val mapGroupBoundaries: List<MapGroupBoundary> = remember(mapGroupFilteredRows, groupColorById, mapGroups) {
        mapGroupFilteredRows
            .mapNotNull { row ->
                val lat = row.person.gpsLat
                val lng = row.person.gpsLng
                if (lat == null || lng == null) return@mapNotNull null
                Triple(row.resolvedGroupId(), row.resolvedGroupColor(), LatLng(lat, lng, null))
            }
            .groupBy({ it.first }, { it.second to it.third })
            .map { (groupId, entries) ->
                val name = if (groupId == null) "Unassigned" else mapGroups.firstOrNull { it.id == groupId }?.name ?: "Unassigned"
                MapGroupBoundary(id = groupId ?: UNASSIGNED_GROUP_ID, name = name, color = entries.first().first, points = entries.map { it.second })
            }
    }
    // Bug fix (explicit instruction, confirmed live: two Return Visit
    // records from the same Group — Kit Gamboa and Kenneth Fernandez —
    // showed a Group territory covering the *entire* Barangay they happen
    // to live in, when it must show only the area actually between them) —
    // this used to union the real bundled polygon of every Barangay a
    // Group's own records fell in ("Each Field Service Group is assigned
    // specific whole Barangays; the group's territory is the union of
    // those Barangays' real boundaries"), which is correct only if Groups
    // really do own whole Barangays; this app has no such explicit
    // assignment, so that was actually derived from wherever a Group's
    // members *happened* to live — nowhere near a real per-Barangay
    // assignment, and it made a 2-record Group's own territory look
    // exactly like a much bigger Group's. [mapGroupBoundaries]'s own
    // point-hull shape (a Group's Territory Scope is the convex hull of
    // its own actual records — see [TerritoryLiveMap]'s own
    // `buildGroupBoundaryLayer`) is the only shape drawn now, always
    // proportional to the Group's own real records, never a whole
    // administrative unit.
    // Only null at the screen's absolute default (now "All", per spec —
    // previously Municipalities → All) — see this card's own render site
    // for why.
    val noRecordsAreaLabel: String? = remember(mapSearchCategory, mapSelectionId, mapSelectionLabel) {
        if (mapSearchCategory == MapSearchCategory.ALL) null
        else "${mapSearchCategory.label}: $mapSelectionLabel"
    }

    // "Group Report... automatically summarize the records according to the
    // current search/filter criteria... update automatically whenever the
    // user changes Search type/Municipality/Barangay/Congregation
    // Group/Publisher/Search Deeper... When All Groups is selected, display
    // a row for every group. When a specific group is selected, display the
    // report for that group." — deliberately built from [mapDeepSearchedRows]
    // (category + deep search applied, *before* the Congregation Group
    // filter itself), not [mapGroupFilteredRows`, so a full per-Group
    // breakdown is always available to slice by [mapGroupFilterId] here —
    // exactly the same underlying dataset the map's own markers/barriers use
    // (spec §9's own "map and report must use the same filtered dataset"),
    // just grouped differently. An "Unassigned" row (a record whose own
    // assigned Publisher has no Congregation Group) is appended only when
    // "All Groups" is selected and at least one such record actually exists
    // in scope — every displayed marker must be accounted for somewhere
    // (spec §9), but a real Group's own report should never need to care
    // about the possibility once one specific Group is what was asked for.
    val groupReportRows: List<GroupReportRow> = remember(mapDeepSearchedRows, mapGroups, publisherGroupIds, mapGroupFilterId, groupColorById) {
        fun rowsFor(groupId: String?) = mapDeepSearchedRows.filter { it.resolvedGroupId() == groupId }
        fun reportRow(id: String, name: String, color: String, rowsInGroup: List<TerritoryMapRow>) = GroupReportRow(
            groupId = id,
            groupName = name,
            color = color,
            publishers = rowsInGroup.mapNotNull { it.person.publisherPersonId.takeIf { pid -> pid.isNotBlank() } }.distinct().size,
            interestedPersons = rowsInGroup.count { it.person.pipelineStage == PipelineStage.SEARCHING },
            returnVisits = rowsInGroup.count { it.person.pipelineStage == PipelineStage.RETURN_VISIT },
            bibleStudies = rowsInGroup.count { it.person.pipelineStage == PipelineStage.BIBLE_STUDY },
        )
        val filterId = mapGroupFilterId
        if (filterId != null) {
            val group = mapGroups.firstOrNull { it.id == filterId }
            if (group == null) emptyList()
            else listOf(reportRow(group.id, group.name, groupColorById[group.id] ?: UNASSIGNED_GROUP_COLOR, rowsFor(group.id)))
        } else {
            val groupRows = mapGroups.map { group -> reportRow(group.id, group.name, groupColorById[group.id] ?: UNASSIGNED_GROUP_COLOR, rowsFor(group.id)) }
            val unassigned = rowsFor(null)
            if (unassigned.isEmpty()) groupRows
            else groupRows + reportRow(UNASSIGNED_GROUP_ID, "Unassigned", UNASSIGNED_GROUP_COLOR, unassigned)
        }
    }

    // "Switching between List View and Map View must preserve the current
    // search/filter criteria. Search results must be consistent between
    // List View and Map View" — the two views keep their own independent
    // search controls (a geography-grouped directory vs. a category cascade
    // don't map onto each other cleanly enough to share one control — see
    // [MapSearchCategory]'s own doc comment), but switching now translates
    // whatever's currently active into the other view's own terms, both
    // directions, rather than silently dropping it.
    fun toggleViewMode() {
        if (viewMode == TerritoryViewMode.LIST) {
            if (searchQuery.isNotBlank()) mapDeepSearchQuery = searchQuery
            when {
                advancedFilter.cityMunicipality != null && advancedFilter.barangay != null -> {
                    mapSearchCategory = MapSearchCategory.BARANGAY
                    mapSelectionId = "${advancedFilter.cityMunicipality}$BARANGAY_SELECTION_SEPARATOR${advancedFilter.barangay}"
                }
                advancedFilter.cityMunicipality != null -> {
                    mapSearchCategory = MapSearchCategory.MUNICIPALITY
                    mapSelectionId = advancedFilter.cityMunicipality
                }
            }
            viewMode = TerritoryViewMode.MAP
        } else {
            if (mapDeepSearchQuery.isNotBlank()) searchQuery = mapDeepSearchQuery
            when (mapSearchCategory) {
                MapSearchCategory.MUNICIPALITY -> mapSelectionId?.let {
                    advancedFilter = advancedFilter.copy(cityMunicipality = it, barangay = null)
                }
                MapSearchCategory.BARANGAY -> mapSelectionId?.let { selection ->
                    advancedFilter = advancedFilter.copy(
                        cityMunicipality = selection.substringBefore(BARANGAY_SELECTION_SEPARATOR),
                        barangay = selection.substringAfter(BARANGAY_SELECTION_SEPARATOR),
                    )
                }
                else -> {}
            }
            viewMode = TerritoryViewMode.LIST
        }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = { Text("Territory Map") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = ::toggleViewMode) {
                            Icon(
                                if (viewMode == TerritoryViewMode.MAP) Icons.Rounded.ViewList else Icons.Rounded.Map,
                                contentDescription = if (viewMode == TerritoryViewMode.MAP) "Switch to List View" else "Switch to Map View",
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (viewMode != TerritoryViewMode.MAP) {
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
                // "Make the Map Full Screen... Remove unnecessary margins,
                // empty spaces, or panels that reduce the map viewing area"
                // — the map itself now fills this entire Box (same as the
                // TopAppBar-only budget every other full-screen map in this
                // app gets), with the search/filter controls floating on top
                // of it instead of pushing it down; collapsed by default
                // (see [mapControlsExpanded]'s own doc comment) so the map
                // starts genuinely full-screen with only a slim strip
                // covering it.
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    TerritoryLiveMap(
                        rows = mapRows,
                        publisherRows = mapPublisherRows,
                        canSeePublisherLocations = canSeePublisherLocations,
                        getCurrentLocation = viewModel::currentLocation,
                        hasLocationPermission = viewModel::hasLocationPermission,
                        geocodeArea = viewModel::geocodeArea,
                        boundaryGeometry = viewModel::boundaryGeometry,
                        nearbyStreetPoints = viewModel::nearbyStreetPoints,
                        selectedAreaQuery = selectedAreaQuery,
                        selectedAreaNames = selectedAreaNames,
                        explicitAreaSelection = explicitAreaSelection,
                        province = effectiveProvince,
                        groupBoundaries = mapGroupBoundaries,
                        groupColorFor = { it.resolvedGroupColor() },
                        groupNameFor = { row -> row.resolvedGroupId()?.let { id -> mapGroups.firstOrNull { g -> g.id == id }?.name } },
                        groupIdFor = { it.resolvedGroupId() },
                        municipalityBoundaries = viewModel::provinceMunicipalityBoundaries,
                        isFullScreen = isFullScreen,
                        onToggleFullScreen = { isFullScreen = !isFullScreen },
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

                    Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                        Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)) {
                            if (mapControlsExpanded) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        IconButton(onClick = { mapControlsExpanded = false }) {
                                            Icon(Icons.Rounded.ExpandLess, contentDescription = "Shrink search")
                                        }
                                    }
                                    // "TERRITORY MAP → MAP VIEW SEARCH SIMPLIFICATION" —
                                    // Map View's own cascading Search Category →
                                    // Specific Record/All → Deeper Text Search bar,
                                    // unchanged (see its own doc comment); only
                                    // *where* it renders (floating over the map,
                                    // collapsible) changed here.
                                    TerritoryMapSearchBar(
                                        category = mapSearchCategory,
                                        onCategoryChange = ::onMapCategoryChange,
                                        selectionId = mapSelectionId,
                                        onSelectionChange = ::onMapSelectionChange,
                                        selectionOptions = when (mapSearchCategory) {
                                            // No second dropdown for [ALL] (see its own doc comment) — [TerritoryMapSearchBar] skips rendering it entirely when this is empty and `selectLabel` is blank.
                                            MapSearchCategory.ALL -> emptyList()
                                            MapSearchCategory.MUNICIPALITY -> municipalityOptions.map { it to it }
                                            MapSearchCategory.BARANGAY -> mapBarangayDirectory.map { (muni, brgy) -> "$muni$BARANGAY_SELECTION_SEPARATOR$brgy" to "$brgy, $muni" }
                                            MapSearchCategory.INTERESTED_PERSON, MapSearchCategory.RETURN_VISIT, MapSearchCategory.BIBLE_STUDY -> mapStageOptions
                                            MapSearchCategory.PUBLISHER_TERRITORY -> mapPublisherPersons.map { it.id to it.fullName }
                                        },
                                        groups = mapGroups,
                                        groupFilterId = mapGroupFilterId,
                                        onGroupFilterChange = { mapGroupFilterId = it },
                                        groupColorFor = { id -> groupColorById[id] ?: UNASSIGNED_GROUP_COLOR },
                                        deepSearchQuery = mapDeepSearchQuery,
                                        onDeepSearchQueryChange = { mapDeepSearchQuery = it },
                                        resultCount = mapGroupFilteredRows.size,
                                    )
                                }
                            } else {
                                // Shrunk (default) — a slim, always-usable strip:
                                // the same deep-search text field (spec's own
                                // "keep the essential search... controls
                                // accessible without covering important map
                                // information") plus a chevron to expand back to
                                // the full category/selection cascade.
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        value = mapDeepSearchQuery,
                                        onValueChange = { mapDeepSearchQuery = it },
                                        singleLine = true,
                                        placeholder = { Text("Search ${mapSearchCategory.label}…") },
                                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                                        trailingIcon = {
                                            if (mapDeepSearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { mapDeepSearchQuery = "" }) {
                                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { mapControlsExpanded = true }) {
                                        Icon(Icons.Rounded.ExpandMore, contentDescription = "Expand search")
                                    }
                                }
                            }
                        }

                        // "AREA/SCOPE INFORMATION PANEL" — only shown expanded,
                        // same as the full search cascade above, so the shrunk
                        // strip never covers more of the map than a single
                        // search row's worth.
                        if (mapControlsExpanded) {
                            val bsCount = mapGroupFilteredRows.count { it.person.pipelineStage == PipelineStage.BIBLE_STUDY }
                            val rvCount = mapGroupFilteredRows.count { it.person.pipelineStage == PipelineStage.RETURN_VISIT }
                            val ipCount = mapGroupFilteredRows.count { it.person.pipelineStage == PipelineStage.SEARCHING }
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
                            // "Group Report" — hidden entirely for a
                            // congregation with no Congregation Groups at
                            // all yet, same convention the Group filter
                            // dropdown above already follows.
                            if (mapGroups.isNotEmpty()) {
                                GroupReportTable(rows = groupReportRows)
                            }
                        }
                    }
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
                    // Bug fix (confirmed live: an Admin/Elder session's List
                    // View showing far fewer records than a Publisher's own,
                    // for the exact same congregation): a record whose own
                    // Municipality/Barangay IS set, but doesn't exactly match
                    // any entry in [municipalityOptions]/[barangayDirectory]
                    // (a Province-resolution mismatch for that particular
                    // session, a saved name that doesn't line up with the
                    // bundled PSGC table, ...) used to fall through *both*
                    // [groups] (no matching entry) and [unassigned] (it isn't
                    // actually missing a Municipality/Barangay) — vanishing
                    // from the directory entirely despite still being fully
                    // permitted, congregation-scoped data. Grouped here under
                    // its own real, saved location name instead, so a record
                    // is never hidden just because the master PSGC list
                    // didn't happen to resolve for this session.
                    val coveredKeys = groups.mapTo(mutableSetOf()) { it.key }
                    val orphaned = when (groupBy) {
                        TerritoryGroupBy.MUNICIPALITY -> filtered
                            .filter { it.person.cityMunicipality != null && it.person.cityMunicipality !in coveredKeys }
                            .groupBy { it.person.cityMunicipality!! }
                            .map { (muni, rows) -> DirectoryGroup(key = muni, label = muni, rows = rows) }
                        TerritoryGroupBy.BARANGAY -> filtered
                            .filter { it.person.cityMunicipality != null && it.person.barangay != null && "${it.person.cityMunicipality}|${it.person.barangay}" !in coveredKeys }
                            .groupBy { "${it.person.cityMunicipality}|${it.person.barangay}" }
                            .map { (key, rows) -> DirectoryGroup(key = key, label = "${rows.first().person.barangay}, ${rows.first().person.cityMunicipality}", rows = rows) }
                    }
                    (groups + orphaned + (if (unassigned.isEmpty()) emptyList() else listOf(DirectoryGroup("—unassigned—", "Unassigned", unassigned))))
                        .sortedBy { if (it.key == "—unassigned—") "￿" else it.label }
                }

                // "TERRITORY MAP – FINAL GEOGRAPHICAL AND LIST VIEW FIX" §10-13
                // — bug fix: [directoryGroups] itself must keep listing every
                // real PSGC Municipality/Barangay unconditionally (that's the
                // correct "master location data controls the displayed list"
                // behavior for a *blank* search — see its own doc comment
                // above), but rendering it as-is straight through an active
                // search meant a one-person name search still showed every
                // *other* Municipality/Barangay in the province at 0 matches.
                // This is the only place that distinction needs to exist:
                // hide a zero-row group, but only while [searchQuery] is
                // actually non-blank — clearing the search box immediately
                // reverts to the unfiltered [directoryGroups] with nothing
                // else to recompute, since [directoryGroups] itself was never
                // touched.
                val visibleDirectoryGroups = if (searchQuery.isBlank()) directoryGroups else directoryGroups.filter { it.rows.isNotEmpty() }

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

                    if (visibleDirectoryGroups.isEmpty()) {
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
                            items(visibleDirectoryGroups, key = { it.key }) { group ->
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
    // "Congregation Group Filter... must work together with all existing
    // search types" — a fourth, independent control, always shown
    // regardless of [category] (unlike [selectionOptions], which only
    // applies to some categories); [groups] is however many Congregation
    // Groups actually exist (spec §1's own "dynamic... may be fewer or more
    // than five"), [groupColorFor] resolves each one's own dot color for the
    // dropdown (see [colorForGroupId]'s own doc comment on why that color is
    // stable per Group id, never a shifting index-based one).
    groups: List<Group>,
    groupFilterId: String?,
    onGroupFilterChange: (String?) -> Unit,
    groupColorFor: (String) -> String,
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
            // "Add a 'Congregation Group' filter... include an 'All Groups'
            // option" — hidden entirely when a congregation has no Groups at
            // all yet, same "nothing to show" convention every other Map
            // View control already follows rather than a dropdown with only
            // one, meaningless "All Groups" entry.
            if (groups.isNotEmpty()) {
                var groupExpanded by remember { mutableStateOf(false) }
                val selectedGroup = groups.firstOrNull { it.id == groupFilterId }
                androidx.compose.material3.ExposedDropdownMenuBox(expanded = groupExpanded, onExpandedChange = { groupExpanded = it }) {
                    OutlinedTextField(
                        value = selectedGroup?.name ?: "All Groups",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Congregation Group") },
                        leadingIcon = {
                            Box(
                                modifier = Modifier.padding(start = 4.dp).size(14.dp)
                                    .clip(CircleShape)
                                    .background(selectedGroup?.let { parseHexColor(groupColorFor(it.id)) } ?: Color(0xFFBDBDBD)),
                            )
                        },
                        trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("All Groups") },
                            onClick = { onGroupFilterChange(null); groupExpanded = false },
                        )
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(parseHexColor(groupColorFor(group.id))))
                                        Text(group.name)
                                    }
                                },
                                onClick = { onGroupFilterChange(group.id); groupExpanded = false },
                            )
                        }
                    }
                }
            }
            // "After selecting the first option, display a second dropdown
            // containing the applicable records" — [selectionOptions] is
            // already whichever list applies to [category] (built by
            // [TerritoryMapScreen] itself); "All" is always this dropdown's
            // own leading option (see [FilterDropdownField]'s own doc
            // comment), never a separate control. [MapSearchCategory.ALL]
            // itself is the one exception — a blank [selectLabel] means
            // there's nothing narrower than "every category" to pick, so the
            // second dropdown is skipped entirely rather than shown empty.
            if (category.selectLabel.isNotBlank()) {
                FilterDropdownField(
                    label = category.selectLabel,
                    options = selectionOptions,
                    selectedId = selectionId,
                    onSelected = onSelectionChange,
                    allLabel = category.allLabel,
                )
            }
            // "The third field (search textbox) should perform a deeper
            // search within the currently selected scope" — restricted to
            // [selectionOptions]' own current scope by [TerritoryMapScreen]'s
            // own `mapDeepSearchedRows`, never the full dataset.
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            OutlinedTextField(
                value = deepSearchQuery,
                onValueChange = onDeepSearchQueryChange,
                label = { Text("Search deeper") },
                singleLine = true,
                // "Include a proper Search button so the user can explicitly
                // execute the search" — search is already live as the user
                // types (every keystroke re-runs the same full-dataset
                // search this button would trigger — see [TerritoryMapScreen]'s
                // own `mapDeepSearchedRows`), so this is a real, always-
                // present control that confirms/dismisses the keyboard,
                // exactly the same "Search" convention [TerritoryPersistentFilterBar]'s
                // own List View search box already uses.
                leadingIcon = {
                    IconButton(onClick = { focusManager.clearFocus() }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                },
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

/** "Group Report" — a simple, compact summary table: one row per
 * Congregation Group (or just the one selected — see [TerritoryMapScreen]'s
 * own `groupReportRows` doc comment), Publishers/Interested Persons/Return
 * Visits/Bible Studies counted within the current search scope, spec's own
 * exact worked column layout. Each row's own leading color dot/name text
 * uses that Group's own assigned color (spec §8's own "use each group's
 * assigned color for its group name, indicator, or report row") — the exact
 * same color its markers/barrier already use, so the report and the map
 * visually agree about which color means which Group. Scrolls horizontally
 * on a narrow phone screen rather than clipping a column, same
 * responsive-table convention this app's other data tables already use. */
@Composable
private fun GroupReportTable(rows: List<GroupReportRow>) {
    if (rows.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Group Report", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    // Header row.
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("Congregation Group", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 160.dp))
                        Text("Publishers", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 90.dp))
                        Text("Interested", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 90.dp))
                        Text("Return Visits", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 100.dp))
                        Text("Bible Studies", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 100.dp))
                    }
                    rows.forEach { row ->
                        Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(min = 160.dp)) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(parseHexColor(row.color)))
                                Text(row.groupName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
                            }
                            Text("${row.publishers}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(min = 90.dp))
                            Text("${row.interestedPersons}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(min = 90.dp))
                            Text("${row.returnVisits}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(min = 100.dp))
                            Text("${row.bibleStudies}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(min = 100.dp))
                        }
                    }
                }
            }
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
    // "Snap the small [Group Territory] shape to real streets/blocks where
    // possible" — see [OverpassStreetRepository]'s own doc comment; `null`
    // (offline, timeout, rate-limited) means the caller falls back to that
    // Group's own points-only shape, same "best-effort, never blocking"
    // convention [geocodeArea]/[boundaryGeometry] already use.
    nearbyStreetPoints: suspend (Double, Double, Double) -> List<Pair<Double, Double>>?,
    selectedAreaQuery: Pair<String, Float>?,
    selectedAreaNames: Pair<String, String?>?,
    // False for [selectedAreaNames]' own implicit single-Municipality
    // fallback — the Municipality barrier still draws, but must never force
    // the camera to jump there the way a deliberate search selection does
    // (see [TerritoryMapScreen]'s own `explicitAreaSelection` doc comment).
    explicitAreaSelection: Boolean,
    // The scoped role/Super-Admin's own effective Province — needed only
    // for the "Territory Map Loading — Province Wide Only" initial-focus
    // geocode query below.
    province: String?,
    // "Create a separate barrier line for every Congregation Group... do not
    // merge different groups into one barrier" — one entry per Group
    // currently represented among [rows] (plus an "Unassigned" entry when
    // applicable), each with its own color and its own point set; drawn via
    // `window.setGroupScopeBoundaries` alongside — never instead of — the
    // real Municipality/Barangay administrative polygon [selectedAreaNames]
    // resolves, when it does.
    groupBoundaries: List<MapGroupBoundary>,
    // "Every record displayed on the map must use the color assigned to its
    // Congregation Group" — resolved per-row by the caller (which already
    // owns [publisherGroupIds]/the color-by-id cache), keeping this
    // composable a pure rendering layer with no Group-membership lookup
    // logic of its own, same convention [boundaryGeometry]/[geocodeArea]
    // already use for their own caller-owned lookups.
    groupColorFor: (TerritoryMapRow) -> String,
    groupNameFor: (TerritoryMapRow) -> String?,
    // See [MapPoint.groupId]'s own doc comment — the real Congregation
    // Group id (never the display name), same caller-owned-lookup
    // convention as [groupColorFor]/[groupNameFor] above.
    groupIdFor: (TerritoryMapRow) -> String?,
    // "PROVINCE-WIDE COLOR CODING... the initial map view must clearly show
    // the province divided into its different municipalities" — every real
    // Municipality name + boundary geometry pair in the congregation's own
    // Province (see [TerritoryMapViewModel.provinceMunicipalityBoundaries]),
    // an empty list when the Province isn't known yet or isn't covered by
    // the bundled boundary asset (see [TerritoryBoundaryRepository]'s own
    // doc comment) — same graceful-miss convention [boundaryGeometry]
    // already uses.
    municipalityBoundaries: suspend (String) -> List<Pair<String, String>>,
    // "Full-Screen Map" (spec §20) — owned by the parent (it hides that
    // Scaffold's own TopAppBar), just read/toggled here so the Full Screen
    // control can live among this composable's own floating map controls.
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
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
    val pipelinePoints = remember(rows, publisherNames, groupColorFor, groupNameFor) {
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
                    groupColor = groupColorFor(row),
                    groupName = groupNameFor(row),
                    groupId = groupIdFor(row),
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
    // "Selecting a Territory Group... Expand House Holders Inside the
    // Selected Territory" — set from `AndroidBridge.showGroupDetails` (see
    // the WebView factory below) when a Group's own territory fill is
    // tapped; drives both the info panel below and which House Holders
    // actually get pushed to the map (see [visiblePoints]) — the Group
    // territory shapes themselves are unaffected (every Group's barrier
    // stays visible, per spec's own "keep the other territories visible in
    // the background").
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    // Bug fix (confirmed live: with a Publisher subdivision now rendered
    // *underneath* the Group's own info sheet — spec's own "tap a
    // Publisher's territory subdivision" — there was no way to actually
    // reach it: Compose's `ModalBottomSheet` puts a full-screen scrim
    // behind itself that intercepts every tap, including ones that
    // visually land on the sliver of map still showing above the sheet, so
    // any second tap just dismissed the sheet instead of reaching the
    // Leaflet layer underneath — and dismissing used to clear
    // [selectedGroupId] itself, erasing the Publisher subdivisions too).
    // This tracks "the sheet's own UI is closed" separately from "no Group
    // is selected" — swiping/tapping the sheet away now only hides that
    // UI, leaving the Group selection (and its Publisher subdivisions)
    // fully intact and tappable; only the sheet's own explicit "Show All
    // Territories" button actually clears [selectedGroupId].
    var groupInfoSheetDismissed by remember { mutableStateOf(false) }
    // "Territory Group Information" (spec §11) — Group Name, Publishers,
    // Interested Persons, Bible Studies, reusing the exact same
    // [GroupReportRow] shape/counting logic [TerritoryMapScreen]'s own
    // Group Report table already computes (spec §9's "map and report must
    // use the same filtered dataset"), just for one Group instead of every
    // Group at once. [rows] here is already Congregation/Publisher/Status-
    // filtered by the caller — same dataset [points] itself came from —
    // never re-derived from anything wider.
    val selectedGroupInfo = remember(selectedGroupId, rows, groupIdFor, groupBoundaries, publisherNames) {
        val groupId = selectedGroupId ?: return@remember null
        val boundary = groupBoundaries.firstOrNull { it.id == groupId }
        val groupRows = rows.filter { groupIdFor(it) == groupId }
        val publisherIds = groupRows.mapNotNull { it.person.publisherPersonId.takeIf { pid -> pid.isNotBlank() } }.distinct()
        GroupReportRow(
            groupId = groupId,
            groupName = boundary?.name ?: "Unassigned",
            color = boundary?.color ?: UNASSIGNED_GROUP_COLOR,
            publishers = publisherIds.size,
            interestedPersons = groupRows.count { it.person.pipelineStage == PipelineStage.SEARCHING },
            returnVisits = groupRows.count { it.person.pipelineStage == PipelineStage.RETURN_VISIT },
            bibleStudies = groupRows.count { it.person.pipelineStage == PipelineStage.BIBLE_STUDY },
            // "Show the names of the group publishers if the Territory
            // Group is clicked" — every distinct assigned Publisher's own
            // full name across this Group's own records (resolved via this
            // composable's own [publisherNames] param, the same id -> name
            // map every other Publisher-name lookup here already uses),
            // sorted so the list reads consistently rather than in
            // arbitrary record order.
            publisherNames = publisherIds.mapNotNull { id -> publisherNames[id] }.sorted(),
            municipalities = groupRows.mapNotNull { it.person.cityMunicipality?.takeIf { m -> m.isNotBlank() } }.distinct().sorted(),
            barangays = groupRows.mapNotNull { it.person.barangay?.takeIf { b -> b.isNotBlank() } }.distinct().sorted(),
            houseHolderCount = groupRows.size,
        )
    }
    // "GROUP TERRITORY & PUBLISHER TERRITORY DIVISION" — once a Group is
    // selected, subdivide its own territory by the Publisher each record
    // is actually assigned to (spec §1's own "this is a map visualization
    // of the existing assignments," never a new database record). Deliberately
    // the exact same point-hull shape convention [MapGroupBoundary] already
    // uses one level up — since every one of a Publisher's own points here
    // is already a subset of the Group's own points the parent hull was
    // built from, the Publisher's own hull is mathematically guaranteed to
    // stay inside the Group's own hull (a convex hull of any subset of
    // points can never extend outside the convex hull of the full set) —
    // spec's own "must remain inside the parent Group Territory" holds
    // automatically, with no separate clipping step needed. Empty (not
    // just unpopulated) whenever no Group is selected, so the JS push
    // effect below can use emptiness alone to decide whether to draw or
    // clear this layer.
    val selectedGroupPublisherBoundaries: List<MapPublisherBoundary> = remember(selectedGroupId, rows, groupIdFor, groupColorFor, publisherNames) {
        val groupId = selectedGroupId ?: return@remember emptyList()
        val groupRows = rows.filter { groupIdFor(it) == groupId }
        // "PUBLISHER TERRITORY – NO DIFFERENT COLORS... All publishers
        // inside the same Group Territory must use the same territory/map
        // appearance" — every Publisher's own [MapPublisherBoundary] now
        // shares the parent Group's own single color (same value every
        // entry gets, never one drawn per Publisher index); Publishers are
        // distinguished only by their own boundary line + name label (see
        // `buildPublisherBoundaryLayer`'s no-fill style), not by a
        // different fill color the way [PUBLISHER_TERRITORY_COLORS] used to
        // assign.
        val groupColor = groupRows.firstOrNull()?.let(groupColorFor) ?: UNASSIGNED_GROUP_COLOR
        groupRows
            .mapNotNull { row ->
                val lat = row.person.gpsLat
                val lng = row.person.gpsLng
                val publisherId = row.person.publisherPersonId.takeIf { it.isNotBlank() }
                if (lat == null || lng == null || publisherId == null) null
                else Triple(publisherId, publisherNames[publisherId] ?: "Unassigned", LatLng(lat, lng, null))
            }
            .groupBy({ it.first }, { it.second to it.third })
            .entries
            .sortedBy { it.key }
            .map { (publisherId, entries) ->
                MapPublisherBoundary(id = publisherId, name = entries.first().first, color = groupColor, points = entries.map { it.second })
            }
    }
    // "Clicking a Publisher Territory" (spec §6) — set from
    // `AndroidBridge.showPublisherDetails`; always cleared the moment the
    // parent Group selection itself changes (a stale Publisher detail
    // panel from a *previous* Group must never survive into a new one).
    var selectedPublisherId by remember { mutableStateOf<String?>(null) }
    // "Publisher Territories must remain hidden by default... Do NOT
    // immediately display the individual Publisher Territories" — a Group
    // tap alone only ever opens the Group's own details now; this is the
    // one thing that actually reveals the Publisher subdivisions (the
    // "SHOW PUBLISHER TERRITORY"/"HIDE THE PUBLISHERS TERRITORY" button in
    // [GroupInfoSheet]), reset to hidden every time the Group selection
    // itself changes so a *new* Group never inherits the previous one's
    // "shown" state.
    var showPublisherTerritories by remember { mutableStateOf(false) }
    LaunchedEffect(selectedGroupId) {
        selectedPublisherId = null
        groupInfoSheetDismissed = false
        showPublisherTerritories = false
    }
    val selectedPublisherInfo = remember(selectedPublisherId, selectedGroupId, rows, groupIdFor, selectedGroupPublisherBoundaries, groupBoundaries) {
        val publisherId = selectedPublisherId ?: return@remember null
        val groupId = selectedGroupId ?: return@remember null
        val boundary = selectedGroupPublisherBoundaries.firstOrNull { it.id == publisherId } ?: return@remember null
        val groupBoundary = groupBoundaries.firstOrNull { it.id == groupId }
        val publisherRows = rows.filter { groupIdFor(it) == groupId && it.person.publisherPersonId == publisherId }
        PublisherTerritoryInfo(
            publisherId = publisherId,
            publisherName = boundary.name,
            color = boundary.color,
            groupName = groupBoundary?.name ?: "Unassigned",
            municipalities = publisherRows.mapNotNull { it.person.cityMunicipality?.takeIf { m -> m.isNotBlank() } }.distinct().sorted(),
            barangays = publisherRows.mapNotNull { it.person.barangay?.takeIf { b -> b.isNotBlank() } }.distinct().sorted(),
            houseHolderCount = publisherRows.size,
            interestedPersons = publisherRows.count { it.person.pipelineStage == PipelineStage.SEARCHING },
            returnVisits = publisherRows.count { it.person.pipelineStage == PipelineStage.RETURN_VISIT },
            bibleStudies = publisherRows.count { it.person.pipelineStage == PipelineStage.BIBLE_STUDY },
        )
    }
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

    // Built once — no [points] dependency at all now (see [buildTerritoryMapHtml]'s
    // own doc comment); reloading the whole page on every search would
    // violate spec's own "do not reload the entire map unnecessarily after
    // every search."
    val html = remember { buildTerritoryMapHtml() }
    // Bumped on a manual "Retry" tap to force a reload of the same [html].
    var reloadToken by remember { mutableIntStateOf(0) }
    var loadState by remember { mutableStateOf(MapLoadState.LOADING) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // "Add debugging checks" — captured on-device (no adb/Logcat access
    // needed to report back what actually happened) rather than only
    // written to Logcat.
    val consoleMessages = remember { mutableStateListOf<String>() }
    var showDiagnostics by remember { mutableStateOf(false) }

    // "OPENSTREETMAP MAP LAYERS AND MAP CONTROLS" — the base map style and
    // which optional overlays/GoPreach layers are on. Deliberately plain UI
    // state, pushed to the WebView by its own dedicated effects below (see
    // each one's own doc comment) — never folded into the main data-driven
    // effect, so switching a layer can never touch camera position, search,
    // or any GoPreach territory data (spec §15's own "do not reset the
    // user's current map position... do not remove House Holder markers").
    var showMapLayers by remember { mutableStateOf(false) }
    var selectedBaseLayer by remember { mutableStateOf("standard") }
    var overlayStates by remember {
        mutableStateOf(mapOf("bicycle" to false, "hiking" to false, "railways" to false, "gpsTraces" to false, "mapNotes" to false, "mapData" to false))
    }
    var goPreachLayerStates by remember {
        mutableStateOf(mapOf("municipalities" to true, "groups" to true, "householders" to true))
    }
    // "Show or hide the Names and other details of Interested Person/Return
    // Visit/Bible Study" — the on-map "Name (Status)" + "RP <publisher>"
    // label every such marker can show. Defaults OFF (explicit instruction —
    // "Uncheck 'Names and details on map' as initial state in GoPreach
    // Layers"): the map opens clean/uncluttered, and a user who wants the
    // labels can still turn them on from Map Layers. Its own effect below,
    // same "never touch anything else" convention as every other layer
    // toggle here.
    var showPipelineDetails by remember { mutableStateOf(false) }

    // Fetches a fresh GPS fix the first time "My Location" is requested —
    // shared so a fix obtained once during this screen's lifetime is never
    // asked for twice. Surfaces the two specific failure states as
    // Snackbars rather than silently doing nothing. Declared after
    // [webViewRef] (a plain local function still reads the *current* value
    // of a `by remember` var, since it isn't itself snapshotted — it only
    // needs to be declared textually after so the name resolves).
    // "Current Location Button... Move the map to the user's current
    // location... Do not permanently change the default province-wide
    // opening behavior" (spec §19) — a deliberate, one-off camera move,
    // completely separate from the province-wide initial focus
    // ([hasAppliedProvinceFocus]/[hasAppliedMunicipalityProvinceFit]
    // below): this only ever runs when the button itself is tapped, never
    // automatically, and never touches either of those "already applied"
    // flags. Always re-centers on tap (never short-circuits just because
    // [myLocation] was already known once this screen session), so
    // pressing it again after panning away actually brings the camera back.
    suspend fun ensureMyLocation(recenter: Boolean = true): LatLng? {
        val existing = myLocation
        if (existing != null) {
            if (recenter) webViewRef?.evaluateJavascript("if (window.territoryMap) { window.territoryMap.setView([${existing.lat}, ${existing.lng}], 17); }", null)
            return existing
        }
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
        if (recenter) webViewRef?.evaluateJavascript("if (window.territoryMap) { window.territoryMap.setView([${fix.lat}, ${fix.lng}], 17); }", null)
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

    // "Territory Map Loading — Province Wide Only... do not load the entire
    // world map... automatically focus on the Province" — set the very
    // first time the map positions its camera with no Municipality/Barangay
    // narrowed down yet (see the main boundary/camera effect below); never
    // re-applied afterward so it can't fight a user's own later pan/zoom or
    // a deliberate "All Municipalities" re-selection every time [points]
    // simply refreshes off a live Firestore listener.
    var hasAppliedProvinceFocus by remember { mutableStateOf(false) }

    // Only reloads the page itself on first mount and on a manual "Retry"
    // tap — never on a search/filter change (see [html]'s own doc comment);
    // [points] reaches the already-loaded page via `window.setPoints`
    // instead (see the effect below).
    LaunchedEffect(reloadToken, webViewRef, containerReady) {
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
    // Bug fix (confirmed live: the Overpass fetch below never once
    // completed — logging showed the boundary-push effect firing
    // repeatedly, LOADED and all, but never reaching the point where it
    // would log a street-point request) — that effect is keyed on
    // [points] and [selectedAreaQuery]/[selectedAreaNames], both of which
    // this app's own live Firestore listeners can legitimately recompute
    // every second or two even when nothing a user would call "changed"
    // actually did; Compose cancels and restarts a `LaunchedEffect`
    // whenever any of its keys change, so a suspend chain slow enough to
    // take a few seconds — a live network round-trip to Overpass, exactly
    // this — could keep getting cancelled mid-flight before ever finishing,
    // forever. Running the fetch here instead, in its own effect keyed
    // only on [groupBoundaries] itself (structurally compared, so an
    // unrelated Firestore update that leaves every Group's own points
    // unchanged never restarts this either), decouples it completely from
    // that churn; [streetPointsByGroupId] is read back synchronously
    // (already resolved, no suspend call) by the boundary-push effect
    // below, which re-pushes an upgraded, street-snapped shape the moment
    // a fetch actually lands, without needing anything else to change.
    // "TERRITORY MAP – PROVINCE-WIDE COLOR CODING" — every Municipality in
    // the congregation's own Province, solid-filled and fit to camera, once,
    // the first real attempt this screen-open makes (same "consumed only
    // once a real attempt actually happens" convention [hasAppliedProvinceFocus]
    // below already uses — an unresolved [province] simply waits for the
    // next recomposition rather than forfeiting the one real attempt). Its
    // own decoupled effect, keyed only on (loadState, webViewRef, province)
    // — never on [points]/[selectedAreaQuery]/etc. — for the exact same
    // reason [streetPointsByGroupId] below needed its own effect: a
    // province-wide fetch (up to a dozen-plus real boundary lookups) must
    // never get cancelled mid-flight by an unrelated live-Firestore-driven
    // recomposition of this composable.
    var hasAppliedMunicipalityProvinceFit by remember { mutableStateOf(false) }
    LaunchedEffect(loadState, webViewRef, province) {
        if (loadState != MapLoadState.LOADED || hasAppliedMunicipalityProvinceFit) return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect
        val currentProvince = province ?: return@LaunchedEffect
        val entries = municipalityBoundaries(currentProvince)
        hasAppliedMunicipalityProvinceFit = true
        if (entries.isNotEmpty()) {
            val entriesJson = entries.joinToString(",", prefix = "[", postfix = "]") { (name, geometry) ->
                """{name:"${jsEscape(name)}",geometry:$geometry}"""
            }
            // "Make the entire Province selected when initial load of the
            // map" — always fits the camera to every Municipality in the
            // Province, unconditionally, on first load.
            webView.evaluateJavascript("if (window.setMunicipalityLayers) { window.setMunicipalityLayers($entriesJson, true); }", null)
            // A real bounds-fit across every Municipality just positioned
            // the camera far more precisely than the plain geocoded
            // center+zoom-9 pan below ever could — suppressing that
            // fallback here means the two never fight over the camera on
            // the same screen-open.
            hasAppliedProvinceFocus = true
        }
    }

    // "Do not reload or reset the Territory Map [when changing a map
    // layer]" — three independent effects, each keyed only on its own
    // piece of layer state (never on [points]/[groupBoundaries]/[province]/
    // etc.), so toggling a base map or overlay can never cancel/restart
    // anything data-driven, and a data refresh can never re-push a layer
    // choice the user didn't just change. `window.set*` on the JS side is
    // always idempotent (checks `map.hasLayer` before adding/removing), so
    // pushing the same state twice is harmless.
    LaunchedEffect(loadState, webViewRef, selectedBaseLayer) {
        if (loadState != MapLoadState.LOADED) return@LaunchedEffect
        webViewRef?.evaluateJavascript("if (window.setBaseLayer) { window.setBaseLayer('${jsEscape(selectedBaseLayer)}'); }", null)
    }
    LaunchedEffect(loadState, webViewRef, overlayStates) {
        if (loadState != MapLoadState.LOADED) return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect
        overlayStates.forEach { (id, enabled) ->
            webView.evaluateJavascript("if (window.setOverlayEnabled) { window.setOverlayEnabled('${jsEscape(id)}', $enabled); }", null)
        }
    }
    LaunchedEffect(loadState, webViewRef, goPreachLayerStates) {
        if (loadState != MapLoadState.LOADED) return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect
        goPreachLayerStates.forEach { (id, visible) ->
            webView.evaluateJavascript("if (window.setGoPreachLayerVisible) { window.setGoPreachLayerVisible('${jsEscape(id)}', $visible); }", null)
        }
    }
    LaunchedEffect(loadState, webViewRef, showPipelineDetails) {
        if (loadState != MapLoadState.LOADED) return@LaunchedEffect
        webViewRef?.evaluateJavascript("if (window.setShowPipelineLabels) { window.setShowPipelineLabels($showPipelineDetails); }", null)
    }

    var streetPointsByGroupId by remember { mutableStateOf<Map<String, List<Pair<Double, Double>>>>(emptyMap()) }
    LaunchedEffect(groupBoundaries) {
        for (entry in groupBoundaries) {
            val pts = entry.points
            if (pts.isEmpty() || streetPointsByGroupId.containsKey(entry.id)) continue
            val centroidLat = pts.sumOf { it.lat } / pts.size
            val centroidLng = pts.sumOf { it.lng } / pts.size
            val maxDist = pts.maxOf { haversineMeters(centroidLat, centroidLng, it.lat, it.lng) }
            val fetchRadius = (maxDist + 150.0).coerceIn(150.0, 500.0)
            val result = nearbyStreetPoints(centroidLat, centroidLng, fetchRadius)
            if (result != null) {
                streetPointsByGroupId = streetPointsByGroupId + (entry.id to result)
            }
        }
    }

    // "Expand House Holders Inside the Selected Territory... only show
    // House Holders actually located inside the selected territory. Do not
    // display unrelated House Holders from other territories" — a plain
    // client-side narrowing of the already-filtered [points], applied only
    // once a Group's own territory has actually been tapped
    // ([selectedGroupId]); a Publisher-sharing point's own [MapPoint.groupId]
    // is always null (see that field's own doc comment), so it's dropped
    // here too — a Publisher was never a House Holder to begin with (spec
    // §15). Selecting nothing (`null`, the default) shows every point,
    // completely unchanged from before this Group-tap feature existed.
    val visiblePoints = remember(points, selectedGroupId) {
        val groupId = selectedGroupId
        if (groupId == null) points else points.filter { it.groupId == groupId }
    }
    LaunchedEffect(loadState, webViewRef, visiblePoints, selectedAreaQuery, selectedAreaNames, groupBoundaries, province, streetPointsByGroupId) {
        if (loadState != MapLoadState.LOADED) return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect
        // "Update only the required markers, search results, and line
        // barrier" — pushes the latest already-filtered [visiblePoints]
        // straight into the already-loaded page (see `window.setPoints`'s
        // own doc comment) instead of Kotlin reloading the whole WebView;
        // runs on every search/filter change, same as the boundary logic
        // below it, never only once at load.
        webView.evaluateJavascript("if (window.setPoints) { window.setPoints(${mapPointsToJs(visiblePoints)}); }", null)
        // `setPoints` above rebuilds every marker from scratch, so whichever
        // one was highlighted needs to be re-applied right after — it no
        // longer refers to a marker object that still exists otherwise.
        val selectedIdJs = selectedPointId?.let { "'${jsEscape(it)}'" } ?: "null"
        webView.evaluateJavascript("if (window.setSelectedMarker) { window.setSelectedMarker($selectedIdJs); }", null)
        myLocation?.let { fix -> webView.evaluateJavascript("if (window.setMyLocation) { window.setMyLocation(${fix.lat}, ${fix.lng}); }", null) }
        // Real polygon geometry first (no network dependency at all — see
        // [TerritoryBoundaryRepository]); only reach for the unreliable
        // on-device Geocoder (already found flaky on this session's own
        // non-genuine-GMS test device) as a last resort, once there isn't
        // even a point on screen to build a scope boundary from.
        val geometry = selectedAreaNames?.let { (muni, brgy) -> boundaryGeometry(muni, brgy) }
        // "Do not remove or replace the existing municipality line
        // barriers... the municipality boundary must remain visible at all
        // times [while Field Service Group barriers are also shown]" — bug
        // fix: this used to skip the circle-fallback outer boundary
        // entirely whenever any Group barrier also existed
        // (`groupBoundaries.isEmpty()`), leaving a selected Municipality
        // with real records but no bundled real polygon showing *only* the
        // inner Group territories and no outer Municipality boundary at
        // all. The outer boundary (real polygon, or this circle
        // approximation when there's no bundled polygon) is a completely
        // separate concept from the Group barriers now drawn alongside it
        // (see `setGroupScopeBoundaries` below) — one is the container, the
        // other is what's inside it — so its own presence must never depend
        // on whether the inner Group barriers happen to have anything to
        // draw. [cameraAlreadyFit] tracks whether this block already
        // positioned the camera, so the Group-boundary push below only
        // takes over centering when nothing here already did.
        var cameraAlreadyFit = false
        when {
            geometry != null -> {
                // Draw the area boundary whenever a specific Municipality/
                // Barangay is selected, regardless of whether it has any
                // records — spec ties the boundary to the *selection*, not
                // to an empty result. [setAreaBoundaryGeoJson] itself frames
                // the camera to these exact bounds, taking priority over any
                // marker elsewhere in the province (a Publisher's shared
                // location, say) — see that JS function's own doc comment —
                // but only for an actual deliberate selection
                // ([explicitAreaSelection]); [selectedAreaNames]' own
                // implicit single-Municipality fallback still draws this
                // same boundary ("must be shown always") without forcing
                // the camera to jump there on every plain map open.
                webView.evaluateJavascript(
                    "if (window.setAreaBoundaryGeoJson) { window.setAreaBoundaryGeoJson($geometry, $explicitAreaSelection); }",
                    null,
                )
                if (explicitAreaSelection) cameraAlreadyFit = true
            }
            selectedAreaQuery != null -> {
                // No bundled real polygon for this Municipality/Barangay —
                // best-effort circle fallback, forward-geocoded from the
                // selected area's own name, so it still shows *something*
                // real as the outer boundary, exactly like the real-polygon
                // case above, regardless of whether it has any records (and
                // therefore any Group barriers) inside it or not.
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
                    cameraAlreadyFit = true
                } else {
                    webView.evaluateJavascript("if (window.clearAreaBoundary) { window.clearAreaBoundary(); }", null)
                }
            }
            else -> {
                // No real administrative polygon this update — the
                // per-Congregation-Group barriers below are the only
                // boundary drawn (or nothing selected/visible at all).
                webView.evaluateJavascript("if (window.clearAreaBoundary) { window.clearAreaBoundary(); }", null)
            }
        }
        // "Territory Map Loading — Province Wide Only" — bug fix: this used
        // to live only in the `when` block's own `else` branch above, which
        // stopped running once [selectedAreaNames]' own implicit
        // single-Municipality fallback started resolving real `geometry`
        // there instead (the common case — nothing explicitly searched, but
        // a real bundled boundary still exists for the one Municipality
        // every visible record shares). Gating on [explicitAreaSelection]
        // directly, independent of which branch above actually drew the
        // boundary, means the Province-wide initial focus still applies
        // whenever nothing was *deliberately* searched, exactly as before —
        // "do not initially focus on only one Householder or one Group
        // unless the user specifically searches for it." Skipped once a
        // Share-Location deep link ([focusLat]/[focusLng]) already claims
        // the camera, and only ever applied once per screen-open (see
        // [hasAppliedProvinceFocus]'s own doc comment) — consumed only once
        // a real geocode attempt actually happens, so a still-loading
        // [province] simply waits for the next re-run instead of
        // forfeiting the one real attempt.
        if (!explicitAreaSelection && !hasAppliedProvinceFocus && focusLat == null && focusLng == null && province != null) {
            hasAppliedProvinceFocus = true
            val fix = geocodeArea("$province, Philippines")
            if (fix != null) {
                webView.evaluateJavascript("if (window.territoryMap) { window.territoryMap.setView([${fix.lat}, ${fix.lng}], 9); }", null)
                cameraAlreadyFit = true
            }
        }
        // Bug fix (explicit instruction, confirmed live: two records from
        // the same Group showed a territory covering the whole Barangay
        // they happen to live in, when it must show only the area actually
        // between them) — this used to also resolve each Group's real
        // bundled Barangay polygon(s) and union them in as `geometry`,
        // which [buildRealGroupTerritoryLayer] preferred over the tight
        // point-hull whenever available. That's removed: every Group's
        // Territory Scope is now always [MapGroupBoundary.points]' own
        // point-hull shape (see [buildGroupBoundaryLayer]), proportional to
        // that Group's own actual records, never a whole administrative
        // unit standing in for it.
        // "Snap the small territory shape to real streets/blocks where
        // possible" — [nearbyStreetPoints] (best-effort; see its own doc
        // comment for why a failure/timeout is never treated as an error)
        // gives each Group's own territory real OpenStreetMap street-node
        // coordinates to shape itself around, tightly capped to that
        // Group's own footprint so this can never balloon into anything
        // close to a whole administrative unit:
        //  - 3+ records (already a real point-hull) — each vertex is
        //    nudged onto its own nearest real street node, only when one
        //    exists within [snapMaxMeters]; the hull's own actual shape/
        //    extent is otherwise unchanged.
        //  - 1-2 records (the JS side's own fully synthetic jittered-
        //    polygon fallback — exactly the "just any drawing" this
        //    replaces) — the handful of real street nodes nearest this
        //    Group's own centroid stand in for that synthetic shape
        //    instead, still only ever within [fetchRadius] of the Group's
        //    own real records.
        // A plain `for` loop, not `joinToString { }` — this used to call
        // [nearbyStreetPoints] (a suspend function) inline here, but that
        // starved it: this whole effect is keyed on several values (live
        // Firestore-backed state, in particular) that can legitimately
        // change every second or two even when nothing a user would call
        // "changed" actually did, and Compose cancels+restarts a
        // `LaunchedEffect` on every key change — so the network round-trip
        // never once survived long enough to finish. The fetch itself now
        // happens in its own effect keyed only on [groupBoundaries]
        // (see [streetPointsByGroupId] above); this loop just reads back
        // whatever has already resolved, synchronously.
        val snapMaxMeters = 60.0
        val groupEntryJsons = mutableListOf<String>()
        for (entry in groupBoundaries) {
            val pts = entry.points
            val augmented: List<Pair<Double, Double>> = if (pts.isEmpty()) {
                emptyList()
            } else {
                val centroidLat = pts.sumOf { it.lat } / pts.size
                val centroidLng = pts.sumOf { it.lng } / pts.size
                val streetPoints = streetPointsByGroupId[entry.id].orEmpty()
                if (streetPoints.isEmpty()) {
                    pts.map { it.lat to it.lng }
                } else if (pts.size >= 3) {
                    pts.map { p ->
                        val nearest = streetPoints.minByOrNull { haversineMeters(p.lat, p.lng, it.first, it.second) }
                        if (nearest != null && haversineMeters(p.lat, p.lng, nearest.first, nearest.second) <= snapMaxMeters) nearest else p.lat to p.lng
                    }
                } else {
                    val nearestStreetPoints = streetPoints.sortedBy { haversineMeters(centroidLat, centroidLng, it.first, it.second) }.take(6)
                    (pts.map { it.lat to it.lng } + nearestStreetPoints).distinct()
                }
            }
            val pointsJson = augmented.joinToString(",", prefix = "[", postfix = "]") { (lat, lng) -> "[$lat,$lng]" }
            groupEntryJsons.add("""{id:"${jsEscape(entry.id)}",name:"${jsEscape(entry.name)}",color:"${jsEscape(entry.color)}",points:$pointsJson}""")
        }
        val groupEntriesJson = groupEntryJsons.joinToString(",", prefix = "[", postfix = "]")
        // "Create a separate barrier line for every Congregation Group" —
        // always pushed, independently of whichever case the `when` above
        // took, so a real (or approximated) Municipality/Barangay outline
        // and each Group's own color-coded barrier are always visible
        // together — the outer container and its inner territories, never
        // one replacing the other. Camera-fitting is suppressed here only
        // when the block above already positioned it this same update.
        webView.evaluateJavascript(
            "if (window.setGroupScopeBoundaries) { window.setGroupScopeBoundaries($groupEntriesJson, ${!cameraAlreadyFit}); }",
            null,
        )
        // "Do not hide the Municipality map [boundary]" — Group territories
        // are drawn *after* the Municipality/Barangay outline above, so a
        // now-solid Group fill would otherwise land on top of it in the
        // SVG stack and visually bury its line/fill underneath a Group's
        // own color, especially once a Group's real Barangay-union
        // territory covers roughly the same area as the Municipality
        // itself. Explicitly raising the Municipality boundary back above
        // every Group layer, every update, guarantees it stays visible
        // regardless of how solid/opaque Group fills are.
        webView.evaluateJavascript(
            "if (window.areaBoundary && window.areaBoundary.bringToFront) { window.areaBoundary.bringToFront(); }",
            null,
        )
    }

    // "GROUP TERRITORY & PUBLISHER TERRITORY DIVISION" — its own decoupled
    // effect, keyed only on [selectedGroupId]/[selectedGroupPublisherBoundaries]/
    // [showPublisherTerritories] (never on [points]/[selectedAreaQuery]/etc.),
    // same "never let an unrelated recomposition cancel this" convention
    // every other independent push effect on this screen already follows.
    // Clearing (`groupId == null` OR [showPublisherTerritories] false) is
    // just as real a state change as drawing — the JS side's own
    // `clearPublisherScopeBoundaries` also restores the parent Group's
    // normal solid style, so this must fire for that transition too, not
    // only for a new selection.
    LaunchedEffect(loadState, webViewRef, selectedGroupId, selectedGroupPublisherBoundaries, showPublisherTerritories) {
        if (loadState != MapLoadState.LOADED) return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect
        val groupId = selectedGroupId
        if (groupId == null || !showPublisherTerritories) {
            webView.evaluateJavascript("if (window.clearPublisherScopeBoundaries) { window.clearPublisherScopeBoundaries(); }", null)
        } else {
            val entriesJson = selectedGroupPublisherBoundaries.joinToString(",", prefix = "[", postfix = "]") { entry ->
                val pointsJson = entry.points.joinToString(",", prefix = "[", postfix = "]") { (lat, lng) -> "[$lat,$lng]" }
                """{id:"${jsEscape(entry.id)}",name:"${jsEscape(entry.name)}",color:"${jsEscape(entry.color)}",points:$pointsJson}"""
            }
            webView.evaluateJavascript(
                "if (window.setPublisherScopeBoundaries) { window.setPublisherScopeBoundaries('${jsEscape(groupId)}', $entriesJson); }",
                null,
            )
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

                            // "Selecting a Territory Group" (spec §10) — a
                            // tap anywhere inside a Group's own solid
                            // territory fill (see `buildGroupBoundaryLayer`'s
                            // own click binding) lands here with that
                            // Group's real id.
                            @JavascriptInterface
                            fun showGroupDetails(groupId: String) {
                                self.post {
                                    // "Re-clicking the Group Territory...
                                    // closes the Group details" (spec §9) —
                                    // tapping the *same* already-selected
                                    // Group's own fill again toggles
                                    // everything closed (Group details AND
                                    // any Publisher Territories currently
                                    // showing, via the existing
                                    // `LaunchedEffect(selectedGroupId)`
                                    // reset this already triggers); tapping
                                    // a *different* Group selects that one
                                    // fresh instead.
                                    if (selectedGroupId == groupId) {
                                        selectedGroupId = null
                                    } else {
                                        selectedGroupId = groupId
                                        groupInfoSheetDismissed = false
                                    }
                                }
                            }

                            // "Clicking a Publisher Territory" (spec §6) — a
                            // tap anywhere inside a Publisher's own
                            // subdivision fill (see
                            // `buildPublisherBoundaryLayer`'s own click
                            // binding, only ever drawn once a Group is
                            // already selected) lands here with that
                            // Publisher's real personId.
                            @JavascriptInterface
                            fun showPublisherDetails(publisherId: String) {
                                self.post { selectedPublisherId = publisherId }
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
        // null only at the screen's absolute default ("All"), so this never
        // alarms a brand-new congregation with zero territory records yet
        // before they've actually searched for anything.
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
        // permission the first time. A separate, deliberate action from the
        // Province-wide initial open (spec §19) — see [ensureMyLocation]'s
        // own doc comment.
        SmallFloatingActionButton(
            onClick = { scope.launch { ensureMyLocation() } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 140.dp),
        ) {
            Icon(Icons.Rounded.MyLocation, contentDescription = "Show my location")
        }

        // "Province Overview / Reset" map control (spec §18) — re-fits the
        // camera to every Municipality's own bounds, independent of the
        // Refresh button above (which re-fetches data/reloads the page);
        // this only ever moves the camera.
        SmallFloatingActionButton(
            onClick = { webViewRef?.evaluateJavascript("if (window.resetProvinceView) { window.resetProvinceView(); }", null) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 204.dp),
        ) {
            Icon(Icons.Rounded.Public, contentDescription = "Province overview")
        }

        // "Add a professional 'Map Layers' button" (spec §1).
        SmallFloatingActionButton(
            onClick = { showMapLayers = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 268.dp),
        ) {
            Icon(Icons.Rounded.Layers, contentDescription = "Map layers")
        }

        // "Full-Screen Map" (spec §20) — see [isFullScreen]'s own doc
        // comment in the parent composable for why this hides only the
        // Scaffold's TopAppBar, never anything inside this Box.
        SmallFloatingActionButton(
            onClick = onToggleFullScreen,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 332.dp),
        ) {
            Icon(if (isFullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, contentDescription = if (isFullScreen) "Exit full screen" else "Full screen")
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

    // "Clicking a Publisher Territory" takes over from the parent Group's
    // own info sheet while a Publisher is selected — dismissing it falls
    // back to the Group sheet still being open underneath (selectedGroupId
    // itself is untouched by dismissing this one), rather than stacking
    // two sheets or losing the Group context entirely.
    if (selectedPublisherInfo != null) {
        PublisherInfoSheet(
            info = selectedPublisherInfo,
            onDismiss = { selectedPublisherId = null },
        )
    } else if (selectedGroupInfo != null && !groupInfoSheetDismissed) {
        GroupInfoSheet(
            info = selectedGroupInfo,
            // Just hides this sheet's own UI — see [groupInfoSheetDismissed]'s
            // own doc comment for why this must never also clear
            // [selectedGroupId] the way it used to.
            onDismiss = { groupInfoSheetDismissed = true },
            onClearFilter = { selectedGroupId = null },
            showPublisherTerritories = showPublisherTerritories,
            onToggleShowPublisherTerritories = { showPublisherTerritories = !showPublisherTerritories },
        )
    }

    if (showMapLayers) {
        MapLayersSheet(
            selectedBaseLayer = selectedBaseLayer,
            onSelectBaseLayer = { selectedBaseLayer = it },
            overlayStates = overlayStates,
            onOverlayChange = { id, enabled -> overlayStates = overlayStates + (id to enabled) },
            goPreachLayerStates = goPreachLayerStates,
            onGoPreachLayerChange = { id, visible -> goPreachLayerStates = goPreachLayerStates + (id to visible) },
            showPipelineDetails = showPipelineDetails,
            onShowPipelineDetailsChange = { showPipelineDetails = it },
            onDismiss = { showMapLayers = false },
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
private fun mapPointsToJs(points: List<MapPoint>): String =
    points.joinToString(",", prefix = "[", postfix = "]") { p ->
        // "Householder Name + Status + assigned Group Member/Publisher" —
        // [publisherName] is the *actual* Publisher assigned to this record
        // ([MapPoint.publisherName]'s own doc comment: sourced from the
        // record's real publisherPersonId, never the nearest publisher on
        // the map); omitted (JS null) for a PUBLISHER/ME point, which has no
        // assigned Publisher of its own, so the tooltip stays one line for
        // those exactly as before.
        val publisherJs = p.publisherName?.let { "\"${jsEscape(it)}\"" } ?: "null"
        """{id:"${jsEscape(p.id)}",lat:${p.lat},lng:${p.lng},name:"${jsEscape(p.name)}",status:"${jsEscape(p.status)}",color:"${jsEscape(p.groupColor)}",kind:"${p.kind.name}",publisherName:$publisherJs}"""
    }

/** Built once, with no markers baked in — [TerritoryLiveMap]'s own
 * points-update effect populates the map via `window.setPoints` right after
 * the page finishes loading, and again on every later search/filter change,
 * without ever reloading this page (see that function's own doc comment on
 * why: spec's own "do not reload the entire map unnecessarily after every
 * search"). */
private fun buildTerritoryMapHtml(): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css">
        <style>
          /* height:100% cascading from html->body->#map depends on every
             ancestor resolving to a *definite* pixel height; anchoring #map
             with all four absolute offsets sizes it directly from the
             viewport instead (see TerritoryLiveMap's own bug-fix history). */
          html, body { height: 100%; margin: 0; padding: 0; }
          #map { position: absolute; top: 0; left: 0; right: 0; bottom: 0; }
          /* "SMALLER MAP ICONS AND TEXT... no fill, badge, box, bubble, or
             solid background... clean text with appropriate contrast" —
             every text label on this map (marker names, Group/Municipality/
             Publisher names) now uses a plain, borderless, background-free
             halo technique instead: white text with a solid dark outline
             (4 directional shadows) plus a soft glow, the same convention
             most professional map labels use to stay readable over *any*
             color/tile underneath without needing an opaque chip behind
             the text. Shared as one rule ([.map-label-text]) rather than
             repeated per label class, since every label now wants the
             exact same treatment. */
          .map-label-text {
            background: none; border: none; box-shadow: none; padding: 0;
            color: #ffffff;
            text-shadow: -1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000, 0 0 3px rgba(0,0,0,0.7);
          }
          .territory-label { font-size: 10px; font-weight: 600; white-space: nowrap; }
          .territory-marker { background: transparent; border: none; }
          /* "Show group names in every territory map... professional Group
             icon/name" — [font-size] is set inline per-marker by
             [groupLabelHtml] (map-aware/zoom-responsive, spec §1/§4/§5), so
             it's deliberately absent here. */
          .group-name-label { font-weight: 700; white-space: nowrap; }
          /* "Professional Territory Group icon... smaller... clean modern
             appearance... uses the same color assigned to that Territory
             Group" — a small circular badge (never the teardrop pin shape
             a House Holder marker uses, so the two are never visually
             confused), solid-filled in the Group's own color with a plain
             white flag glyph, recognizable at a glance at any zoom. */
          .group-badge { width: 15px; height: 15px; border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 1px 2px rgba(0,0,0,0.45); border: 1.5px solid #ffffff; flex: 0 0 auto; }
          .group-label-wrap { display: flex; align-items: center; gap: 4px; }
          /* Province-wide overview (spec "PROVINCE → MUNICIPALITY → TERRITORY
             GROUP → HOUSE HOLDER") — same halo text as every other label
             now, just a lighter font-weight, since a Municipality is the
             outer/background layer of that hierarchy, never the focus.
             [font-size] set inline by [refreshMunicipalityLabels], same
             zoom-responsive convention as the Group label above. */
          .municipality-name-label { font-weight: 600; white-space: nowrap; letter-spacing: 0.2px; }
          /* "GROUP TERRITORY & PUBLISHER TERRITORY DIVISION" — the same
             icon-badge convention as .group-badge above, one size down (a
             Publisher's own subdivision is always smaller than its parent
             Group's territory), with a plain person glyph instead of a
             flag so the two are never confused at a glance even when both
             a Group's own [now-subtle] boundary and its Publisher
             subdivisions are visible together. */
          .publisher-badge { width: 12px; height: 12px; border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 1px 2px rgba(0,0,0,0.45); border: 1.5px solid #ffffff; flex: 0 0 auto; }
          .publisher-label-wrap { display: flex; align-items: center; gap: 4px; }
          .publisher-name-label { font-weight: 700; white-space: nowrap; }
        </style>
        </head>
        <body>
        <div id="map"></div>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
        <script>
        try {
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

          // "OPENSTREETMAP MAP LAYERS" — only styles this app can actually
          // load with no paid/keyed provider are offered (spec's own "Only
          // show layers that are actually supported... Do not display a
          // layer that cannot be loaded... do not pretend that [a keyed
          // service] is a completely free OSM service"). A full "Transport"
          // base style (buses+rail together) has no free, key-less tile
          // provider — Thunderforest Transport is the usual one and needs a
          // paid API key this app doesn't have — so it's deliberately not
          // offered here; [OVERLAY_TILE_DEFS.railways] below (OpenRailwayMap)
          // covers the rail half of that ask for free instead. Each style's
          // own required attribution is kept intact, per that provider's own
          // usage policy (spec §21) — never just the plain OSM line reused
          // for a different provider's tiles.
          var BASE_LAYER_DEFS = {
            standard: { url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', options: { maxZoom: 19, subdomains: 'abc', attribution: '&copy; OpenStreetMap contributors' } },
            topo: { url: 'https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png', options: { maxZoom: 17, subdomains: 'abc', attribution: 'Map data: &copy; OpenStreetMap contributors, SRTM | Map style: &copy; OpenTopoMap (CC-BY-SA)' } },
            cycling: { url: 'https://{s}.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png', options: { maxZoom: 20, subdomains: 'abc', attribution: '&copy; OpenStreetMap contributors, tiles style by CyclOSM' } },
            humanitarian: { url: 'https://tile-{s}.openstreetmap.fr/hot/{z}/{x}/{y}.png', options: { maxZoom: 19, subdomains: 'abc', attribution: '&copy; OpenStreetMap contributors, tiles style by Humanitarian OSM Team' } },
            // "Detailed/Colorful" (spec's Shortbread/MapTiler OMT example)
            // was tried here with CARTO's Voyager rastertiles endpoint and
            // reverted: confirmed live that it now demands an API key this
            // app doesn't have (a plain, un-styled "API KEY REQUIRED"
            // watermark tiled across the whole map) — every genuinely free,
            // key-less "detailed/colorful" OSM raster provider checked
            // (MapTiler OMT, Stadia's Stamen styles) has the same problem.
            // Per spec §21's own "do not pretend that [a keyed service] is
            // a completely free OSM service" / §1's "do not display a layer
            // that cannot be loaded," this style is simply not offered
            // rather than shipped broken; Standard/Humanitarian already
            // cover a clearly-labeled, readable base map.
          };
          var baseLayerInstances = { standard: tiles };
          window.currentBaseLayerId = 'standard';
          // Bug fix (confirmed live: a blank gray map — no tiles, no
          // Municipality fills, nothing — with logcat showing "Cannot
          // convert undefined or null to object at Object.keys... at
          // refreshGroupLabels"): this used to be declared right before
          // [buildGroupBoundaryLayer] itself, much further down this same
          // script — but [window.setPoints]'s own `fitToMarkers()` call
          // fires a synchronous `zoomend` event the very first time it
          // runs (during this script's own initial `window.setPoints([])`
          // call near the bottom), which immediately invokes
          // [refreshGroupLabels]. That happens long before the script's
          // own top-to-bottom execution ever reaches the line that used to
          // assign this — `Object.keys(undefined)` throws, and since this
          // is a *plain assignment* (unlike a hoisted `function` statement,
          // execution order genuinely matters here) the uncaught exception
          // aborts every remaining line of the script, including the
          // `buildGroupBoundaryLayer`/`setGroupScopeBoundaries` definitions
          // and the final `invalidateSize()` calls — explaining why
          // *nothing* rendered, not just Group labels. Declared here
          // instead, before anything that could possibly trigger a
          // `zoomend` event.
          window.groupLabelMarkers = {};
          // Same fix, same reason, for the Publisher-territory-subdivision
          // globals below (see [buildPublisherBoundaryLayer]/[refreshPublisherLabels])
          // — they're also read from the shared `zoomend` handler, which
          // can fire before this script's own execution would otherwise
          // reach their declaration.
          window.publisherLabelMarkers = {};
          window.publisherBoundaryLayers = {};
          window.publisherSubdividedGroupId = null;
          // Swaps only the tile layer — every GoPreach territory layer
          // lives in its own separate Leaflet layer object, entirely
          // untouched by this (spec §11/§15's own "must not remove the
          // GoPreach Territory Map data... do not reset the user's current
          // map position"); no camera change here either.
          window.setBaseLayer = function(id) {
            var def = BASE_LAYER_DEFS[id];
            if (!def) return;
            if (!baseLayerInstances[id]) { baseLayerInstances[id] = L.tileLayer(def.url, def.options); }
            var current = baseLayerInstances[window.currentBaseLayerId];
            if (current && map.hasLayer(current) && current !== baseLayerInstances[id]) { map.removeLayer(current); }
            if (!map.hasLayer(baseLayerInstances[id])) { baseLayerInstances[id].addTo(map); }
            window.currentBaseLayerId = id;
          };

          // "MAP OVERLAYS" — real, free, key-less raster overlays (each its
          // own separate tile layer, always in Leaflet's tilePane, so —
          // same as every base style above — these can never render above
          // or interfere with a GoPreach vector layer regardless of
          // add/remove order). "Public Transit" has the same no-free-
          // provider problem as the Transport base map above, so it's
          // covered by [railways] (OpenRailwayMap) rather than offered as
          // something it isn't; "Bicycle Routes"/"Hiking" use
          // waymarkedtrails.org, the same overlay provider OpenStreetMap's
          // own osm.org site uses for these.
          var OVERLAY_TILE_DEFS = {
            bicycle: { url: 'https://tile.waymarkedtrails.org/cycling/{z}/{x}/{y}.png', options: { maxZoom: 18, opacity: 0.85, attribution: '&copy; waymarkedtrails.org, OpenStreetMap contributors' } },
            hiking: { url: 'https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png', options: { maxZoom: 18, opacity: 0.85, attribution: '&copy; waymarkedtrails.org, OpenStreetMap contributors' } },
            railways: { url: 'https://{s}.tiles.openrailwaymap.org/standard/{z}/{x}/{y}.png', options: { maxZoom: 19, subdomains: 'abc', opacity: 0.9, attribution: '&copy; OpenRailwayMap contributors' } },
            gpsTraces: { url: 'https://gps.tile.openstreetmap.org/lines/{z}/{x}/{y}.png', options: { maxZoom: 20, opacity: 0.7, attribution: 'GPS traces &copy; OpenStreetMap contributors' } },
          };
          var overlayLayerInstances = {};
          // "Map Notes" — real open OSM Notes near whatever the map is
          // currently showing, via OSM's own public Notes API (no key
          // needed to read). "GoPreach House Holder records and Territory
          // Groups must remain separate from OpenStreetMap Notes" — its own
          // [notesLayer], never mixed into [cluster] (House Holders) or any
          // GoPreach layer.
          window.notesLayer = L.layerGroup();
          function refreshMapNotes() {
            if (!map.hasLayer(window.notesLayer)) return;
            var b = map.getBounds();
            var bbox = [b.getWest(), b.getSouth(), b.getEast(), b.getNorth()].join(',');
            fetch('https://api.openstreetmap.org/api/0.6/notes.json?bbox=' + bbox + '&limit=100')
              .then(function(r) { return r.json(); })
              .then(function(data) {
                window.notesLayer.clearLayers();
                (data.features || []).forEach(function(f) {
                  var coords = f.geometry.coordinates;
                  var comments = f.properties && f.properties.comments;
                  var text = (comments && comments[0] && comments[0].text) || 'Map Note';
                  var icon = L.divIcon({
                    className: 'territory-marker',
                    html: '<div style="background:#fff;border:2px solid #FB8C00;border-radius:50%;width:22px;height:22px;display:flex;align-items:center;justify-content:center;font-size:12px;box-shadow:0 1px 3px rgba(0,0,0,.4);">&#128221;</div>',
                    iconSize: [22, 22], iconAnchor: [11, 11],
                  });
                  L.marker([coords[1], coords[0]], { icon: icon }).bindPopup(escapeHtml(text)).addTo(window.notesLayer);
                });
              })
              .catch(function(e) { console.error('Map Notes fetch failed: ' + e); });
          }
          window.setMapNotesEnabled = function(enabled) {
            if (enabled) { map.addLayer(window.notesLayer); refreshMapNotes(); }
            else { map.removeLayer(window.notesLayer); }
          };
          // "Map Data" — the underlying OSM way geometry itself (osm.org's
          // own "Data" layer), fetched live via the same Overpass API
          // [OverpassStreetRepository]/[nearbyStreetPoints] already use.
          // osm.org's real Data layer only loads once zoomed in close
          // enough that a bbox query stays cheap — mirrored here via
          // [MAP_DATA_MIN_ZOOM], consistent with spec's own "do not display
          // a layer that cannot be loaded" rather than firing an
          // unbounded, possibly-huge query at whatever's on screen.
          window.mapDataLayer = L.layerGroup();
          var MAP_DATA_MIN_ZOOM = 16;
          function refreshMapData() {
            if (!map.hasLayer(window.mapDataLayer)) return;
            window.mapDataLayer.clearLayers();
            if (map.getZoom() < MAP_DATA_MIN_ZOOM) return;
            var b = map.getBounds();
            var bbox = [b.getSouth(), b.getWest(), b.getNorth(), b.getEast()].join(',');
            var query = '[out:json][timeout:15];(way(' + bbox + '););out geom 300;';
            fetch('https://overpass-api.de/api/interpreter', { method: 'POST', body: 'data=' + encodeURIComponent(query) })
              .then(function(r) { return r.json(); })
              .then(function(data) {
                (data.elements || []).forEach(function(el) {
                  if (!el.geometry) return;
                  var latlngs = el.geometry.map(function(g) { return [g.lat, g.lon]; });
                  L.polyline(latlngs, { color: '#FF6600', weight: 2, opacity: 0.75, interactive: false }).addTo(window.mapDataLayer);
                });
              })
              .catch(function(e) { console.error('Map Data fetch failed: ' + e); });
          }
          window.setMapDataEnabled = function(enabled) {
            if (enabled) { map.addLayer(window.mapDataLayer); refreshMapData(); }
            else { map.removeLayer(window.mapDataLayer); }
          };
          map.on('moveend', function() { refreshMapNotes(); refreshMapData(); });
          window.setOverlayEnabled = function(id, enabled) {
            if (id === 'mapNotes') { window.setMapNotesEnabled(enabled); return; }
            if (id === 'mapData') { window.setMapDataEnabled(enabled); return; }
            var def = OVERLAY_TILE_DEFS[id];
            if (!def) return;
            if (!overlayLayerInstances[id]) { overlayLayerInstances[id] = L.tileLayer(def.url, def.options); }
            if (enabled) { if (!map.hasLayer(overlayLayerInstances[id])) overlayLayerInstances[id].addTo(map); }
            else { if (map.hasLayer(overlayLayerInstances[id])) map.removeLayer(overlayLayerInstances[id]); }
          };

          // "GoPreach Layers" show/hide (Municipalities/Territory Groups/
          // House Holders) — [window.municipalitiesVisible]/
          // [window.groupLayersVisible]/[window.householdersVisible] default
          // true and are consulted by [setMunicipalityLayers]/
          // [setGroupScopeBoundaries]/[setPoints] below every time GoPreach's
          // own data refreshes, so a hidden layer stays hidden across a live
          // Firestore update instead of silently reappearing.
          window.municipalitiesVisible = true;
          window.groupLayersVisible = true;
          window.householdersVisible = true;
          window.setGoPreachLayerVisible = function(id, visible) {
            if (id === 'municipalities') {
              window.municipalitiesVisible = visible;
              if (window.municipalityLayers) {
                if (visible) { if (!map.hasLayer(window.municipalityLayers)) window.municipalityLayers.addTo(map); }
                else { if (map.hasLayer(window.municipalityLayers)) map.removeLayer(window.municipalityLayers); }
              }
            } else if (id === 'groups') {
              window.groupLayersVisible = visible;
              Object.keys(window.groupBoundaryLayers).forEach(function(gid) {
                var layer = window.groupBoundaryLayers[gid];
                if (visible) { if (!map.hasLayer(layer)) layer.addTo(map); } else { if (map.hasLayer(layer)) map.removeLayer(layer); }
              });
            } else if (id === 'householders') {
              window.householdersVisible = visible;
              if (visible) { if (!map.hasLayer(cluster)) map.addLayer(cluster); } else { if (map.hasLayer(cluster)) map.removeLayer(cluster); }
            }
          };
          // "Province Overview / Reset" map control — re-fits the camera to
          // every Municipality's own combined bounds without reloading the
          // page or touching any other layer/state.
          window.resetProvinceView = function() {
            if (window.municipalityLayers) { map.fitBounds(window.municipalityLayers.getBounds().pad(0.05)); }
          };

          // "PROVINCE-WIDE COLOR CODING" — every Municipality in the
          // congregation's own Province, filled solid (never outline-only,
          // never a gradient/transparent wash), each a different color from
          // this fixed, professional palette so neighboring Municipalities
          // are always visually distinguishable at a glance, even before
          // any Congregation Group territory is drawn on top.
          // "SOLID COLORS AND 100% OPACITY... do not automatically reduce
          // the opacity of any color" — [MUNICIPALITY_FILL_OPACITY] is now
          // fully opaque, same as a Group's own [GROUP_FILL_OPACITY] below;
          // "Territory Group color should take visual priority" (an
          // unchanged, still-real requirement) is achieved purely through
          // z-order now instead — always `bringToBack()`-ed, so a Group's
          // own opaque color, drawn after, simply paints over this layer
          // everywhere the two overlap, with this layer's own full color
          // only showing through wherever no Group territory covers it.
          var MUNICIPALITY_COLORS = ['#1E88E5', '#43A047', '#FB8C00', '#8E24AA', '#00ACC1', '#F4511E', '#6D4C41', '#3949AB', '#7CB342', '#D81B60', '#00897B', '#5E35B1', '#C0CA33', '#546E7A'];
          var MUNICIPALITY_FILL_OPACITY = 1.0;
          // "MUNICIPALITY LABELS... map-responsive... show compactly when
          // zoomed out" — below [MUNICIPALITY_LABEL_MIN_ZOOM] the label is
          // hidden entirely (the province-wide initial view routinely sits
          // right around this zoom, with a dozen-plus Municipality labels
          // that would otherwise overlap each other and their neighbors'
          // own areas); font-size scales up to [MUNICIPALITY_LABEL_MAX_ZOOM]
          // — see [refreshMunicipalityLabels].
          var MUNICIPALITY_LABEL_MIN_ZOOM = 7;
          var MUNICIPALITY_LABEL_MAX_ZOOM = 14;
          window.municipalityLayers = null;
          window.municipalityLabelMarkers = [];
          // [fit] mirrors [window.setAreaBoundaryGeoJson]'s own convention —
          // true only for the one-time initial "show the entire Province"
          // open (spec's own "do not initially focus on only one Householder
          // or Group... the initial map view must clearly show the province
          // divided into its different municipalities"); a later refresh of
          // this same layer (e.g. the Province simply re-resolving) must
          // never yank the camera away from wherever the user has since
          // navigated to.
          window.setMunicipalityLayers = function(entries, fit) {
            if (window.municipalityLayers) { map.removeLayer(window.municipalityLayers); window.municipalityLayers = null; }
            window.municipalityLabelMarkers = [];
            if (!entries || entries.length === 0) return;
            var layers = [];
            entries.forEach(function(entry, idx) {
              var color = MUNICIPALITY_COLORS[idx % MUNICIPALITY_COLORS.length];
              var geoLayer = L.geoJSON(entry.geometry, {
                interactive: false,
                style: {
                  color: shadeColor(color, -0.25), weight: 1.5, opacity: 1,
                  fill: true, fillColor: color, fillOpacity: MUNICIPALITY_FILL_OPACITY,
                  lineJoin: 'round', lineCap: 'round',
                },
              });
              layers.push(geoLayer);
              if (entry.name) {
                var labelMarker = L.marker(geoLayer.getBounds().getCenter(), {
                  icon: L.divIcon({ className: 'territory-marker', html: '<div class="municipality-name-label map-label-text">' + escapeHtml(entry.name) + '</div>' }),
                  interactive: false,
                  zIndexOffset: 50,
                });
                disableClickCapture(labelMarker);
                window.municipalityLabelMarkers.push(labelMarker);
                layers.push(labelMarker);
              }
            });
            window.municipalityLayers = L.featureGroup(layers);
            // Respects a "Municipalities" GoPreach-layer toggle the user
            // already turned off (see [window.setGoPreachLayerVisible])
            // even though this data just refreshed — the fit-bounds below
            // still uses the layer's own bounds either way, since re-
            // framing the camera is independent of whether it's drawn.
            if (window.municipalitiesVisible !== false) {
              window.municipalityLayers.addTo(map);
              window.municipalityLayers.bringToBack();
              disableClickCapture(window.municipalityLayers);
            }
            if (fit !== false) { map.fitBounds(window.municipalityLayers.getBounds().pad(0.05)); }
            refreshMunicipalityLabels();
          };
          // "PREVENT TEXT AND ICON OVERLAPPING... Do not allow municipality
          // labels to cover unrelated municipalities" — several
          // Municipalities can sit small and tightly packed together at a
          // province-wide zoom (confirmed live: Solano/Bayombong/Villaverde/
          // Ambaguio/Bagabag's own labels stacking on top of each other,
          // burying the thin sliver of each one's own color fill still
          // peeking out underneath); any label whose own screen position
          // lands within [MUNICIPALITY_LABEL_OVERLAP_PX] of one already kept
          // is hidden too — the *polygon fill itself* is never hidden by
          // this, only the text, and always the same (stable, non-flickering)
          // ones survive since [window.municipalityLabelMarkers] keeps its
          // own fixed order across every call.
          var MUNICIPALITY_LABEL_OVERLAP_PX = 70;
          // Re-evaluates every Municipality label's own visibility/font-size
          // against the *current* zoom — called from the shared `zoomend`
          // handler below.
          function refreshMunicipalityLabels() {
            var zoom = map.getZoom();
            var fontSize = scaleForZoom(zoom, MUNICIPALITY_LABEL_MIN_ZOOM, MUNICIPALITY_LABEL_MAX_ZOOM, 8, 11);
            var shownPoints = [];
            window.municipalityLabelMarkers.forEach(function(marker) {
              var el = marker.getElement();
              if (!el) return;
              var inner = el.querySelector('.municipality-name-label');
              if (!inner) return;
              if (zoom < MUNICIPALITY_LABEL_MIN_ZOOM) {
                inner.style.display = 'none';
                return;
              }
              var pt = map.latLngToContainerPoint(marker.getLatLng());
              var overlapsShown = shownPoints.some(function(shownPt) { return pt.distanceTo(shownPt) < MUNICIPALITY_LABEL_OVERLAP_PX; });
              if (overlapsShown) {
                inner.style.display = 'none';
              } else {
                shownPoints.push(pt);
                inner.style.display = '';
                inner.style.fontSize = fontSize + 'px';
              }
            });
          }

          // "Interested Person, Return Visit, and Bible Study must each have
          // unique, recognizable icons... do not use the same icon" —
          // supersedes the earlier "one shape for every record" rule: the
          // pin *silhouette* stays the same recognizable map-marker shape
          // (still colored by Congregation Group — spec's own "Record
          // Color + Icon" combination), but the small glyph inside it now
          // differs by record type instead of always being a plain white
          // dot, via [iconGlyphFor]. A selected marker still grows and turns
          // gold regardless of its own Group color, the same "which one's
          // highlighted" convention Google Maps' own default marker uses —
          // `color` is simply ignored while `selected` is true; the glyph
          // itself is unaffected by selection.
          // Leaflet tooltip content is rendered as HTML — needed now that a
          // marker's tooltip can carry two lines (a `<br>`) — so any
          // Householder/Publisher name that happens to contain HTML-special
          // characters can never break the tooltip markup or inject into it.
          function escapeHtml(text) {
            return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
          }
          function iconGlyphFor(kind) {
            switch (kind) {
              // Return Visit — a "revisit/follow-up" circular-arrow glyph.
              case 'RETURN_VISIT':
                return '<path d="M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46A7.93 7.93 0 0 0 20 13c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 8.74A7.93 7.93 0 0 0 4 13c0 4.42 3.58 8 8 8v4l5-5-5-5v4z"/>';
              // Bible Study — an open-book glyph.
              case 'BIBLE_STUDY':
                return '<path d="M12 4.5C10.4 3.4 8 2.5 6 2.5c-1.5 0-3.1.4-4.5 1.1v14.9c1.4-.6 3-1 4.5-1 2 0 4.4.9 6 2 1.6-1.1 4-2 6-2 1.5 0 3.1.4 4.5 1V3.6c-1.4-.7-3-1.1-4.5-1.1-2 0-4.4.9-6 2zm0 13.9c-1.5-.9-3.6-1.6-5.5-1.6-.9 0-1.7.1-2.5.4V5.1c.8-.3 1.6-.4 2.5-.4 1.9 0 4 .7 5.5 1.6v12.1z"/>';
              // Interested Person ("Searching") — a plain person/user glyph,
              // same recognizable silhouette as every "person" record kind
              // (Publisher/"Me" keep their own distinct looks below).
              case 'SEARCHING':
              default:
                return '<path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>';
            }
          }
          function buildPinIcon(selected, color, kind) {
            // "Make all map icons... smaller and more professional" — the
            // pin's own shape/color/glyph are unchanged (still the official
            // 3D location pin), only its on-screen size shrank.
            var w = selected ? 28 : 22, h = selected ? 39 : 31;
            var fill = selected ? '#FFC107' : (color || '#EA4335');
            var glyph = iconGlyphFor(kind);
            var html = '<svg width="' + w + '" height="' + h + '" viewBox="0 0 30 42" xmlns="http://www.w3.org/2000/svg">' +
              '<path d="M15 0C6.7 0 0 6.7 0 15c0 11 15 27 15 27s15-16 15-27C30 6.7 23.3 0 15 0z" fill="' + fill + '" stroke="#8a1c14" stroke-width="1"/>' +
              '<g transform="translate(8.5,8.5) scale(0.54)" fill="#ffffff">' + glyph + '</g></svg>';
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
              // Reverts to that marker's own Group color and record-type
              // glyph (both stashed on it at creation — see
              // `window.setPoints`), never a shared default, so deselecting
              // one Group's marker can never make it look like it belongs
              // to a different Group or a different record type.
              if (prev) prev.setIcon(prev._isMe ? buildMeIcon(false) : buildPinIcon(false, prev._color, prev._kind));
            }
            selectedMarkerId = id || null;
            if (id) {
              var current = id === 'me' ? window.myLocationMarker : markersById[id];
              if (current) current.setIcon(current._isMe ? buildMeIcon(true) : buildPinIcon(true, current._color, current._kind));
            }
          };

          // Superseded — an earlier spec forbade any clustering at all
          // ("each record should continue to display its designated icon");
          // the current spec explicitly asks for anti-overlap handling
          // instead ("marker clustering, controlled spacing, spiderfying...
          // ensure every House Holder remains individually selectable...
          // never allow one marker to completely cover another"). Every
          // marker is still rendered individually — none are ever combined
          // into a single "N records" bubble — [declutterMarkers] below just
          // nudges markers that would otherwise land on/near the exact same
          // screen pixel outward into a small spiderfied ring around their
          // shared spot, so each stays fully visible and independently
          // tappable. "You are here" stays a separate marker outside this
          // group entirely, unchanged.
          var cluster = L.layerGroup();
          var markers = [];
          var markersById = {};
          map.addLayer(cluster);

          // Greedy single-link grouping by on-screen pixel distance (not
          // real-world distance — two records genuinely far apart in meters
          // can still overlap on screen when zoomed far out, and that's
          // exactly the case this needs to catch) — any marker within
          // [SPIDERFY_PIXEL_RADIUS] px of another joins the same group.
          // Re-run on every zoom/pan (the same markers can go from
          // overlapping to comfortably apart, or vice versa, purely from a
          // zoom change) — always measured from each marker's own real,
          // unmoved [_trueLatLng], never from a previous run's already-
          // spiderfied position, so repeated runs never drift or compound.
          var SPIDERFY_PIXEL_RADIUS = 26;
          function declutterMarkers() {
            if (!markers.length) return;
            var used = new Array(markers.length).fill(false);
            var groups = [];
            for (var i = 0; i < markers.length; i++) {
              if (used[i]) continue;
              var group = [i];
              used[i] = true;
              var basePt = map.latLngToContainerPoint(markers[i]._trueLatLng);
              for (var j = i + 1; j < markers.length; j++) {
                if (used[j]) continue;
                var pt = map.latLngToContainerPoint(markers[j]._trueLatLng);
                if (basePt.distanceTo(pt) < SPIDERFY_PIXEL_RADIUS) { group.push(j); used[j] = true; }
              }
              groups.push(group);
            }
            groups.forEach(function(group) {
              if (group.length === 1) {
                markers[group[0]].setLatLng(markers[group[0]]._trueLatLng);
                return;
              }
              var centerPt = map.latLngToContainerPoint(markers[group[0]]._trueLatLng);
              var ringRadius = SPIDERFY_PIXEL_RADIUS * (group.length > 6 ? 1.7 : 1.2);
              group.forEach(function(markerIdx, k) {
                var angle = (k / group.length) * Math.PI * 2;
                var offsetPt = L.point(centerPt.x + ringRadius * Math.cos(angle), centerPt.y + ringRadius * Math.sin(angle));
                markers[markerIdx].setLatLng(map.containerPointToLatLng(offsetPt));
              });
            });
          }
          map.on('zoomend moveend', declutterMarkers);
          // "MAP ZOOM MUST NOT BREAK THE DESIGN... zoom-aware text" — every
          // zoom-responsive label (Group name/icon, Municipality name,
          // House Holder name/status/publisher) re-evaluates itself here,
          // on every zoom change; none of them depend on *pan* position (a
          // territory's own on-screen pixel size, and a marker's own zoom
          // tier, are both invariant under panning — only zooming changes
          // either), so this stays on `zoomend` alone, never `moveend`.
          // Guarded with `typeof` since this fires from a `map.on` callback
          // registered before every one of these functions is necessarily
          // defined further down the script — harmless no-ops until then.
          map.on('zoomend', function() {
            if (typeof refreshMunicipalityLabels === 'function') { refreshMunicipalityLabels(); }
            if (typeof refreshGroupLabels === 'function') { refreshGroupLabels(); }
            if (typeof refreshPipelineLabels === 'function') { refreshPipelineLabels(); }
            if (typeof refreshPublisherLabels === 'function') { refreshPublisherLabels(); }
          });

          // "RESPONSIVE MAP FILTERING... AUTOMATIC MAP ZOOM" — a single
          // point gets a comfortable street-level zoom (a 0-span "bounds"
          // around one coordinate would otherwise throw); more than one
          // fits the smallest bounds containing all of them, padded so no
          // marker lands clipped at the screen edge; zero falls back to a
          // Philippines-wide view until a fresh search (with real markers)
          // or a selected-but-empty area's own geocode repositions it.
          window.fitToMarkers = function() {
            if (markers.length === 1) {
              map.setView(markers[0].getLatLng(), 16);
            } else if (markers.length > 1) {
              map.fitBounds(L.featureGroup(markers).getBounds().pad(0.2));
            } else {
              map.setView([12.8797, 121.7740], 6);
            }
          };

          // "Show or hide the Names and other details of Interested Person/
          // Return Visit/Bible Study" — the master on/off switch; defaults
          // OFF (matching [TerritoryLiveMap]'s own `showPipelineDetails`
          // default — "uncheck Names and details on map as initial state")
          // so the map opens clean before Kotlin's own effect even has a
          // chance to push a value. Combined with
          // [PIPELINE_LABEL_MIN_ZOOM]/[PIPELINE_LABEL_FULL_ZOOM] below by
          // [refreshPipelineLabels] — both have to agree a label should show
          // (spec's own "at a far zoom level, do not display large amounts
          // of House Holder text" applies regardless of this switch's own
          // state). Never affects PUBLISHER/ME (spec names only Interested
          // Person/Return Visit/Bible Study), nor whether a marker itself is
          // shown — only its label.
          window.showPipelineLabels = false;
          // "HOUSE HOLDER LABELS... adapt to the current zoom level" — below
          // [PIPELINE_LABEL_MIN_ZOOM] no label at all (just the pin — relying
          // on [declutterMarkers]'s own spiderfy for legibility once several
          // pins sit close together); from there up to
          // [PIPELINE_LABEL_FULL_ZOOM] just "Name (Status)"; at or above
          // [PIPELINE_LABEL_FULL_ZOOM] the complete "Name (Status)" + "RP
          // <publisher>" form.
          var PIPELINE_LABEL_MIN_ZOOM = 13;
          var PIPELINE_LABEL_FULL_ZOOM = 16;
          function pipelineTooltipHtml(marker, zoom) {
            var html = escapeHtml(marker._name) + ' (' + escapeHtml(marker._status) + ')';
            if (zoom >= PIPELINE_LABEL_FULL_ZOOM && marker._publisherName) { html += '<br>RP ' + escapeHtml(marker._publisherName); }
            return html;
          }
          // Re-evaluates every pipeline marker's own label content/
          // visibility against the *current* zoom and [window.showPipelineLabels]
          // — called from the shared `zoomend` handler below, and once right
          // after `window.setPoints` rebuilds every marker.
          function refreshPipelineLabels() {
            var zoom = map.getZoom();
            markers.forEach(function(m) {
              if (!m._isPipeline || !m.getTooltip()) return;
              if (window.showPipelineLabels === false || zoom < PIPELINE_LABEL_MIN_ZOOM) {
                m.closeTooltip();
                return;
              }
              m.setTooltipContent(pipelineTooltipHtml(m, zoom));
              m.openTooltip();
            });
          }
          window.setShowPipelineLabels = function(show) {
            window.showPipelineLabels = show;
            refreshPipelineLabels();
          };

          // "Do not reload the entire map unnecessarily after every search.
          // Update only the required markers, search results, and line
          // barrier" — every search/filter change calls this instead of
          // Kotlin reloading the whole WebView page (see [TerritoryLiveMap]'s
          // own points-update effect); it clears only the marker layer
          // (never the tile layer/map instance itself) and rebuilds it from
          // the latest already-permission-filtered point set.
          window.setPoints = function(newPoints) {
            cluster.clearLayers();
            markers = [];
            markersById = {};
            newPoints.forEach(function(p) {
              var marker = L.marker([p.lat, p.lng], { icon: buildPinIcon(false, p.color, p.kind) });
              // Stashed so `window.setSelectedMarker` can revert to this
              // exact color/glyph later without needing to look `p` back up.
              marker._color = p.color;
              marker._kind = p.kind;
              // This record's own real, unmoved coordinate — see
              // [declutterMarkers]'s own doc comment for why every
              // spiderfy/de-overlap pass always measures and offsets from
              // this, never from wherever the marker's `setLatLng` last
              // displayed it.
              marker._trueLatLng = L.latLng(p.lat, p.lng);
              // "Every visible marker must display: Name (Status)" (spec §26),
              // plus — "Householder Name + Status + assigned Group Member/
              // Publisher" — a second line naming the actual Publisher
              // responsible for this record, when there is one (never for a
              // PUBLISHER/ME point, which has no assigned Publisher of its
              // own). Same "RP <name>" label the Group detail sheet/Group
              // Report already use for a Publisher, so this reads
              // consistently everywhere the app names one. Stashed on the
              // marker itself (rather than only baked into the tooltip's
              // own initial HTML) so [refreshPipelineLabels] can rebuild the
              // right zoom-tier content later without needing [p] again.
              marker._name = p.name;
              marker._status = p.status;
              marker._publisherName = p.publisherName || null;
              marker._isPipeline = p.kind === 'SEARCHING' || p.kind === 'RETURN_VISIT' || p.kind === 'BIBLE_STUDY';
              marker.bindTooltip(escapeHtml(p.name), { permanent: true, direction: 'right', offset: [10, 0], className: 'territory-label map-label-text' });
              marker.on('click', function() {
                window.setSelectedMarker(p.id);
                if (window.AndroidBridge) { AndroidBridge.showDetails(p.id); }
              });
              cluster.addLayer(marker);
              markers.push(marker);
              markersById[p.id] = marker;
            });
            // The previously-selected marker's own JS object no longer
            // exists once it's been rebuilt above; Kotlin re-applies the
            // highlight right after this call (see [TerritoryLiveMap]'s own
            // `setSelectedMarker` follow-up), so this never leaves a stale
            // reference behind in the meantime.
            selectedMarkerId = null;
            window.fitToMarkers();
            declutterMarkers();
            refreshPipelineLabels();
          };
          window.setPoints([]);

          // "Add the user current location in the map view" — a distinct
          // dot, outside the cluster group (always visible), added once
          // Android actually has a GPS fix.
          window.setMyLocation = function(lat, lng) {
            if (window.myLocationMarker) { map.removeLayer(window.myLocationMarker); }
            window.myLocationMarker = L.marker([lat, lng], { icon: buildMeIcon(selectedMarkerId === 'me'), zIndexOffset: 1000 }).addTo(map);
            window.myLocationMarker._isMe = true;
            window.myLocationMarker.bindTooltip('My Location', { permanent: true, direction: 'right', offset: [10, 0], className: 'territory-label map-label-text' });
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
          // circle around the area's geocoded center instead, sized to
          // roughly the area's real footprint (Barangay vs. Municipality get
          // different Kotlin-supplied radii — see [selectedAreaQuery]'s own
          // zoom levels), which is disclosed to the user as an approximate
          // area indicator, not a true administrative boundary.
          //
          // "Enhance the line barrier design... professional red color...
          // thicker and wider... clean, polished, professionally drawn
          // appearance, similar to a high-quality GIS/map boundary... subtle
          // visual definition, such as a slightly darker red edge" — every
          // boundary kind below (circle, real polygon, convex-hull scope)
          // now shares one cartographic "line casing" style: a wider, darker
          // red stroke drawn first, with a narrower, brighter red stroke on
          // top of it — the same technique styled maps use for a road's own
          // outlined casing — giving the line real edge definition instead
          // of a single flat stroke, while `weight` stays constant in screen
          // pixels (Leaflet's own behavior) so it reads the same, undiminished,
          // at every zoom level rather than thinning out when zoomed out.
          // `L.featureGroup` (not a plain `L.layerGroup`) so `.getBounds()`
          // still works for camera-fitting exactly like the single-layer
          // version this replaces. `lineJoin`/`lineCap: 'round'` avoids sharp,
          // jagged corners at the polygon's own vertices; SVG rendering
          // (Leaflet's default, unchanged) keeps every edge crisp at any
          // zoom rather than a rasterized/pixelated line.
          var BOUNDARY_CASING_COLOR = '#7A0E0E';
          var BOUNDARY_MAIN_COLOR = '#E53935';
          var BOUNDARY_CASING_WEIGHT = 9;
          var BOUNDARY_MAIN_WEIGHT = 4.5;
          var BOUNDARY_FILL_OPACITY = 0.10;
          // See [window.setAreaBoundaryGeoJson]'s own doc comment — a floor
          // (never more zoomed-out than this) and a ceiling (never more
          // zoomed-in than this) on the camera zoom a single explicit
          // Municipality/Barangay selection can land at, regardless of how
          // large or small that area's own bundled bounding box happens to
          // be.
          var AREA_FOCUS_MIN_ZOOM = 12;
          var AREA_FOCUS_MAX_ZOOM = 16;
          // "Never use a generic square, rectangle, circle, or rounded
          // shape to represent an actual geographic territory" — the
          // minimum real-world radius (meters) a lone point (or a tight
          // 2-point cluster) gets, via [smallTerritoryPolygon]'s own
          // deliberately-irregular shape, never a geometric primitive.
          var SMALL_TERRITORY_HALF_WIDTH_METERS = 150;
          // Bug fix (confirmed live: tapping directly inside a Group's own
          // solid territory fill never opened its info panel — logging
          // showed the tap landing as a plain, unattributed map click
          // instead of this layer's own bound click handler) — Leaflet's
          // `interactive: false` option only skips *Leaflet's own* internal
          // event wiring for a layer; it does nothing to the browser's own
          // native SVG hit-testing, which by default still treats any
          // painted (filled or stroked) `<path>` as opaque to pointer
          // events regardless of what Leaflet calls it. The Municipality
          // boundary's own solid fill sits deliberately *above* every Group
          // layer (`window.areaBoundary.bringToFront()`, "the Municipality
          // boundary must remain visible") and covers the same real
          // territory a Group's own shape sits inside of — so a tap that
          // visually lands inside a Group's own fill was actually
          // physically hitting the *Municipality* fill's painted pixels
          // sitting on top of it, which absorbs the click and lets it
          // bubble straight up to the map's own (unrelated) click handler
          // instead of ever reaching the Group polygon underneath. Applied
          // to every genuinely non-interactive path layer (the Municipality
          // boundary casing+fill, the province-wide Municipality fills, and
          // each Group's own outline-only casing layer) right after it's
          // added to the map, so all of them become purely visual and let
          // clicks fall through to whatever's actually meant to receive them.
          function disableClickCapture(layer) {
            if (layer.eachLayer) { layer.eachLayer(disableClickCapture); return; }
            var apply = function() {
              // `getElement()` only exists on L.Marker/L.ImageOverlay — a
              // Path (polygon/circle, what every layer this is actually
              // used on really is) keeps its live SVG `<path>` element on
              // the private `_path` property instead; there's no public
              // accessor for it. Confirmed live: without this fallback,
              // `getElement` is simply `undefined` on every Path layer, so
              // `apply()` silently did nothing at all for exactly the
              // layers that needed it.
              var el = (layer.getElement && layer.getElement()) || layer._path || null;
              if (el) { el.style.pointerEvents = 'none'; }
            };
            if (layer._map) { apply(); } else { layer.once('add', apply); }
          }
          function boundaryCasingStyle() {
            return { color: BOUNDARY_CASING_COLOR, weight: BOUNDARY_CASING_WEIGHT, opacity: 0.9, fill: false, lineJoin: 'round', lineCap: 'round', interactive: false };
          }
          function boundaryMainStyle() {
            return { color: BOUNDARY_MAIN_COLOR, weight: BOUNDARY_MAIN_WEIGHT, opacity: 1, fill: true, fillColor: BOUNDARY_MAIN_COLOR, fillOpacity: BOUNDARY_FILL_OPACITY, lineJoin: 'round', lineCap: 'round', interactive: false };
          }
          window.areaBoundary = null;
          window.setAreaBoundary = function(lat, lng, radiusMeters) {
            window.clearAreaBoundary();
            var center = [lat, lng];
            window.areaBoundary = L.featureGroup([
              L.circle(center, Object.assign({ radius: radiusMeters }, boundaryCasingStyle())),
              L.circle(center, Object.assign({ radius: radiusMeters }, boundaryMainStyle())),
            ]).addTo(map);
            disableClickCapture(window.areaBoundary);
          };
          // The real thing: an actual Municipality/Barangay polygon (from
          // [TerritoryBoundaryRepository]'s bundled NAMRIA/PSA/OCHA boundary
          // data), drawn the same casing+main red style as the circle
          // fallback above so a real boundary and an approximate one never
          // look meaningfully different to the user.
          // "The Municipality barrier must be shown always" — [fit] is
          // false for the implicit single-Municipality fallback
          // ([TerritoryMapScreen]'s own `explicitAreaSelection` doc
          // comment): the boundary still draws, just without the camera
          // jump below, so a plain map open (nothing searched) can't get
          // pulled away from the Province-wide initial view this map
          // otherwise starts on.
          // [maxZoom] — optional, defaults to [AREA_FOCUS_MAX_ZOOM]. Added
          // for the congregation's own initial-focus fit (see
          // [TerritoryMapScreen]'s `initialFocusMunicipality` effect): on a
          // wide-viewport device (a landscape tablet especially),
          // `getBoundsZoom` legitimately computes a much higher zoom to fit
          // the exact same real-world bounding box than it would on a
          // narrow phone screen — confirmed live, a genuine ~0.11°x0.16°
          // Municipality (Solano) landed at [AREA_FOCUS_MAX_ZOOM]'s ceiling
          // (16) on a tablet, close enough to fill the entire screen with
          // just one solid-color edge, nothing recognizable as "a whole
          // Municipality" left in view. [AREA_FOCUS_MIN_ZOOM] (12) was
          // already established by that same earlier fix as the zoom a
          // "focus on this one Municipality" view should land at — a
          // whole-Municipality fit's own ceiling is capped much closer to
          // that floor (see call site) rather than sharing barangay-level
          // search's own, deliberately closer, 16.
          window.setAreaBoundaryGeoJson = function(geometry, fit, maxZoom) {
            window.clearAreaBoundary();
            // "interactive" is a GeoJSON-layer-level constructor option, not
            // a Path style property — it has to sit alongside `style`, not
            // inside it, or Leaflet silently ignores it and the boundary
            // would wrongly intercept taps meant for a marker underneath it.
            var main = L.geoJSON(geometry, { style: boundaryMainStyle(), interactive: false });
            window.areaBoundary = L.featureGroup([
              L.geoJSON(geometry, { style: boundaryCasingStyle(), interactive: false }),
              main,
            ]).addTo(map);
            disableClickCapture(window.areaBoundary);
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
            //
            // Bug fix ("calibrate the territory map... wrong zoom/scale,"
            // reported live for Diadi) — a plain `fitBounds` has no floor:
            // some Municipalities in the bundled boundary asset (see
            // [TerritoryBoundaryRepository]'s own doc comment on where this
            // data actually comes from) carry a noticeably larger bounding
            // box than that Municipality's own real, compact footprint —
            // Diadi's own bundled polygon spans roughly 0.16°x0.24°, nearly
            // triple its real extent, confirmed live against OpenStreetMap's
            // own data for the town. Plain `fitBounds` on a bounding box
            // that size lands the camera far more zoomed out than "focus on
            // this one Municipality" should ever look — `getBoundsZoom`
            // computes what zoom that fit would actually produce, and
            // [AREA_FOCUS_MIN_ZOOM] refuses to land any more zoomed-out
            // than a single Municipality/Barangay selection should
            // reasonably be, regardless of how much larger the bundled
            // polygon's own bounding box happens to be than its real
            // footprint — a real, if imperfect, safety floor rather than
            // trying to fix the bundled geometry itself (not something this
            // app can regenerate on-device).
            if (fit !== false) {
              var bounds = main.getBounds().pad(0.15);
              var zoom = Math.max(map.getBoundsZoom(bounds), AREA_FOCUS_MIN_ZOOM);
              map.setView(bounds.getCenter(), Math.min(zoom, maxZoom || AREA_FOCUS_MAX_ZOOM));
            }
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
          // Same casing+main red style as the two boundary kinds above so
          // none of the three ever look meaningfully different to the user.
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
          // Darkens (negative percent) or lightens (positive) a "#RRGGBB"
          // string by blending each channel toward black/white — used to
          // derive a Group's own casing shade directly from its *own*
          // dynamically-generated color (see Kotlin's own `colorForGroupId`),
          // rather than a single hardcoded casing color that would only ever
          // suit one specific hue.
          function shadeColor(hex, percent) {
            var num = parseInt(hex.replace('#', ''), 16);
            var r = Math.max(0, Math.min(255, (num >> 16) + Math.round(255 * percent)));
            var g = Math.max(0, Math.min(255, ((num >> 8) & 0x00FF) + Math.round(255 * percent)));
            var b = Math.max(0, Math.min(255, (num & 0x0000FF) + Math.round(255 * percent)));
            return '#' + (0x1000000 + r * 0x10000 + g * 0x100 + b).toString(16).slice(1);
          }
          function dedupePoints(pts) {
            var unique = [];
            var seen = {};
            pts.forEach(function(p) {
              var key = p[0] + ',' + p[1];
              if (!seen[key]) { seen[key] = true; unique.push(p); }
            });
            return unique;
          }
          // "MAP-RESPONSIVE TEXT AND DETAILS... map-aware/scaled labels,
          // rather than fixed-size UI elements" — a plain linear interpolation
          // between [minVal] (at [minZoom] or below) and [maxVal] (at
          // [maxZoom] or above), shared by every zoom-responsive label below
          // (Group name, Municipality name) so they all scale by the same
          // rule rather than each inventing its own math.
          function scaleForZoom(zoom, minZoom, maxZoom, minVal, maxVal) {
            var t = maxZoom > minZoom ? (zoom - minZoom) / (maxZoom - minZoom) : 1;
            t = Math.max(0, Math.min(1, t));
            return minVal + (maxVal - minVal) * t;
          }
          // A deliberately irregular (never square/rectangular/circular)
          // small polygon around a point or tiny cluster with no real
          // polygon of its own to draw (see [buildGroupBoundaryLayer]'s own
          // doc comment for why this case exists at all) — vertices at
          // uneven radii/angles, deterministic per exact coordinate (so the
          // same location always draws the same shape across reloads,
          // "keep color/shape assignments consistent... whenever possible"),
          // rather than a perfect geometric primitive standing in for a
          // location this app has no real surveyed boundary for.
          function smallIrregularPolygon(centerLat, centerLng, radiusMeters) {
            var vertices = [];
            var sides = 7;
            var cosLat = Math.cos(centerLat * Math.PI / 180);
            var cosLatSafe = Math.abs(cosLat) > 0.01 ? cosLat : 0.01;
            for (var i = 0; i < sides; i++) {
              var angle = (i / sides) * Math.PI * 2;
              var jitter = 0.65 + 0.35 * Math.abs(Math.sin(i * 12.9898 + centerLat * 78.233 + centerLng * 37.719));
              var r = radiusMeters * jitter;
              var dLat = (r * Math.cos(angle)) / 111320;
              var dLng = (r * Math.sin(angle)) / (111320 * cosLatSafe);
              vertices.push([centerLat + dLat, centerLng + dLng]);
            }
            return vertices;
          }
          // Same idea, sized to actually cover every one of [points] (never
          // smaller than [SMALL_TERRITORY_HALF_WIDTH_METERS], so it stays a
          // real, visible area rather than shrinking to nothing for two
          // near-identical coordinates).
          function smallTerritoryPolygon(points) {
            var lats = points.map(function(p) { return p[0]; });
            var lngs = points.map(function(p) { return p[1]; });
            var centerLat = (Math.min.apply(null, lats) + Math.max.apply(null, lats)) / 2;
            var centerLng = (Math.min.apply(null, lngs) + Math.max.apply(null, lngs)) / 2;
            var center = L.latLng(centerLat, centerLng);
            var maxDist = 0;
            points.forEach(function(p) { maxDist = Math.max(maxDist, center.distanceTo(L.latLng(p[0], p[1]))); });
            var radius = Math.max(SMALL_TERRITORY_HALF_WIDTH_METERS, maxDist * 1.3);
            return smallIrregularPolygon(centerLat, centerLng, radius);
          }
          // "PROFESSIONAL TERRITORY GROUP ICON... same color assigned to
          // that Territory Group" — a plain white flag glyph (Material
          // Design's own "flag" icon path), simple enough to stay
          // recognizable at every zoom rather than degrading into a smudge
          // once shrunk; solid-filled onto [.group-badge]'s own colored
          // circle (never the glyph itself recolored), so it always reads
          // clearly against light or dark base-map tiles alike.
          var GROUP_BADGE_SVG = '<svg viewBox="0 0 24 24" width="8" height="8"><path fill="#ffffff" d="M14.4 6L14 4H5v17h2v-7h5.6l.4 2h7V6z"/></svg>';
          // "GROUP TEXT MUST FIT TO MAP... ZOOM-DEPENDENT GROUP LABELS" —
          // below [GROUP_LABEL_MIN_ZOOM] only the icon badge shows at all
          // (a territory this small on screen has no room for readable
          // text without it spilling outside its own shape); between that
          // and [GROUP_LABEL_FULL_ZOOM] the *short* form of the name shows
          // (see [shortGroupName]); at or above [GROUP_LABEL_FULL_ZOOM] the
          // complete name shows. The same tiers apply again, independently,
          // whenever the territory's own on-screen pixel width is too
          // narrow for its current tier's text (checked directly in
          // [groupLabelHtml]) — a huge Group's territory still gets the
          // short form at a middling zoom if its own shape is unusually
          // narrow, and vice versa.
          var GROUP_LABEL_MIN_ZOOM = 10;
          var GROUP_LABEL_FULL_ZOOM = 15;
          var GROUP_LABEL_MIN_BOX_PX = 34;
          var GROUP_LABEL_FULL_BOX_PX = 110;
          // A Group's own [name] is already short in this app ("Group 5") —
          // but the same "GROUP 1 – BAYOMBONG EAST" -> "GROUP 1" shrink the
          // spec itself asks for is supported for whenever a longer,
          // hyphen-separated name exists (a future "Group 1 - Bayombong"
          // naming convention, say): only the part before the first " - "/
          // " – " survives the short form.
          function shortGroupName(name) {
            var sep = /\s[-–]\s/;
            return sep.test(name) ? name.split(sep)[0] : name;
          }
          function groupLabelHtml(name, color, zoom, bounds) {
            var badge = '<div class="group-badge" style="background:' + color + ';">' + GROUP_BADGE_SVG + '</div>';
            var nw = map.latLngToContainerPoint(bounds.getNorthWest());
            var se = map.latLngToContainerPoint(bounds.getSouthEast());
            var boxWidth = Math.abs(se.x - nw.x);
            if (zoom < GROUP_LABEL_MIN_ZOOM || boxWidth < GROUP_LABEL_MIN_BOX_PX) {
              return '<div class="group-label-wrap">' + badge + '</div>';
            }
            var fontSize = scaleForZoom(zoom, GROUP_LABEL_MIN_ZOOM, GROUP_LABEL_FULL_ZOOM, 9, 13);
            var showFull = zoom >= GROUP_LABEL_FULL_ZOOM && boxWidth >= GROUP_LABEL_FULL_BOX_PX;
            var displayName = showFull ? name : shortGroupName(name);
            return '<div class="group-label-wrap">' + badge +
              '<div class="group-name-label map-label-text" style="font-size:' + fontSize + 'px;">' +
              escapeHtml(displayName) + '</div></div>';
          }
          // Re-evaluates every currently-drawn Group's own label (icon-only
          // vs. short vs. full name, and its own font-size) against the
          // *current* zoom — called from the shared `zoomend` handler below,
          // never on `moveend` (a Group's own territory bounds don't change
          // on-screen pixel *width* from panning, only from zooming).
          function refreshGroupLabels() {
            var zoom = map.getZoom();
            Object.keys(window.groupLabelMarkers).forEach(function(gid) {
              var marker = window.groupLabelMarkers[gid];
              if (!map.hasLayer(marker)) return;
              marker.setIcon(L.divIcon({ className: 'territory-marker', html: groupLabelHtml(marker._groupName, marker._groupColor, zoom, marker._groupBounds) }));
            });
          }
          // Bug fix ("I cannot find the Group Territory Map, and the
          // Publishers Territory now in the Map View... show the map that
          // is working before") — the marker-only rendering this replaced
          // (see this file's own git history) made a Group's territory
          // nearly invisible against a solid, much-larger Municipality
          // fill: a small badge sitting on top of a huge color block reads
          // as just another household pin, not as its own distinct
          // territory. Restored to a real, visible filled shape — the
          // tightest real polygon around this Group's own points (a
          // genuine convex hull of real, congregation-assigned record
          // coordinates, proportional to their actual spread — the closest
          // thing to "the actual territory" derivable from real data when
          // no surveyed Field Service Group boundary dataset exists), with
          // the same color-coded marker + name label as before still
          // centered on it.
          var GROUP_FILL_OPACITY = 1.0;
          function buildGroupBoundaryLayer(points, color, groupId, name) {
            var casingStyle = { color: shadeColor(color, -0.35), weight: BOUNDARY_CASING_WEIGHT, opacity: 1, fill: false, lineJoin: 'round', lineCap: 'round', interactive: false };
            var mainStyle = { color: color, weight: BOUNDARY_MAIN_WEIGHT, opacity: 1, fill: true, fillColor: color, fillOpacity: GROUP_FILL_OPACITY, lineJoin: 'round', lineCap: 'round', interactive: true };
            var casingLayer, mainLayer;
            if (points.length === 1) {
              var soloHull = smallTerritoryPolygon(points);
              casingLayer = L.polygon(soloHull, casingStyle);
              mainLayer = L.polygon(soloHull, mainStyle);
            } else {
              var unique = dedupePoints(points);
              var hull = unique.length >= 3 ? convexHull(unique) : unique;
              if (hull.length < 3) {
                var smallHull = smallTerritoryPolygon(unique);
                casingLayer = L.polygon(smallHull, casingStyle);
                mainLayer = L.polygon(smallHull, mainStyle);
              } else {
                casingLayer = L.polygon(hull, casingStyle);
                mainLayer = L.polygon(hull, mainStyle);
              }
            }
            disableClickCapture(casingLayer);
            if (groupId) {
              mainLayer.on('click', function(e) {
                if (window.AndroidBridge) { AndroidBridge.showGroupDetails(groupId); }
                L.DomEvent.stopPropagation(e);
              });
            }
            var layers = [casingLayer, mainLayer];
            if (name) {
              var labelMarker = L.marker(mainLayer.getBounds().getCenter(), {
                icon: L.divIcon({ className: 'territory-marker', html: groupLabelHtml(name, color, map.getZoom(), mainLayer.getBounds()) }),
                interactive: false,
                zIndexOffset: 500,
              });
              labelMarker._groupName = name;
              labelMarker._groupColor = color;
              labelMarker._groupBounds = mainLayer.getBounds();
              disableClickCapture(labelMarker);
              if (groupId) { window.groupLabelMarkers[groupId] = labelMarker; }
              layers.push(labelMarker);
            }
            return L.featureGroup(layers);
          }
          // Rough "how big is this Group's own footprint" heuristic (a
          // plain bounding-box area, not a true geodesic one — only ever
          // used to *order* Groups relative to each other, so the
          // approximation only needs to be consistent, not precise) —
          // "the parent group's territory must remain clearly visible
          // around [a nested/smaller territory]... never hide one group's
          // barrier behind another" (spec §4/§8): drawing larger Groups
          // first and smaller ones last means a small/nested Group's own
          // barrier and fill always land on top of a larger Group's, in
          // the same SVG stacking order Leaflet already draws layers in —
          // never buried underneath it regardless of which Group happened
          // to be selected/loaded first.
          function boundsFootprintArea(points) {
            if (!points || points.length === 0) return 0;
            var minLat = points[0][0], maxLat = points[0][0], minLng = points[0][1], maxLng = points[0][1];
            for (var i = 1; i < points.length; i++) {
              minLat = Math.min(minLat, points[i][0]); maxLat = Math.max(maxLat, points[i][0]);
              minLng = Math.min(minLng, points[i][1]); maxLng = Math.max(maxLng, points[i][1]);
            }
            return (maxLat - minLat) * (maxLng - minLng);
          }
          // "Create a separate barrier line for every Congregation Group...
          // do not merge different groups into one barrier... Neighboring or
          // overlapping territories must remain visually distinguishable" —
          // one independent, color-coded layer per Group (keyed by
          // `entry.id`, dynamically however many entries Kotlin sends — 2,
          // 5, 10+, no fixed limit), living entirely apart from
          // `window.areaBoundary` above (the single, always-red real
          // Municipality/Barangay administrative outline), so both can be
          // visible together without either one clobbering the other.
          // `fitCamera` is false whenever `window.areaBoundary` already
          // framed the camera to a real administrative polygon this same
          // update (see [TerritoryLiveMap]'s own points-update effect) — a
          // deliberate "focus on this exact area" action must never be
          // immediately overridden by fitting to every Group's combined
          // bounds instead.
          window.groupBoundaryLayers = {};
          window.setGroupScopeBoundaries = function(entries, fitCamera) {
            var keepIds = {};
            (entries || []).forEach(function(entry) { keepIds[entry.id] = true; });
            Object.keys(window.groupBoundaryLayers).forEach(function(id) {
              if (!keepIds[id]) {
                map.removeLayer(window.groupBoundaryLayers[id]);
                delete window.groupBoundaryLayers[id];
                delete window.groupLabelMarkers[id];
              }
            });
            var allPoints = [];
            // Largest footprint first, smallest/nested last — see
            // [boundsFootprintArea]'s own doc comment for why draw order
            // (not just opacity) is what actually keeps a small/nested
            // Group's own barrier from ever landing underneath a larger
            // one's.
            var ordered = (entries || []).slice().sort(function(a, b) {
              return boundsFootprintArea(b.points) - boundsFootprintArea(a.points);
            });
            ordered.forEach(function(entry) {
              if (window.groupBoundaryLayers[entry.id]) {
                map.removeLayer(window.groupBoundaryLayers[entry.id]);
                delete window.groupBoundaryLayers[entry.id];
                delete window.groupLabelMarkers[entry.id];
              }
              if (entry.points) { entry.points.forEach(function(p) { allPoints.push(p); }); }
              // "Do not create an artificially large territory based on the
              // distance between two Householders" — always the tight
              // point-hull shape, proportional to this Group's own actual
              // records, never a whole administrative unit standing in for
              // it (see [buildGroupBoundaryLayer]'s own doc comment).
              var layer = entry.points && entry.points.length > 0 ? buildGroupBoundaryLayer(entry.points, entry.color, entry.id, entry.name) : null;
              if (!layer) { return; }
              window.groupBoundaryLayers[entry.id] = layer;
              // Respects a "Territory Groups" GoPreach-layer toggle already
              // turned off (see [window.setGoPreachLayerVisible]) even
              // though this data just refreshed.
              if (window.groupLayersVisible !== false) { layer.addTo(map); }
            });
            if (fitCamera && allPoints.length > 0) {
              map.fitBounds(L.latLngBounds(allPoints).pad(0.2));
            }
          };
          window.clearGroupScopeBoundaries = function() {
            Object.keys(window.groupBoundaryLayers).forEach(function(id) { map.removeLayer(window.groupBoundaryLayers[id]); });
            window.groupBoundaryLayers = {};
            window.groupLabelMarkers = {};
          };

          // "GROUP TERRITORY & PUBLISHER TERRITORY DIVISION" — once a Group
          // is selected, subdivides its own territory into each Publisher's
          // own real assigned records (spec §1's own "visualization of the
          // existing assignments," never a new database record).
          // "PUBLISHER TERRITORY – NO DIFFERENT COLORS" superseded the
          // earlier per-Publisher color-coded-fill idea — every Publisher
          // now shares the parent Group's own single color and (see
          // `buildPublisherBoundaryLayer`'s `mainStyle`) has effectively no
          // fill at all; the two layers still read as distinct purely
          // through the Publisher's own boundary/divider line and name
          // label, drawn on top of the Group's own fill.
          var PUBLISHER_LABEL_MIN_ZOOM = 11;
          var PUBLISHER_LABEL_FULL_ZOOM = 15;
          var PUBLISHER_BADGE_SVG = '<svg viewBox="0 0 24 24" width="7" height="7"><path fill="#ffffff" d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>';
          // Same "icon-only vs. short vs. full" zoom-tier idea
          // [groupLabelHtml] already uses, one size down; "RP <name>" is the
          // same Publisher-naming convention every other label on this map
          // (marker tooltips, the Group Report table) already reads.
          function publisherLabelHtml(name, color, zoom, bounds) {
            var badge = '<div class="publisher-badge" style="background:' + color + ';">' + PUBLISHER_BADGE_SVG + '</div>';
            var nw = map.latLngToContainerPoint(bounds.getNorthWest());
            var se = map.latLngToContainerPoint(bounds.getSouthEast());
            var boxWidth = Math.abs(se.x - nw.x);
            if (zoom < PUBLISHER_LABEL_MIN_ZOOM || boxWidth < 26) {
              return '<div class="publisher-label-wrap">' + badge + '</div>';
            }
            var fontSize = scaleForZoom(zoom, PUBLISHER_LABEL_MIN_ZOOM, PUBLISHER_LABEL_FULL_ZOOM, 8, 10);
            return '<div class="publisher-label-wrap">' + badge +
              '<div class="publisher-name-label map-label-text" style="font-size:' + fontSize + 'px;">RP ' +
              escapeHtml(name) + '</div></div>';
          }
          function refreshPublisherLabels() {
            var zoom = map.getZoom();
            Object.keys(window.publisherLabelMarkers).forEach(function(pid) {
              var marker = window.publisherLabelMarkers[pid];
              if (!map.hasLayer(marker)) return;
              marker.setIcon(L.divIcon({ className: 'territory-marker', html: publisherLabelHtml(marker._pubName, marker._pubColor, zoom, marker._pubBounds) }));
            });
          }
          // Bug fix ("I cannot find... the Publishers Territory now in the
          // Map View... show the map that is working before") — same
          // restore as [buildGroupBoundaryLayer] above, one level down: a
          // real, visible filled shape instead of a marker-only badge that
          // read as invisible against the map underneath it. Still every
          // Publisher's own single [color] is the parent Group's own (see
          // [TerritoryMapScreen]'s `selectedGroupPublisherBoundaries`) —
          // "no different colors for individual Publishers" stays true;
          // Publishers are only distinguished from each other by their own
          // shape/position and name label, never by a different fill color.
          var PUBLISHER_FILL_OPACITY = 1.0;
          function buildPublisherBoundaryLayer(points, color, publisherId, name) {
            var casingStyle = { color: shadeColor(color, -0.35), weight: 3, opacity: 1, fill: false, lineJoin: 'round', lineCap: 'round', interactive: false };
            var mainStyle = { color: color, weight: 2.5, opacity: 1, fill: true, fillColor: color, fillOpacity: PUBLISHER_FILL_OPACITY, lineJoin: 'round', lineCap: 'round', interactive: true };
            var casingLayer, mainLayer;
            if (points.length === 1) {
              var soloHull = smallTerritoryPolygon(points);
              casingLayer = L.polygon(soloHull, casingStyle);
              mainLayer = L.polygon(soloHull, mainStyle);
            } else {
              var unique = dedupePoints(points);
              var hull = unique.length >= 3 ? convexHull(unique) : unique;
              if (hull.length < 3) {
                var smallHull = smallTerritoryPolygon(unique);
                casingLayer = L.polygon(smallHull, casingStyle);
                mainLayer = L.polygon(smallHull, mainStyle);
              } else {
                casingLayer = L.polygon(hull, casingStyle);
                mainLayer = L.polygon(hull, mainStyle);
              }
            }
            disableClickCapture(casingLayer);
            // "Clicking a Publisher Territory" (spec §6).
            mainLayer.on('click', function(e) {
              if (window.AndroidBridge) { AndroidBridge.showPublisherDetails(publisherId); }
              L.DomEvent.stopPropagation(e);
            });
            var layers = [casingLayer, mainLayer];
            if (name) {
              var labelMarker = L.marker(mainLayer.getBounds().getCenter(), {
                icon: L.divIcon({ className: 'territory-marker', html: publisherLabelHtml(name, color, map.getZoom(), mainLayer.getBounds()) }),
                interactive: false,
                zIndexOffset: 700,
              });
              labelMarker._pubName = name;
              labelMarker._pubColor = color;
              labelMarker._pubBounds = mainLayer.getBounds();
              disableClickCapture(labelMarker);
              window.publisherLabelMarkers[publisherId] = labelMarker;
              layers.push(labelMarker);
            }
            return L.featureGroup(layers);
          }
          // [groupId]'s own boundary layer (already drawn by
          // `setGroupScopeBoundaries`) is dimmed to a subtle background the
          // moment its own Publisher breakdown is showing — "Group
          // Territory... subtle group-level boundary or background
          // indication" (spec §4) — and the camera fits to that same
          // Group's own bounds ("Automatically fit the selected Group
          // Territory to the map when it is opened," spec §7).
          // "Do not automatically reduce the opacity of any color" — the
          // parent Group's own boundary is no longer dimmed while its own
          // Publisher breakdown is showing (it used to drop to 12% fill);
          // both stay fully opaque, and a Publisher's own subdivision
          // simply paints over the Group's own color wherever the two
          // overlap because it's added to the map *after* (see the
          // `ordered.forEach` below), the same "later = on top" SVG
          // stacking every other layer on this map already relies on — the
          // Group's own full color still shows through everywhere a
          // Publisher's own subdivision doesn't reach.
          window.setPublisherScopeBoundaries = function(groupId, entries) {
            window.clearPublisherScopeBoundaries();
            window.publisherSubdividedGroupId = groupId;
            // Bug fix: [window.groupBoundaryLayers[groupId]] is now just
            // that Group's own single centroid marker (see
            // [buildGroupBoundaryLayer]'s own doc comment — no more
            // territory polygon to derive real bounds from), so its own
            // `getBounds()` collapsed to one point and zoomed the camera in
            // far tighter than "fit to every Publisher's own records"
            // should — confirmed live: every Publisher marker ended up
            // outside the viewport. Real bounds come from every entry's own
            // points instead (the same real records this call is about to
            // draw markers for), same as the Province-wide/Group-wide fits
            // elsewhere on this screen already do.
            var allEntryPoints = [];
            (entries || []).forEach(function(entry) {
              (entry.points || []).forEach(function(p) { allEntryPoints.push(L.latLng(p[0], p[1])); });
            });
            if (allEntryPoints.length > 0) {
              map.fitBounds(L.latLngBounds(allEntryPoints).pad(0.35));
            }
            var ordered = (entries || []).slice().sort(function(a, b) {
              return boundsFootprintArea(b.points) - boundsFootprintArea(a.points);
            });
            ordered.forEach(function(entry) {
              if (!entry.points || entry.points.length === 0) return;
              var layer = buildPublisherBoundaryLayer(entry.points, entry.color, entry.id, entry.name);
              window.publisherBoundaryLayers[entry.id] = layer.addTo(map);
            });
          };
          window.clearPublisherScopeBoundaries = function() {
            Object.keys(window.publisherBoundaryLayers).forEach(function(id) { map.removeLayer(window.publisherBoundaryLayers[id]); });
            window.publisherBoundaryLayers = {};
            window.publisherLabelMarkers = {};
            window.publisherSubdividedGroupId = null;
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
            // "Use the color code of the icons of Interested Person/Return
            // Visit/Bible Study based on the congregation Group color" — a
            // pipeline point's own icon now uses its actual assigned
            // Group's color ([MapPoint.groupColor], the exact same color
            // its map pin/territory shape already use), never a fixed
            // per-status color; Publisher/"Me" keep [markerColorFor]'s own
            // fixed colors, since neither belongs to a Congregation Group
            // the same way.
            val iconColor = if (isPipelinePoint) parseHexColor(point.groupColor) else markerColorFor(point.kind)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(iconColor),
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
                    // "Clearly show which records... belong to which
                    // [Congregation Group]" — the marker's own fill color
                    // already does this at a glance; named here too for
                    // anyone who can't rely on color alone.
                    point.groupName?.let { DetailRow(label = "Congregation Group", value = it) }
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

/** "Territory Group Information" (spec §11) — Group Name + Icon (both in
 * the Group's own assigned color, spec §5/§14's own "consistency"
 * requirement), Publishers/Interested Persons/Bible Studies counts, shown
 * when a Group's own territory fill is tapped on the map (see
 * [TerritoryLiveMap]'s own `selectedGroupId`/`AndroidBridge.showGroupDetails`).
 * Same [ModalBottomSheet] convention [MapPointDetailsSheet] already uses,
 * so both feel like the same app rather than two different UI patterns. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInfoSheet(
    info: GroupReportRow,
    onDismiss: () -> Unit,
    onClearFilter: () -> Unit,
    // "Publisher Territories must remain hidden by default... SHOW
    // PUBLISHER TERRITORY... HIDE THE PUBLISHERS TERRITORY" (spec §2/§3/§8) —
    // this sheet only ever *reads* the current shown/hidden state and asks
    // to toggle it; [TerritoryLiveMap] owns the actual state and everything
    // that depends on it (which layer gets pushed to the map).
    showPublisherTerritories: Boolean,
    onToggleShowPublisherTerritories: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val groupColor = parseHexColor(info.color)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(groupColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Groups, contentDescription = null, tint = Color.White)
                }
                Column {
                    Text(info.groupName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = groupColor)
                    Text("Congregation Group Territory", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // "Hide/collapse the normal Group Territory detail information
            // as appropriate" (spec §3) once Publisher Territories are
            // actually showing — the map itself is already the detailed
            // view at that point; the sheet shrinks to just the toggle
            // button so it covers as little of the map as possible.
            if (!showPublisherTerritories) {
                if (info.municipalities.isNotEmpty()) {
                    DetailRow(label = "Municipality", value = info.municipalities.joinToString(", "))
                }
                if (info.barangays.isNotEmpty()) {
                    DetailRow(label = "Barangay", value = info.barangays.joinToString(", "))
                }
                DetailRow(label = "House Holders", value = info.houseHolderCount.toString())
                DetailRow(label = "Publishers", value = info.publishers.toString())
                // "Show the names of the group publishers if the Territory
                // Group is clicked" — every distinct Publisher assigned to a
                // record in this Group, listed right under the count above.
                if (info.publisherNames.isNotEmpty()) {
                    Text(
                        info.publisherNames.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                DetailRow(label = "Interested Persons", value = info.interestedPersons.toString())
                DetailRow(label = "Return Visits", value = info.returnVisits.toString())
                DetailRow(label = "Bible Studies", value = info.bibleStudies.toString())
            }
            // "Do not display Publisher Territories automatically when the
            // Group Territory is first clicked. They must only appear after
            // the user explicitly selects SHOW PUBLISHER TERRITORY."
            Button(onClick = onToggleShowPublisherTerritories, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(if (showPublisherTerritories) "HIDE THE PUBLISHERS TERRITORY" else "SHOW PUBLISHER TERRITORY")
            }
            // "Keep the other territories visible in the background" — this
            // only ever narrows which House Holder markers are drawn (see
            // [TerritoryLiveMap]'s own `visiblePoints`), never which Group
            // territory *shapes* are drawn; this button is the one explicit
            // way back to seeing every Group's own House Holders again.
            OutlinedButton(onClick = onClearFilter, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Show All Territories")
            }
        }
    }
}

/** "Clicking a Publisher Territory" (spec §6) — Publisher Name, Group Name,
 * Municipality/Barangay, House Holder/Interested Person/Return Visit/Bible
 * Study counts, all scoped to this one Publisher's own records *within the
 * currently-selected Group* (spec §8). Same [ModalBottomSheet] convention
 * [GroupInfoSheet]/[MapPointDetailsSheet] already use. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublisherInfoSheet(
    info: PublisherTerritoryInfo,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val publisherColor = parseHexColor(info.color)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(publisherColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White)
                }
                Column {
                    Text("RP ${info.publisherName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = publisherColor)
                    Text("Publisher Territory · ${info.groupName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DetailRow(label = "Group", value = info.groupName)
            if (info.municipalities.isNotEmpty()) {
                DetailRow(label = "Municipality", value = info.municipalities.joinToString(", "))
            }
            if (info.barangays.isNotEmpty()) {
                DetailRow(label = "Barangay", value = info.barangays.joinToString(", "))
            }
            DetailRow(label = "House Holders", value = info.houseHolderCount.toString())
            DetailRow(label = "Interested Persons", value = info.interestedPersons.toString())
            DetailRow(label = "Return Visits", value = info.returnVisits.toString())
            DetailRow(label = "Bible Studies", value = info.bibleStudies.toString())
        }
    }
}

/** A base map style offered in [MapLayersSheet] — [id] is what
 * `window.setBaseLayer`/the JS `BASE_LAYER_DEFS` map key on the other end
 * expects, so this list and that JS object must be kept in sync by hand. */
private data class BaseLayerOption(val id: String, val label: String, val description: String)
private val BASE_LAYER_OPTIONS = listOf(
    BaseLayerOption("standard", "Standard", "Roads, buildings, places, water, parks, points of interest"),
    BaseLayerOption("topo", "Topographic", "Elevation, contours, terrain — mountainous/rural territories"),
    BaseLayerOption("cycling", "Cycling", "Bicycle routes and cycling infrastructure (CyclOSM)"),
    BaseLayerOption("humanitarian", "Humanitarian", "Emphasizes roads/tracks/buildings in less-mapped areas"),
    // "Detailed / Colorful" (CARTO Voyager) was tried and removed — see
    // the matching JS `BASE_LAYER_DEFS` comment: it now requires a paid
    // API key this app doesn't have, and no genuinely free, key-less
    // alternative for this exact style was found.
)

/** An optional overlay in [MapLayersSheet] — [id] matches what
 * `window.setOverlayEnabled` on the JS side expects. */
private data class OverlayOption(val id: String, val label: String, val description: String)
private val OVERLAY_OPTIONS = listOf(
    OverlayOption("bicycle", "Bicycle Routes", "Waymarked cycling routes"),
    OverlayOption("hiking", "Hiking / Walking Routes", "Waymarked hiking routes"),
    OverlayOption("railways", "Railways", "Rail lines and stations (OpenRailwayMap)"),
    OverlayOption("gpsTraces", "Public GPS Traces", "Publicly shared OpenStreetMap GPS traces"),
    OverlayOption("mapNotes", "Map Notes", "Open OpenStreetMap notes near the current view"),
    OverlayOption("mapData", "Map Data", "Raw OpenStreetMap way geometry (zoom in to load)"),
)

private data class GoPreachLayerOption(val id: String, val label: String)
private val GOPREACH_LAYER_OPTIONS = listOf(
    GoPreachLayerOption("municipalities", "Municipalities"),
    GoPreachLayerOption("groups", "Territory Groups"),
    GoPreachLayerOption("householders", "House Holders"),
)

/** "MAP LAYER PANEL DESIGN" (spec §14) — Base Map (single choice, changes
 * only `window.setBaseLayer`), Overlays (multi-choice, optional OpenStreetMap
 * overlays), and GoPreach Layers (multi-choice, this app's own territory
 * data — on by default). None of these three sections can ever affect one
 * another: a base map swap, an overlay toggle, and a GoPreach layer toggle
 * are three structurally separate Leaflet layer systems on the JS side (see
 * [TerritoryLiveMap]'s own three independent `LaunchedEffect`s that push
 * this state) — switching one never resets camera position, search, or any
 * of the other two (spec §11/§15). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapLayersSheet(
    selectedBaseLayer: String,
    onSelectBaseLayer: (String) -> Unit,
    overlayStates: Map<String, Boolean>,
    onOverlayChange: (String, Boolean) -> Unit,
    goPreachLayerStates: Map<String, Boolean>,
    onGoPreachLayerChange: (String, Boolean) -> Unit,
    // "Show or hide the Names and other details of Interested Person/
    // Return Visit/Bible Study in Territory Map" — a separate toggle from
    // [goPreachLayerStates]'s own "householders" (that one hides the
    // markers themselves; this only hides each marker's own on-map label).
    showPipelineDetails: Boolean,
    onShowPipelineDetailsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 24.dp)
                .heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Map Layers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Text("Base Map", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            BASE_LAYER_OPTIONS.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onSelectBaseLayer(option.id) }.padding(vertical = 6.dp),
                ) {
                    RadioButton(selected = selectedBaseLayer == option.id, onClick = { onSelectBaseLayer(option.id) })
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        Text(option.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("Overlays", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
            OVERLAY_OPTIONS.forEach { option ->
                val checked = overlayStates[option.id] == true
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOverlayChange(option.id, !checked) }.padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = checked, onCheckedChange = { onOverlayChange(option.id, it) })
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        Text(option.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // "Transport Map"/"Public Transit" have no free, key-less tile
            // provider (Thunderforest requires a paid API key this app
            // doesn't have) — Railways above (OpenRailwayMap) is offered
            // instead of pretending a paid service is free (spec §21).
            Text(
                "Railways covers public rail routes; a full transit/bus overlay needs a paid provider not currently configured.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("GoPreach Layers", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
            GOPREACH_LAYER_OPTIONS.forEach { option ->
                val checked = goPreachLayerStates[option.id] != false
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onGoPreachLayerChange(option.id, !checked) }.padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = checked, onCheckedChange = { onGoPreachLayerChange(option.id, it) })
                    Text(option.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp))
                }
            }
            // "Make an option for the user if show or hide the Names and
            // other details of the (Interested Person, Return Visit, Bible
            // Study) in territory map" — hides only the on-map label
            // ("Name (Status)" + "RP <publisher>"); the marker/pin itself
            // (and its own tap-for-details bottom sheet) stays exactly as
            // it is either way, so a record is never actually harder to
            // find, just less cluttered/private-looking at a glance.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onShowPipelineDetailsChange(!showPipelineDetails) }.padding(vertical = 4.dp),
            ) {
                Checkbox(checked = showPipelineDetails, onCheckedChange = onShowPipelineDetailsChange)
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text("Names & Details on Map", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Show each Interested Person/Return Visit/Bible Study marker's name, status, and assigned publisher on the map",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

/** Neutral gray for a record whose assigned Publisher has no Congregation
 * Group at all — deliberately never a "real" group's own assigned/generated
 * color, so an unassigned record can never be mistaken for belonging to
 * one. Delegates to [GroupColorPalette], the single shared palette
 * [ManageGroupsScreen]'s color picker also reads/writes against, so the Map
 * and the Group editor never drift onto two different color formulas. */
private val UNASSIGNED_GROUP_COLOR = GroupColorPalette.UNASSIGNED_COLOR

/** See [GroupColorPalette.CURATED] — kept as a local alias only so the rest
 * of this file (and its own doc comments) don't need touching. */
private val CURATED_GROUP_PALETTE = GroupColorPalette.CURATED

/** See [GroupColorPalette.colorForGroupId]. */
private fun colorForGroupId(groupId: String): String = GroupColorPalette.colorForGroupId(groupId)

/** See [GroupColorPalette.parseHex]. */
private fun parseHexColor(hex: String): Color = GroupColorPalette.parseHex(hex)

/** "The icon identifies the person's current classification; GPS location
 * does not determine classification" — the one place every one of the five
 * category emoji is defined; the Legend and the bottom sheet both reuse it
 * verbatim. The map's own pins now carry their own distinct SVG glyph per
 * kind too (see [buildTerritoryMapHtml]'s own `iconGlyphFor`) — this emoji
 * set is unrelated to that, purely for the Legend/detail-sheet's own
 * Compose-side reference key. */
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
    // "Every record displayed on the map must use the color assigned to its
    // Congregation Group" — a "#RRGGBB" string (see [colorForGroupId]),
    // ready to hand straight to the JS marker-icon builder; [UNASSIGNED_GROUP_COLOR]
    // for PUBLISHER/ME (no Congregation Group applies to either) and for a
    // pipeline record whose own assigned Publisher has no Group.
    val groupColor: String = UNASSIGNED_GROUP_COLOR,
    // Shown in the detail bottom sheet only — "clearly show which records...
    // belong to which group;" null for PUBLISHER/ME/an unassigned record.
    val groupName: String? = null,
    // "Expand House Holders Inside the Selected Territory... only show
    // House Holders actually located inside the selected territory" — the
    // real Congregation Group id this record belongs to (never the display
    // name), used purely client-side to filter [points] down to one Group's
    // own once its territory is tapped (see [TerritoryLiveMap]'s own
    // `selectedGroupId`); null for PUBLISHER/ME (a Publisher-sharing point
    // is never a House Holder — spec §15 — so it's simply never a member of
    // any Group's own filtered set) and for a pipeline record whose
    // assigned Publisher has no Group.
    val groupId: String? = null,
)

/** One Congregation Group's own barrier — see [TerritoryMapScreen]'s
 * `mapGroupBoundaries` for how this is built and [TerritoryLiveMap]'s own
 * `window.setGroupScopeBoundaries` for how it's actually drawn (color-coded,
 * never merged with another Group's). [id] is [UNASSIGNED_GROUP_ID] for the
 * one pseudo-group covering a record whose assigned Publisher has no real
 * Congregation Group. */
private data class MapGroupBoundary(val id: String, val name: String, val color: String, val points: List<LatLng>)

private const val UNASSIGNED_GROUP_ID = "—unassigned-group—"

/** One Publisher's own territory subdivision *inside* an already-selected
 * Group's own territory — see [TerritoryLiveMap]'s own
 * `selectedGroupPublisherBoundaries` for how this is built and
 * `window.setPublisherScopeBoundaries` for how it's actually drawn.
 * "PUBLISHER TERRITORY – NO DIFFERENT COLORS... All publishers inside the
 * same Group Territory must use the same territory/map appearance" —
 * [color] is always that parent Group's own single color, the same value
 * on every Publisher within it (no longer a distinct per-Publisher color;
 * see this file's own history for the removed [PUBLISHER_TERRITORY_COLORS]
 * palette this replaced). Publishers are distinguished on the map only by
 * their own boundary/divider line and name label, never by fill color. */
private data class MapPublisherBoundary(val id: String, val name: String, val color: String, val points: List<LatLng>)

/** "Clicking a Publisher Territory" (spec §6) — everything shown in
 * [PublisherInfoSheet]; [municipalities]/[barangays] are every distinct
 * value across this Publisher's own records *within the selected Group*
 * (never that Publisher's records in some other Group — spec §8's own
 * "only... territory assignments belonging to the selected Group
 * Territory"), sorted for a stable, predictable display order. */
private data class PublisherTerritoryInfo(
    val publisherId: String,
    val publisherName: String,
    val color: String,
    val groupName: String,
    val municipalities: List<String>,
    val barangays: List<String>,
    val houseHolderCount: Int,
    val interestedPersons: Int,
    val returnVisits: Int,
    val bibleStudies: Int,
)

/** One row of the "Group Report" — see [TerritoryMapScreen]'s own
 * `groupReportRows` for how this is built (always from the same dataset the
 * map's own markers/barriers use, spec §9) and [GroupReportTable] for how
 * it's actually rendered. */
private data class GroupReportRow(
    val groupId: String,
    val groupName: String,
    val color: String,
    val publishers: Int,
    val interestedPersons: Int,
    val returnVisits: Int,
    val bibleStudies: Int,
    // "Show the names of the group publishers if the Territory Group is
    // clicked" — only ever populated for [GroupInfoSheet]'s own single-Group
    // focus (see [TerritoryLiveMap]'s own `selectedGroupInfo`); the main
    // multi-row Group Report table has no room for this and never needs it,
    // so it stays the default empty list there.
    val publisherNames: List<String> = emptyList(),
    // "Group Territory details should include... Municipality, Barangay,
    // House Holder records" (spec §2) — same single-Group-focus-only
    // convention as [publisherNames] above; every distinct Municipality/
    // Barangay actually covered by this Group's own records, and the total
    // record count.
    val municipalities: List<String> = emptyList(),
    val barangays: List<String> = emptyList(),
    val houseHolderCount: Int = 0,
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
