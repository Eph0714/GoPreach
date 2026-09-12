package com.emfitsolutions.gopreach.ui.screens.territories

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.StreetViewPanoramaView
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.LatLng as GmsLatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.emfitsolutions.gopreach.data.location.LatLng
import com.emfitsolutions.gopreach.data.location.formatCoordinatesDms
import com.emfitsolutions.gopreach.data.model.InterestedPerson
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
    var showFilterSheet by remember { mutableStateOf(false) }

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
    // "Full-Screen Map View... the map occupies the entire available
    // screen" — Map View is now the default landing mode; List View is
    // still reachable (existing functionality preserved per spec §17.11)
    // via the toggle in the top bar's own actions instead of a persistent
    // segmented-button row that used to eat vertical space in both modes.
    var viewMode by remember { mutableStateOf(TerritoryViewMode.MAP) }

    // "GOOGLE MAP TYPES & MAP DETAILS" — hoisted up here (not local to
    // TerritoryLiveMap) so a Map View <-> List View round trip never resets
    // them, same reasoning every other Territory Map control already
    // follows (see [advancedFilter]/[searchQuery] etc. — TerritoryLiveMap
    // itself is fully torn down and rebuilt every time [viewMode] leaves and
    // re-enters MAP, so anything that needs to survive that has to live
    // above it, not inside it).
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var trafficEnabled by remember { mutableStateOf(false) }
    // "Raised Buildings / 3D Buildings" — the official Google Maps Android
    // SDK feature this maps to 1:1 is `GoogleMap`'s own buildings layer
    // (extruded 3D building footprints, automatic wherever Google has the
    // data — there is no separate "2D vs 3D" toggle to speak of on Android,
    // unlike the JS Maps API's distinct tilt/3D controls).
    var buildingsEnabled by remember { mutableStateOf(false) }
    var streetViewEnabled by remember { mutableStateOf(false) }

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
                    // "Add a filter in Territory Map" — a badge dot marks
                    // whenever any filter beyond the default "All" is active,
                    // so it's obvious the map isn't showing everything.
                    if (showAdvancedFilter) {
                        androidx.compose.material3.BadgedBox(badge = {
                            if (advancedFilter.isActive) androidx.compose.material3.Badge()
                        }) {
                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(Icons.Rounded.Tune, contentDescription = "Filters")
                            }
                        }
                    }
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
            // "Add a persistent filter area" — the one search/filter row
            // present above whichever view (Map or List) is currently
            // showing, so switching the toggle never loses/resets a filter
            // already in effect (spec's own "Map View/List View toggle
            // preserving filters").
            TerritoryPersistentFilterBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                searchByField = searchByField,
                onSearchByFieldChange = { searchByField = it },
                groupBy = groupBy,
                onGroupByChange = { groupBy = it },
                province = effectiveProvince,
                municipality = advancedFilter.cityMunicipality,
                municipalityOptions = municipalityOptions,
                onMunicipalityChange = { advancedFilter = advancedFilter.copy(cityMunicipality = it, barangay = null) },
                barangay = advancedFilter.barangay,
                barangayOptions = barangayOptions,
                onBarangayChange = { advancedFilter = advancedFilter.copy(barangay = it) },
                resultCount = filtered.size,
                showGroupBy = viewMode == TerritoryViewMode.LIST,
            )

            if (viewMode == TerritoryViewMode.MAP) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    TerritoryLiveMap(
                        rows = filtered,
                        publisherRows = filteredPublisherRows,
                        canSeePublisherLocations = canSeePublisherLocations,
                        getCurrentLocation = viewModel::currentLocation,
                        hasLocationPermission = viewModel::hasLocationPermission,
                        focusLat = focusLat,
                        focusLng = focusLng,
                        focusName = focusName,
                        publisherNames = personNames,
                        mapType = mapType,
                        onMapTypeChange = { mapType = it },
                        trafficEnabled = trafficEnabled,
                        onTrafficEnabledChange = { trafficEnabled = it },
                        buildingsEnabled = buildingsEnabled,
                        onBuildingsEnabledChange = { buildingsEnabled = it },
                        streetViewEnabled = streetViewEnabled,
                        onStreetViewEnabledChange = { streetViewEnabled = it },
                        onRecordVisit = { personId ->
                            filtered.firstOrNull { it.person.id == personId }?.let { tryOpenDetails(it.person) }
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
                                                    Column(modifier = Modifier.fillMaxWidth().clickable { tryOpenDetails(row.person) }) {
                                                        // "The Householder Name must be clickable" — opens the exact
                                                        // same full detail + Visit History + Add Visit screen every
                                                        // other Territory Map entry point already reuses (see
                                                        // [tryOpenDetails]) — never a second, parallel implementation.
                                                        Text(row.person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                        Text(row.person.pipelineStage.statusLabel(), style = MaterialTheme.typography.bodySmall)
                                                        if (groupBy == TerritoryGroupBy.MUNICIPALITY) {
                                                            Text("Brgy. ${row.person.barangay ?: "—"}", style = MaterialTheme.typography.bodySmall)
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

    if (showFilterSheet) {
        TerritoryFilterSheet(
            isSuperAdmin = isSuperAdmin,
            fixedCongregationId = fixedCongregationId,
            fixedCongregationName = fixedCongregationName,
            // "The Field Service Group list must depend on the selected
            // Congregation" — the *effective* one now (Super-Admin's current
            // pick, `null` meaning every congregation for "All
            // Congregations"), not a static "every congregation this role
            // could ever pick," so switching Congregation always refreshes
            // this to match.
            congregationIds = if (isSuperAdmin) effectiveCongregationId?.let(::setOf) else setOfNotNull(fixedCongregationId),
            filter = advancedFilter,
            resultCount = advancedFilteredRows.size,
            onFilterChange = { advancedFilter = it },
            onDismiss = { showFilterSheet = false },
            viewModel = viewModel,
        )
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
    resultCount: Int,
    showGroupBy: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Search") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
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

/** The Territory Map's own filter — "the dropdown must contain exactly
 * these options." A `null` [MapFilterOption] (nothing picked yet, or after
 * Refresh) is the default/original view: every pipeline record, no
 * publishers — not itself one of the seven, since the dropdown lists
 * exactly these seven and no more. */
private enum class MapFilterOption(val label: String, val emoji: String) {
    // "Add 'All' in the category" — every classification (including
    // Publishers, when [canSeePublisherLocations] allows it) shown at once.
    ALL("All", "🗂️"),
    MY_LOCATION("My Location", "📍"),
    BIBLE_STUDY("Bible Study", "📖"),
    RETURN_VISIT("Return Visit", "🔄"),
    // "Publisher – All/My Congregation" and "Nearest Publisher" are no
    // longer offered in the dropdown (see the DropdownMenu below, which
    // always skips both) — kept here only because [applySelection]'s
    // Publisher-kind filtering logic and the "open in Territory Map from
    // Share Location" focus effect still reuse it internally.
    PUBLISHERS("Publisher – All Congregation/Group", "👤"),
    NEAREST_PUBLISHER("Nearest Publisher", "👤"),
    NEAREST_BIBLE_STUDY("Nearest Bible Study", "📖"),
    NEAREST_RETURN_VISIT("Nearest Return Visit", "🔄"),
}

/**
 * "Map View" — a real, full-screen, pinch-zoomable embedded map. "Core
 * Requirement: Google Maps must be the actual map used by GoPreach Territory
 * Map" — this renders via [GoogleMap]/[Marker] (maps-compose), the real
 * Google Maps SDK, not a WebView/Leaflet/OpenStreetMap stand-in and not a
 * static image. Requires a real API key (see AndroidManifest.xml's
 * `com.google.android.geo.API_KEY`, sourced from local.properties'
 * `MAPS_API_KEY` — see that file's own comment for how to get one); a
 * machine without one configured gets a clear on-screen message instead of
 * Google Maps' own silent gray-tile failure (see [MapLoadState.NO_API_KEY]).
 *
 * "Publishers, Bible Studies, and Return Visits are separate
 * classifications. A person having a GPS location does not automatically
 * make that person a Publisher." — enforced structurally: [MapPoint.kind]
 * is set once, at construction, from the record's *own* type ([pipelinePoints]
 * from [TerritoryMapRow.person]'s [PipelineStage], [publisherPoints] only
 * from an actively-sharing, [com.emfitsolutions.gopreach.data.model.PublisherCategory.REGULAR_PUBLISHER]
 * [TerritoryPublisherRow] — see [TerritoryMapViewModel.publisherRowsFor]'s
 * own doc comment for that filter). Nothing downstream (the dropdown, the
 * marker style, the bottom sheet) can blur that line — there is no path
 * from "has coordinates" to "counts as a Publisher."
 */
@Composable
private fun TerritoryLiveMap(
    rows: List<TerritoryMapRow>,
    publisherRows: List<TerritoryPublisherRow>,
    canSeePublisherLocations: Boolean,
    getCurrentLocation: suspend () -> LatLng?,
    hasLocationPermission: () -> Boolean,
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
    // "GOOGLE MAP TYPES & MAP DETAILS" — hoisted to [TerritoryMapScreen]
    // itself; see that state's own doc comment for why.
    mapType: MapType,
    onMapTypeChange: (MapType) -> Unit,
    trafficEnabled: Boolean,
    onTrafficEnabledChange: (Boolean) -> Unit,
    buildingsEnabled: Boolean,
    onBuildingsEnabledChange: (Boolean) -> Unit,
    streetViewEnabled: Boolean,
    onStreetViewEnabledChange: (Boolean) -> Unit,
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
    // never reaches the map at all — it's counted separately instead, so
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
                    // icon rather than ever retaining a stale one.
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
    // that upstream (see TerritoryMapViewModel.publisherRowsFor); hidden by
    // default here too (opt-in via the dropdown's own "Publisher – All
    // Congregation"/"Nearest Publisher" entries), never automatic.
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
    // just in Logcat rather than on-screen (this app's other diagnostic UI —
    // SyncStatusButton, etc. — is text/user-facing, not a raw debug dump).
    LaunchedEffect(rows, publisherRows) {
        Log.d(TAG, "Records retrieved: ${rows.size}; valid GPS: ${pipelinePoints.size}; invalid/missing GPS: $invalidCount; publishers sharing: ${publisherPoints.size}")
    }

    // "Add the user current location in the map view" — fetched on demand
    // (the "My Location" filter, or automatically the first time a "Nearest…"
    // filter needs it), not on every screen open, so this never surprises
    // anyone with a permission prompt they didn't ask for.
    var myLocation by remember { mutableStateOf<LatLng?>(null) }
    var pendingPermissionFilter by remember { mutableStateOf<MapFilterOption?>(null) }
    var selectedFilter by remember { mutableStateOf<MapFilterOption?>(null) }
    // "Be collapsible/minimizable... not cover important map controls" —
    // starts collapsed so it never obscures the map on first load; the
    // "LEGEND" chip is always visible to expand it again.
    var legendExpanded by remember { mutableStateOf(false) }
    var selectedPointId by remember { mutableStateOf<String?>(null) }
    // "Show details in all categories in territory map even 'My Location'" —
    // a synthetic point built from the live GPS fix, id "me", so tapping the
    // "you are here" dot opens the exact same bottom sheet every other
    // marker already does, without making it a real member of [points]
    // (which would risk it being counted by a dropdown filter/nearest search
    // it was never meant to participate in).
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

    // Fetches a fresh GPS fix the first time any filter that needs one
    // (My Location, or a Nearest-X search) is selected — shared so a fix
    // obtained once during this screen's lifetime is never asked for twice.
    // Surfaces the two specific failure states as Snackbars rather than
    // silently doing nothing.
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
        return fix
    }

    // "The selected option determines what markers... are displayed on the
    // map" — the single source of truth for which [MapPointKind]s are
    // currently visible; every dropdown entry (and Refresh, and a fresh
    // screen open) sets this, and [visiblePoints] below is the only place
    // that actually reads it. `null` (the default/original state) means
    // every pipeline stage, no publishers.
    fun visibleKindsFor(option: MapFilterOption?): Set<MapPointKind> = when (option) {
        // The old floating category dropdown (the only UI that used to set
        // this to anything but its own default) is gone now that the
        // persistent filter bar's Search/Search By/Group By/Municipality/
        // Barangay already narrow [rows] before they ever reach this
        // composable — so the default view always shows every pipeline
        // record plus Publishers currently sharing (when authorized), with
        // nothing left to separately "select" on the map itself.
        null -> setOf(MapPointKind.SEARCHING, MapPointKind.RETURN_VISIT, MapPointKind.BIBLE_STUDY) + if (canSeePublisherLocations) setOf(MapPointKind.PUBLISHER) else emptySet()
        MapFilterOption.ALL -> setOf(MapPointKind.SEARCHING, MapPointKind.RETURN_VISIT, MapPointKind.BIBLE_STUDY, MapPointKind.PUBLISHER)
        MapFilterOption.MY_LOCATION -> emptySet()
        MapFilterOption.BIBLE_STUDY -> setOf(MapPointKind.BIBLE_STUDY)
        MapFilterOption.RETURN_VISIT -> setOf(MapPointKind.RETURN_VISIT)
        MapFilterOption.PUBLISHERS -> setOf(MapPointKind.PUBLISHER)
        MapFilterOption.NEAREST_PUBLISHER -> setOf(MapPointKind.PUBLISHER)
        MapFilterOption.NEAREST_BIBLE_STUDY -> setOf(MapPointKind.BIBLE_STUDY)
        MapFilterOption.NEAREST_RETURN_VISIT -> setOf(MapPointKind.RETURN_VISIT)
    }

    // "Performance... avoid duplicate markers; clear old markers before
    // adding new filtered results" — declarative by construction here: each
    // recomposition derives the exact, current marker set from [points] +
    // [selectedFilter] fresh (no imperative add/remove calls to get wrong),
    // and every `Marker` composable below is `key()`-ed by its own stable
    // [MapPoint.id], so Compose only ever adds/removes the markers that
    // actually entered/left the set instead of tearing down and rebuilding
    // every marker on every recomposition.
    val visiblePoints = remember(points, selectedFilter) {
        val kinds = visibleKindsFor(selectedFilter)
        points.filter { it.kind in kinds }
    }

    val cameraPositionState = rememberCameraPositionState()
    var loadState by remember { mutableStateOf(if (hasGoogleMapsApiKey(context)) MapLoadState.LOADING else MapLoadState.NO_API_KEY) }
    // Always available (not just on failure) — lets whoever's testing this
    // confirm exactly what's happening (records/points counts, load state,
    // whether a Maps API key is even configured).
    var showDiagnostics by remember { mutableStateOf(false) }

    // "The selected option determines what markers and information are
    // displayed" + "§12 Automatic Map Behavior" — the one place every
    // dropdown entry (and Refresh, and a fresh page load) routes through, so
    // the camera/filter/empty-state logic for each option lives in exactly
    // one spot. No JS bridge anymore — this drives native GoogleMap state
    // directly (see [visiblePoints]/[cameraPositionState]).
    suspend fun applySelection(option: MapFilterOption?) {
        selectedFilter = option
        selectedPointId = null
        when (option) {
            MapFilterOption.MY_LOCATION -> {
                val fix = ensureMyLocation() ?: return
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(GmsLatLng(fix.lat, fix.lng), 16f))
            }
            MapFilterOption.NEAREST_PUBLISHER, MapFilterOption.NEAREST_BIBLE_STUDY, MapFilterOption.NEAREST_RETURN_VISIT -> {
                val kind = when (option) {
                    MapFilterOption.NEAREST_PUBLISHER -> MapPointKind.PUBLISHER
                    MapFilterOption.NEAREST_BIBLE_STUDY -> MapPointKind.BIBLE_STUDY
                    else -> MapPointKind.RETURN_VISIT
                }
                val fix = ensureMyLocation() ?: return
                val candidates = points.filter { it.kind == kind }
                if (candidates.isEmpty()) {
                    snackbarHostState.showSnackbar("No locations found for the selected category.")
                    return
                }
                val nearest = candidates.sortedBy { haversineMeters(fix.lat, fix.lng, it.lat, it.lng) }.take(3)
                selectedPointId = nearest.first().id
                val bounds = LatLngBounds.Builder().apply {
                    include(GmsLatLng(fix.lat, fix.lng))
                    nearest.forEach { include(GmsLatLng(it.lat, it.lng)) }
                }.build()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            }
            else -> {
                val kind = when (option) {
                    MapFilterOption.BIBLE_STUDY -> MapPointKind.BIBLE_STUDY
                    MapFilterOption.RETURN_VISIT -> MapPointKind.RETURN_VISIT
                    MapFilterOption.PUBLISHERS -> MapPointKind.PUBLISHER
                    else -> null
                }
                if (kind != null && points.none { it.kind == kind }) {
                    snackbarHostState.showSnackbar("No locations found for the selected category.")
                }
                val visible = points.filter { it.kind in visibleKindsFor(option) }
                fitCameraTo(cameraPositionState, visible)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val option = pendingPermissionFilter
        pendingPermissionFilter = null
        if (granted && option != null) {
            scope.launch { applySelection(option) }
        } else if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("Location permission is required to display your current position.") }
        }
    }

    fun selectFilter(option: MapFilterOption?) {
        val needsLocation = option == MapFilterOption.MY_LOCATION ||
            option == MapFilterOption.NEAREST_PUBLISHER || option == MapFilterOption.NEAREST_BIBLE_STUDY || option == MapFilterOption.NEAREST_RETURN_VISIT
        if (needsLocation && myLocation == null && !hasLocationPermission()) {
            pendingPermissionFilter = option
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            scope.launch { applySelection(option) }
        }
    }

    // Fits the camera to whatever's currently visible the first time the map
    // finishes loading, and again any time the underlying record set changes
    // shape (new/updated records arriving live) while nothing more specific
    // (a manual filter, a focus target) already claimed the camera — same
    // "don't reload/refit the whole map for no reason" performance
    // requirement the dropdown-driven [applySelection] path already follows.
    LaunchedEffect(loadState, points) {
        if (loadState == MapLoadState.LOADED && selectedFilter == null && myLocation == null) {
            fitCameraTo(cameraPositionState, points.filter { it.kind in visibleKindsFor(null) })
        }
    }

    // "Clicking coordinates should open the Territory Map centered on the
    // Publisher's latest location" — runs once, the first time the map
    // finishes loading with a focus target actually present (see
    // TerritoryMapScreen's own `focusLat`/`focusLng`, only ever non-null
    // when reached via Share Location's "open in Territory Map" action).
    // Switches to the Publisher filter first, since the target would
    // otherwise be hidden by the default pipeline-only view, then pans/
    // zooms there — highlighting the closest matching marker when one's
    // found within a realistic GPS-noise radius, or just centering the
    // camera there otherwise.
    var hasAppliedFocus by remember { mutableStateOf(false) }
    LaunchedEffect(loadState, focusLat, focusLng) {
        if (loadState != MapLoadState.LOADED || hasAppliedFocus) return@LaunchedEffect
        val lat = focusLat
        val lng = focusLng
        if (lat == null || lng == null) return@LaunchedEffect
        hasAppliedFocus = true
        selectedFilter = MapFilterOption.PUBLISHERS
        val nearest = points.filter { it.kind == MapPointKind.PUBLISHER }.minByOrNull { haversineMeters(lat, lng, it.lat, it.lng) }
        if (nearest != null && haversineMeters(lat, lng, nearest.lat, nearest.lng) < 100) {
            selectedPointId = nearest.id
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(GmsLatLng(lat, lng), 17f))
        } else {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(GmsLatLng(lat, lng), 17f))
        }
    }

    Box(modifier = modifier) {
        if (loadState == MapLoadState.NO_API_KEY) {
            // "Configure the Google Maps API key" — a real key is required
            // for Google Maps to render anything at all; rather than a
            // silent gray tile grid (Google's own default failure mode when
            // a key is missing/invalid), this tells whoever's looking at it
            // exactly what to do (see local.properties' own MAPS_API_KEY
            // comment / app/build.gradle.kts' manifestPlaceholders wiring).
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Map, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    "Google Maps API key is not configured.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "Set MAPS_API_KEY in local.properties (see that file's own comment for where to get one), then rebuild the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                // "GOOGLE MAP TYPES & MAP DETAILS" — every one of these is an
                // official, native GoogleMap property (see the Map
                // Type/Details control below); none of it is simulated.
                // "Raised Buildings / 3D Buildings" maps 1:1 to [isBuildingEnabled]
                // — Android's own equivalent of that JS-API-named feature (see
                // that parameter's own doc comment). Public Transit/Bicycling
                // have no native GoogleMap-for-Android equivalent at all (those
                // are Maps JavaScript API-only layers) — spec §14's own
                // "gracefully disable... rather than a non-functional control"
                // clause is exactly why the Map Details menu below shows both,
                // disabled, instead of either faking them or silently omitting
                // them.
                properties = MapProperties(mapType = mapType, isTrafficEnabled = trafficEnabled, isBuildingEnabled = buildingsEnabled),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
                onMapLoaded = { loadState = MapLoadState.LOADED },
            ) {
                visiblePoints.forEach { point ->
                    key(point.id) {
                        Marker(
                            state = MarkerState(position = GmsLatLng(point.lat, point.lng)),
                            title = point.name,
                            snippet = point.status,
                            icon = rememberMarkerIcon(point.kind, selectedPointId == point.id),
                            onClick = {
                                selectedPointId = point.id
                                true
                            },
                        )
                    }
                }
                // "Show details in all categories in territory map even 'My
                // Location'" — a distinct marker, outside [visiblePoints]
                // entirely (always shown regardless of the selected filter),
                // added once Android actually has a GPS fix.
                myLocationPoint?.let { me ->
                    key("me") {
                        Marker(
                            state = MarkerState(position = GmsLatLng(me.lat, me.lng)),
                            title = me.name,
                            snippet = me.status,
                            icon = rememberMarkerIcon(MapPointKind.ME, selectedPointId == "me"),
                            onClick = {
                                selectedPointId = "me"
                                true
                            },
                        )
                    }
                }
            }
        }

        if (loadState == MapLoadState.LOADING) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (loadState == MapLoadState.LOADED && invalidCount > 0) {
            Card(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp).padding(horizontal = 16.dp),
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

        // "Add controls for Map Type and Map Details/Layers" — a single
        // floating "Layers" button (Google Maps' own convention for this
        // exact kind of control), top-end so it never collides with the
        // invalid-count card (top-center) or the Legend/Refresh/diagnostics
        // trio already anchored elsewhere.
        var layersMenuExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp)) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
            ) {
                IconButton(onClick = { layersMenuExpanded = true }) {
                    Icon(Icons.Rounded.Layers, contentDescription = "Map type and details", tint = MaterialTheme.colorScheme.primary)
                }
            }
            DropdownMenu(expanded = layersMenuExpanded, onDismissRequest = { layersMenuExpanded = false }) {
                Text(
                    "MAP TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                // "The map type selection must not reset Search/Search By/
                // Group By/Sort By/Municipality/Barangay/current map
                // position/selected record" — trivially true here: this only
                // ever writes [mapType] itself (hoisted above this whole
                // composable, see its own doc comment), nothing else.
                listOf(
                    "Default" to MapType.NORMAL,
                    "Satellite" to MapType.SATELLITE,
                    "Terrain" to MapType.TERRAIN,
                ).forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = {
                            androidx.compose.material3.RadioButton(selected = mapType == value, onClick = null)
                        },
                        onClick = { onMapTypeChange(value) },
                    )
                }
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "MAP DETAILS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                // "Traffic... keep GoPreach record markers visible" /
                // "Bicycling... keep the existing GoPreach markers visible" —
                // true by construction: these only ever toggle GoogleMap's
                // own [MapProperties] flags (see the GoogleMap call above);
                // [visiblePoints]'s own Marker composables are a completely
                // separate part of this composable's content and are never
                // conditioned on any of these three booleans.
                MapDetailToggleRow(label = "Traffic", checked = trafficEnabled, enabled = true, onCheckedChange = onTrafficEnabledChange)
                MapDetailToggleRow(
                    label = "Public Transit",
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                    unavailableReason = "Not available on Android — Transit is a Google Maps JavaScript API–only layer.",
                )
                MapDetailToggleRow(
                    label = "Bicycling",
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                    unavailableReason = "Not available on Android — Bicycling is a Google Maps JavaScript API–only layer.",
                )
                MapDetailToggleRow(label = "Street View", checked = streetViewEnabled, enabled = true, onCheckedChange = onStreetViewEnabledChange)
                MapDetailToggleRow(label = "Raised Buildings / 3D Buildings", checked = buildingsEnabled, enabled = true, onCheckedChange = onBuildingsEnabledChange)
            }
        }

        // "Add a floating Refresh button" — resets the dropdown back to its
        // unselected/original state and re-fetches location data.
        SmallFloatingActionButton(
            onClick = {
                myLocation = null
                scope.launch {
                    applySelection(null)
                    snackbarHostState.showSnackbar("Territory map updated successfully.")
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 76.dp),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh map")
        }

        // Always available, not just on failure — lets whoever's testing
        // this confirm exactly what's happening (records/points counts,
        // load state) without needing adb/Logcat access to report it back
        // accurately.
        IconButton(
            onClick = { showDiagnostics = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        ) {
            Icon(Icons.Rounded.Info, contentDescription = "Map diagnostics", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // "Add a compact Map Legend floating over the map" — bottom-start,
        // clear of Google Maps' own zoom controls (top-left) and the
        // dropdown (top-center) and Refresh/diagnostics (bottom-end), so
        // nothing floating ever overlaps another control.
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(10.dp).widthIn(max = 220.dp)) {
                Row(
                    modifier = Modifier.clickable { legendExpanded = !legendExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("LEGEND", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Icon(
                        if (legendExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (legendExpanded) "Collapse legend" else "Expand legend",
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (legendExpanded) {
                    // Same icon+category-name pairing used by every marker,
                    // the bottom sheet, and the dropdown — "use the same
                    // marker icons shown on the map."
                    listOf(MapPointKind.ME, MapPointKind.PUBLISHER, MapPointKind.BIBLE_STUDY, MapPointKind.RETURN_VISIT, MapPointKind.SEARCHING).forEach { kind ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Text(emojiFor(kind), style = MaterialTheme.typography.bodyMedium)
                            Text(categoryLabelFor(kind), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
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

    // "STREET VIEW... Street View must not change or delete the GoPreach
    // record" — purely a viewer; centered on whichever record is currently
    // selected (the same coordinates its own detail sheet just showed), or
    // the map's current camera target if nothing's selected. Toggling it off
    // (the Map Details checkbox, or this dialog's own Close button) is the
    // only thing that ever changes [streetViewEnabled] — nothing here writes
    // to any GoPreach record.
    if (streetViewEnabled) {
        val target = selectedPoint?.let { GmsLatLng(it.lat, it.lng) } ?: cameraPositionState.position.target
        StreetViewDialog(location = target, onDismiss = { onStreetViewEnabledChange(false) })
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
                    Text("Google Maps API key configured: ${if (hasGoogleMapsApiKey(context)) "Yes" else "No"}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnostics = false }) { Text("Close") }
            },
        )
    }
}

/** Pans/zooms [cameraPositionState] to fit every point in [visible] — a
 * single point gets a comfortable street-level zoom (a 0-span "bounds"
 * around one coordinate would otherwise throw), more than one fits the
 * smallest bounds containing all of them with generous padding so no marker
 * lands clipped at the very edge of the screen. A no-op when [visible] is
 * empty (nothing to fit to) — the camera simply stays wherever it was. */
private suspend fun fitCameraTo(cameraPositionState: com.google.maps.android.compose.CameraPositionState, visible: List<MapPoint>) {
    when {
        visible.isEmpty() -> return
        visible.size == 1 -> cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(GmsLatLng(visible[0].lat, visible[0].lng), 16f))
        else -> {
            val bounds = LatLngBounds.Builder().apply { visible.forEach { include(GmsLatLng(it.lat, it.lng)) } }.build()
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        }
    }
}

/** Whether a real Google Maps API key is present — read from the exact same
 * manifest `<meta-data>` entry Google Maps' own SDK reads at runtime (see
 * AndroidManifest.xml's `com.google.android.geo.API_KEY`, sourced from
 * local.properties' `MAPS_API_KEY` via app/build.gradle.kts). A machine that
 * hasn't set one yet gets a clear on-screen message (see [TerritoryLiveMap])
 * instead of Google Maps' own silent gray-tile failure mode. */
private fun hasGoogleMapsApiKey(context: android.content.Context): Boolean {
    return runCatching {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
        val key = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
        !key.isNullOrBlank()
    }.getOrDefault(false)
}

/** Builds (and caches, per kind + selected-state) the round, colored, emoji-
 * labeled marker bitmap every category already uses everywhere else (legend,
 * bottom sheet, dropdown) — see [markerColorFor]/[emojiFor], the same
 * functions those other spots call. Selected markers grow and gain a gold
 * ring ("Highlight the selected marker"), same visual language the prior
 * Leaflet markers used. */
@Composable
private fun rememberMarkerIcon(kind: MapPointKind, selected: Boolean): BitmapDescriptor {
    return remember(kind, selected) {
        val sizePx = if (selected) 130 else 100
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = sizePx / 2f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = markerColorFor(kind).toArgb() }
        canvas.drawCircle(radius, radius, radius - 6f, fillPaint)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (selected) 10f else 6f
            color = if (selected) 0xFFFFD600.toInt() else 0xFFFFFFFF.toInt()
        }
        canvas.drawCircle(radius, radius, radius - 6f, borderPaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx * 0.42f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(emojiFor(kind), radius, textY, textPaint)
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}

/** "Use Google's actual Street View functionality... If Street View is
 * unavailable at a particular location, show an appropriate message instead
 * of displaying an empty/broken view" — [StreetViewPanoramaView] is the
 * official Google Maps Android SDK view for this (the same one a native,
 * non-Compose Street View screen would use; maps-compose itself doesn't wrap
 * it, so this is a plain [AndroidView] the same way every WebView-era screen
 * in this app already used that pattern). [StreetViewPanorama.setPosition]
 * searches near [location] for the nearest available panorama;
 * [StreetViewPanorama.OnStreetViewPanoramaChangeListener] fires with a
 * `null` location precisely when nothing was found nearby — that `null` is
 * the one and only signal this reads to show the "not available" message,
 * never a guess. */
@Composable
private fun StreetViewDialog(location: GmsLatLng, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // `null` while the very first search is still in flight, then locked to
    // whatever that first callback found — matches [location] itself never
    // changing after this dialog opens (a fresh dialog instance is what
    // handles a different selected record/camera target, not a live update
    // to this one).
    var available by remember { mutableStateOf<Boolean?>(null) }
    val panoramaView = remember { StreetViewPanoramaView(context) }
    DisposableEffect(panoramaView) {
        panoramaView.onCreate(null)
        panoramaView.onResume()
        panoramaView.getStreetViewPanoramaAsync { panorama ->
            panorama.setOnStreetViewPanoramaChangeListener { changedLocation -> available = changedLocation != null }
            panorama.setPosition(location, 50)
        }
        onDispose {
            panoramaView.onPause()
            panoramaView.onDestroy()
        }
    }
    // "Show an appropriate message instead of displaying an empty/broken
    // view" — an indefinite spinner is itself exactly that broken view.
    // [OnStreetViewPanoramaChangeListener] is Google's own official signal
    // for "no panorama found," but it can also simply never fire at all on a
    // slow/limited connection; a plain, generous timeout is what turns that
    // silent hang into the same message a fast, confirmed "not found" result
    // already shows, rather than leaving whoever's looking at this stuck
    // forever with no explanation.
    LaunchedEffect(location) {
        kotlinx.coroutines.delay(12_000)
        if (available == null) available = false
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { panoramaView })
            when (available) {
                null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                false -> Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Street View is not available at this location.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                true -> {}
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close Street View", tint = Color.White)
            }
        }
    }
}

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

/** One "Map Details" checkbox row. [unavailableReason] non-null means this
 * layer has no real Google Maps Android SDK equivalent at all — spec §14's
 * own "gracefully disable that option rather than displaying a non-
 * functional control": shown, checked-off, disabled, and explained, rather
 * than either silently missing or faked with a client-drawn overlay that
 * isn't actually Google's own data. */
@Composable
private fun MapDetailToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    unavailableReason: String? = null,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(label, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
                if (unavailableReason != null) {
                    Text(unavailableReason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        leadingIcon = {
            androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        },
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
    )
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
 * category emoji is defined, reused verbatim by the map markers (via
 * [rememberMarkerIcon]), the legend, the bottom sheet, the dropdown, and List
 * View's own rows, so it's structurally impossible for two screens to
 * disagree about which icon means what. */
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

private enum class MapLoadState { LOADING, LOADED, NO_API_KEY }

/** What kind of thing a [MapPoint] represents — drives both its marker style
 * ([rememberMarkerIcon]) and which dropdown entry controls its visibility.
 * "Publishers, Bible Studies, and Return Visits are separate
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

/**
 * "Add a filter in Territory Map... make it simple, professional and
 * modern. check the spacing and design" — extended by "Territory Map
 * Congregation and Field Service Group Filters" (spec §18's exact required
 * order): **Congregation** first — Super-Admin picks any active congregation
 * (or "All Congregations"); every other role sees their own single
 * [fixedCongregationName]/[fixedCongregationId], shown but never editable
 * (spec §2/§9) — then **Record Type**, then **Field Service Group** (scoped
 * to whichever congregation is currently effective — see [congregationIds]),
 * then Publisher and Location as additional, still-freely-combinable
 * sub-filters (spec §7 preserves everything that already worked). Every
 * choice here applies immediately (there's nothing to "submit"); "Done" just
 * closes the sheet, and the header's own live count is the confirmation that
 * a change actually did something. Location (Province/Municipality/Barangay)
 * is no longer a section of this sheet at all — see the "TERRITORY MAP –
 * PHILIPPINES LOCATION SEARCH" spec: Province is fully automatic and
 * Municipality/Barangay live in [TerritoryMapScreen]'s own persistent filter
 * bar instead, driven by the real Philippine PSGC hierarchy rather than only
 * whichever names happen to already appear on a saved record.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TerritoryFilterSheet(
    isSuperAdmin: Boolean,
    fixedCongregationId: String?,
    fixedCongregationName: String?,
    congregationIds: Set<String>?,
    filter: TerritoryFilterState,
    resultCount: Int,
    onFilterChange: (TerritoryFilterState) -> Unit,
    onDismiss: () -> Unit,
    viewModel: TerritoryMapViewModel,
) {
    val sheetState = rememberModalBottomSheetState()
    val congregations by remember(isSuperAdmin) { if (isSuperAdmin) viewModel.congregationsFor(null) else flowOf(emptyList()) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // "The Field Service Group list must depend on the selected Congregation"
    // — [congregationIds] is the *effective* one (Super-Admin's current
    // selection, or the scoped role's fixed one — see [TerritoryMapScreen]'s
    // own call site), never a static "every congregation this role could
    // ever see" set, so switching Congregation here always refreshes this to
    // match (spec §5/§14).
    val groups by remember(congregationIds) { viewModel.groupsFor(congregationIds) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val publishers by remember(congregationIds) { viewModel.publishersFor(congregationIds) }.collectAsStateWithLifecycle(initialValue = emptyList())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // Header — title plus a live result count so every tap below has
            // an immediate, legible confirmation it did something, and a
            // Close action that needs no explanation (nothing here is
            // deferred, so there's no separate "Cancel").
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (resultCount == 1) "1 location matches" else "$resultCount locations match",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Spec §1/§2/§18 — Congregation is always the *first* filter,
            // for every role. Super-Admin gets a real dropdown ("All
            // Congregations" plus every active congregation); every other
            // role sees their own assigned congregation's name, read-only —
            // there is no field on [TerritoryFilterState] a scoped role's
            // client could even set to another congregation in the first
            // place (see that state's own doc comment).
            FilterSection(title = "Congregation") {
                if (isSuperAdmin) {
                    FilterDropdownField(
                        label = "Congregation",
                        options = congregations.map { it.id to it.name },
                        selectedId = filter.congregationId,
                        // Spec §14/§15 — changing Congregation always clears
                        // Field Service Group; a group from the previous
                        // congregation must never silently carry over.
                        onSelected = { onFilterChange(filter.copy(congregationId = it, groupId = null)) },
                    )
                } else {
                    ReadOnlyField("Congregation", fixedCongregationName ?: "—")
                }
            }

            FilterSection(title = "Record Type") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerritoryInnerFilter.entries.forEach { option ->
                        androidx.compose.material3.FilterChip(
                            selected = filter.innerFilter == option,
                            onClick = { onFilterChange(filter.copy(innerFilter = option)) },
                            label = { Text(option.label()) },
                        )
                    }
                }
            }

            // Spec §6/§18 — scoped to whichever congregation is currently
            // effective (see [congregationIds]/[groups] above); labeled with
            // its own congregation name only when Super-Admin is browsing
            // "All Congregations" at once (spec §8: "if Field Service Groups
            // have identical names in different congregations, identify them
            // using their congregation").
            FilterSection(title = "Field Service Group") {
                FilterDropdownField(
                    label = "Field Service Group",
                    options = groups.map { group ->
                        val congregationName = congregations.firstOrNull { it.id == group.congregationId }?.name
                        group.id to (if (isSuperAdmin && filter.congregationId == null && congregationName != null) "${group.name} — $congregationName" else group.name)
                    },
                    selectedId = filter.groupId,
                    onSelected = { onFilterChange(filter.copy(groupId = it)) },
                )
            }

            FilterSection(title = "Publisher") {
                FilterDropdownField(
                    label = "Publisher",
                    options = publishers.map { it.id to it.fullName },
                    selectedId = filter.publisherPersonId,
                    onSelected = { onFilterChange(filter.copy(publisherPersonId = it)) },
                )
            }

            // "Do not display a separate Province dropdown in the Territory
            // Map filter... Do not add the old Territory Map filters back" —
            // the old Location section (Province/Municipality/Barangay) used
            // to live here; Municipality/Barangay now live in the persistent
            // filter bar instead (driven by the real Philippine PSGC
            // hierarchy — see TerritoryMapScreen's own effects), and Province
            // is never user-editable anywhere, including here for Super
            // Admin — it's derived automatically from whichever Congregation
            // is chosen just above.

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    // Province is deliberately preserved on Reset (for every
                    // role, not just a scoped one, now that Super Admin's own
                    // Province is equally automatic) — it isn't one of this
                    // sheet's own fields to begin with.
                    onClick = { onFilterChange(TerritoryFilterState(province = filter.province)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset") }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
}

/** One filter group — a plain, bold section label above its content, spaced
 * generously from its siblings ([TerritoryFilterSheet]'s own 28.dp rhythm)
 * rather than ruled off with dividers; a flat, uncluttered look reads more
 * modern than a sheet full of hairlines. */
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

private fun TerritoryInnerFilter.label(): String = when (this) {
    TerritoryInnerFilter.ALL -> "All"
    TerritoryInnerFilter.BIBLE_STUDY -> "Bible Study"
    TerritoryInnerFilter.RETURN_VISIT -> "Return Visit"
    TerritoryInnerFilter.SEARCHED_INTERESTED -> "Searched Interested"
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
