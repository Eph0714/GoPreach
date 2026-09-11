package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.repository.PhilippineLocationRepository
import com.emfitsolutions.gopreach.data.repository.PsgcOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One dropdown level's load state (spec §7 — "Loading provinces... /
 * Complete Province List", and a distinguishable failure state instead of a
 * silent empty list): [isLoading] only while a query is actually in flight,
 * [isError] when the last one threw, [options] holds the last successful
 * result (kept on a failed retry, rather than wiped, so a transient failure
 * doesn't throw away an already-loaded list). */
data class PsgcDropdownState(
    val options: List<PsgcOption> = emptyList(),
    val isLoading: Boolean = false,
    val isError: Boolean = false,
)

@HiltViewModel
class PhilippineAddressPickerViewModel @Inject constructor(
    private val repository: PhilippineLocationRepository,
) : ViewModel() {
    private val _provinceState = MutableStateFlow(PsgcDropdownState())
    val provinceState: StateFlow<PsgcDropdownState> = _provinceState
    private val _cityState = MutableStateFlow(PsgcDropdownState())
    val cityState: StateFlow<PsgcDropdownState> = _cityState
    private val _barangayState = MutableStateFlow(PsgcDropdownState())
    val barangayState: StateFlow<PsgcDropdownState> = _barangayState

    // Bug fix ("I cannot see Province/City..." recurring intermittently,
    // specifically when editing an existing record): all three levels used
    // to share ONE `searchJob` field. Editing a Publisher resolves and sets
    // Province, then (near-simultaneously, once its id resolves) triggers
    // the City search — which canceled the still-in-flight Province search
    // sharing that same field, leaving Province's dropdown permanently empty
    // for that composition. Each level now cancels only its own prior
    // in-flight query, never another level's.
    private var provinceJob: Job? = null
    private var cityJob: Job? = null
    private var barangayJob: Job? = null

    private var lastProvinceQuery = ""
    private var lastCityQuery: Pair<Int?, String>? = null
    private var lastBarangayQuery: Pair<Int, String>? = null

    // Bug fix ("selecting a province... the system is closing" — in every
    // module that uses this picker): none of these five queries had
    // anywhere to catch a failure — a plain Room/SQLite exception from any
    // one of them, left to propagate out of a bare viewModelScope.launch (or
    // out of the composable's own LaunchedEffect for the two suspend
    // functions below), had nothing downstream to stop it and took down the
    // whole app process, exactly the crash pattern already fixed elsewhere
    // in this app (see PipelineViewModel.save's own doc comment). Every path
    // here is now defensive: a failed query surfaces as [PsgcDropdownState
    // .isError] (spec §7 — a retry, not a crash or a silently-empty list)
    // instead of crashing.
    fun searchProvinces(query: String) {
        lastProvinceQuery = query
        provinceJob?.cancel()
        provinceJob = viewModelScope.launch {
            _provinceState.value = _provinceState.value.copy(isLoading = true, isError = false)
            runCatching { repository.searchProvinces(query) }
                .onSuccess { rows ->
                    android.util.Log.d(TAG, "searchProvinces('$query') -> ${rows.size} rows")
                    _provinceState.value = PsgcDropdownState(options = rows, isLoading = false, isError = false)
                }
                .onFailure {
                    android.util.Log.e(TAG, "searchProvinces('$query') failed", it)
                    _provinceState.value = _provinceState.value.copy(isLoading = false, isError = true)
                }
        }
    }

    fun retryProvinces() = searchProvinces(lastProvinceQuery)

    fun searchCities(provinceId: Int?, query: String) {
        lastCityQuery = provinceId to query
        cityJob?.cancel()
        cityJob = viewModelScope.launch {
            _cityState.value = _cityState.value.copy(isLoading = true, isError = false)
            runCatching { repository.searchCitiesMunicipalities(provinceId, query) }
                .onSuccess { rows ->
                    android.util.Log.d(TAG, "searchCities(province=$provinceId, '$query') -> ${rows.size} rows")
                    _cityState.value = PsgcDropdownState(options = rows, isLoading = false, isError = false)
                }
                .onFailure {
                    android.util.Log.e(TAG, "searchCities(province=$provinceId, '$query') failed", it)
                    _cityState.value = _cityState.value.copy(isLoading = false, isError = true)
                }
        }
    }

    fun retryCities() = lastCityQuery?.let { (provinceId, query) -> searchCities(provinceId, query) }

    fun searchBarangays(muncityId: Int, query: String) {
        lastBarangayQuery = muncityId to query
        barangayJob?.cancel()
        barangayJob = viewModelScope.launch {
            _barangayState.value = _barangayState.value.copy(isLoading = true, isError = false)
            runCatching { repository.searchBarangays(muncityId, query) }
                .onSuccess { rows ->
                    android.util.Log.d(TAG, "searchBarangays(muncity=$muncityId, '$query') -> ${rows.size} rows")
                    _barangayState.value = PsgcDropdownState(options = rows, isLoading = false, isError = false)
                }
                .onFailure {
                    android.util.Log.e(TAG, "searchBarangays(muncity=$muncityId, '$query') failed", it)
                    _barangayState.value = _barangayState.value.copy(isLoading = false, isError = true)
                }
        }
    }

    fun retryBarangays() = lastBarangayQuery?.let { (muncityId, query) -> searchBarangays(muncityId, query) }

    private companion object {
        const val TAG = "PhilippineAddressPicker"
    }

    suspend fun resolveProvinceId(name: String): Int? = runCatching { repository.findProvinceByName(name)?.id }.getOrNull()
    suspend fun resolveCityId(name: String, provinceId: Int?): Int? = runCatching { repository.findMuncityByName(name, provinceId)?.id }.getOrNull()
}

/**
 * Three cascading, searchable, all-required dropdowns — Province,
 * Municipality/City, Barangay — over the bundled PSGC data (see
 * [PhilippineLocationRepository]). The Province level shows only real
 * Philippine provinces (plus one pragmatic "Metro Manila" entry standing in
 * for NCR, which has no province of its own in the PSGC — see
 * `rebuild_psgc.js`'s doc comment history / [PhilippineLocationRepository]);
 * every Highly Urbanized/Independent City (Davao City, Cebu City, every
 * city in Metro Manila, ...) is reclassified under its real geographic
 * province (or under Metro Manila for NCR) as a normal Municipality/City
 * option, never shown as its own top-level entry. No PSGC code, region
 * name, or other geographic-code detail is ever shown to the user — only
 * plain names; codes are only ever used internally to scope the next
 * dropdown's query.
 *
 * Selecting a Province narrows the Municipality search to it (leaving it
 * blank searches nationwide, for a publisher who only knows the city);
 * selecting a Municipality is required before Barangay can be searched at
 * all — there's no such thing as a barangay without a parent
 * municipality/city.
 *
 * Callers enforce "required" themselves at submit time (this composable
 * only renders and reports changes, same division of responsibility as
 * every plain [androidx.compose.material3.OutlinedTextField] elsewhere in
 * these forms) — see each screen's own `requiredFieldsMessage` call.
 *
 * Picking a *different* Province clears Municipality and Barangay (they'd
 * no longer be valid children); picking a different Municipality clears
 * Barangay the same way. [province]/[cityMunicipality]/[barangay] are plain
 * names (this composable re-resolves their ids itself on first composition,
 * via [PhilippineAddressPickerViewModel.resolveProvinceId]/`resolveCityId`,
 * so a value that arrived as a name only — a loaded record, or the
 * automatic GPS fill-up — still cascades correctly); [onChanged] is called
 * with the full three-field selection on every change, never just the one
 * field that moved, so a caller can save it in one shot.
 */
@Composable
fun PhilippineAddressPicker(
    province: String?,
    cityMunicipality: String?,
    barangay: String?,
    onChanged: (province: String?, cityMunicipality: String?, barangay: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhilippineAddressPickerViewModel = hiltViewModel(),
) {
    var provinceId by remember { mutableStateOf<Int?>(null) }
    var cityId by remember { mutableStateOf<Int?>(null) }
    var provinceText by remember(province) { mutableStateOf(province.orEmpty()) }
    var cityText by remember(cityMunicipality) { mutableStateOf(cityMunicipality.orEmpty()) }
    var barangayText by remember(barangay) { mutableStateOf(barangay.orEmpty()) }

    // Re-hydrate ids from whatever names this composable was handed (a
    // loaded record, or a fresh automatic fill-up) so City/Barangay search
    // is correctly scoped from the very first keystroke, not just after the
    // publisher re-picks something by hand.
    LaunchedEffect(province, cityMunicipality) {
        provinceId = province?.let { viewModel.resolveProvinceId(it) }
        cityId = cityMunicipality?.let { viewModel.resolveCityId(it, provinceId) }
    }

    // Bug fix ("I cannot see Province/City and other address related..."):
    // each dropdown's option list used to stay empty until the publisher
    // typed at least one character into it — nothing ever populated it just
    // from opening the screen or tapping the field, which reads as "there's
    // nothing here at all." Pre-loads every level's options the moment it
    // becomes relevant: Province on first composition, Municipality/City
    // whenever the resolved Province id becomes available (it stays
    // disabled — see [SearchableDropdown]'s `enabled` — until then, so
    // there's nothing to load before that), Barangay whenever the resolved
    // Municipality id becomes available.
    LaunchedEffect(Unit) { viewModel.searchProvinces(provinceText) }
    LaunchedEffect(provinceId) { provinceId?.let { viewModel.searchCities(it, cityText) } }
    LaunchedEffect(cityId) { cityId?.let { viewModel.searchBarangays(it, barangayText) } }

    val provinceState by viewModel.provinceState.collectAsStateWithLifecycle()
    val cityState by viewModel.cityState.collectAsStateWithLifecycle()
    val barangayState by viewModel.barangayState.collectAsStateWithLifecycle()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SearchableDropdown(
            label = "Province",
            text = provinceText,
            state = provinceState,
            onRetry = viewModel::retryProvinces,
            onTextChange = {
                provinceText = it
                viewModel.searchProvinces(it)
            },
            onOptionSelected = { option ->
                provinceText = option.name
                provinceId = option.id
                // A different province invalidates whatever city/barangay
                // was picked under the old one.
                cityText = ""; cityId = null; barangayText = ""
                onChanged(option.name, null, null)
            },
        )
        SearchableDropdown(
            label = "Municipality / City",
            text = cityText,
            state = cityState,
            onRetry = viewModel::retryCities,
            enabled = provinceId != null,
            supportingText = if (provinceId == null) "Select Province first" else null,
            onTextChange = {
                cityText = it
                viewModel.searchCities(provinceId, it)
            },
            onOptionSelected = { option ->
                cityText = option.name
                cityId = option.id
                barangayText = ""
                onChanged(provinceText.ifBlank { null }, option.name, null)
            },
        )
        SearchableDropdown(
            label = "Barangay",
            text = barangayText,
            state = barangayState,
            onRetry = viewModel::retryBarangays,
            enabled = cityId != null,
            supportingText = if (cityId == null) "Select Municipality / City first" else null,
            onTextChange = {
                barangayText = it
                cityId?.let { id -> viewModel.searchBarangays(id, it) }
            },
            onOptionSelected = { option ->
                barangayText = option.name
                onChanged(provinceText.ifBlank { null }, cityText.ifBlank { null }, option.name)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchableDropdown(
    label: String,
    text: String,
    state: PsgcDropdownState,
    onRetry: () -> Unit,
    onTextChange: (String) -> Unit,
    onOptionSelected: (PsgcOption) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    val options = state.options
    var expanded by remember { mutableStateOf(false) }
    // Bug fix — see PhilippineAddressPicker's own doc comment: [options]
    // being empty no longer hides the menu outright, only shows nothing
    // filtered under it; the menu opening at all is what makes it obvious
    // there's a real dropdown here, "still loading"/"nothing matches" and
    // all, rather than tapping the field silently doing nothing.
    val showMenu = expanded && enabled
    ExposedDropdownMenuBox(expanded = showMenu, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                onTextChange(it)
                expanded = true
            },
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            enabled = enabled,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            // Bug fix ("selecting a province... the system is closing"):
            // wrapping ExposedDropdownMenuDefaults.TrailingIcon in a nested
            // IconButton — belt-and-suspenders for "tap to open" — doubled
            // up the click/pointer-input handling this icon already owns as
            // a direct child of an ExposedDropdownMenuBox, which crashed on
            // exactly the interaction that both closes the popup (selecting
            // an item) and settles focus back on the field at once. The
            // focus listener below already opens the menu on a plain tap
            // into the field itself, so this icon only ever needs to be the
            // plain, unwrapped indicator Material3 expects here.
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMenu) },
            // Bug fix: tapping into this field used to just place a cursor
            // — nothing ever set [expanded] to true until the first
            // keystroke, so a publisher who tapped it to browse (rather
            // than type) saw no dropdown appear at all. Opening on focus
            // means the pre-loaded options (see the LaunchedEffects above)
            // are visible immediately.
            modifier = Modifier.fillMaxWidth().menuAnchor().onFocusChanged { focusState ->
                if (enabled && focusState.isFocused) expanded = true
            },
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            // "Fix Incomplete Province Dropdown" §7 — a failed query now
            // shows a clear error with a Retry action instead of leaving the
            // dropdown looking like an empty "no matches" list forever (the
            // two used to be indistinguishable). [options] is also no longer
            // artificially capped at 50 (see [PsgcDao]'s own doc comment),
            // so a fully-loaded list can now run to hundreds of rows (e.g.
            // Manila's 897 barangays).
            //
            // Bug fix ("the form will close" / crash when tapping this
            // dropdown, reproduced live: IllegalStateException "Asking for
            // intrinsic measurements of SubcomposeLayout layouts is not
            // supported... This includes... lazy lists"): this menu content
            // used to be a LazyColumn — itself built on SubcomposeLayout —
            // nested inside this ExposedDropdownMenuBox's own popup, whose
            // Material3-internal `exposedDropdownSize` sizing logic queries
            // intrinsic measurements of that popup content to match the
            // anchor's width. Querying intrinsics through a SubcomposeLayout
            // is exactly what Compose refuses to do, crashing the instant
            // the popup opened — reproduced specifically on a full-screen
            // host (Enroll Publisher/Congregation), not yet inside the
            // dialog-hosted callers, but the same latent conflict. A plain
            // `Column` (scrollable, still height-capped at 280dp so a
            // several-hundred-row barangay list doesn't grow unbounded) has
            // no SubcomposeLayout in its measurement path, so the same
            // intrinsic query resolves normally. Every row is a lightweight
            // `Text`-only `DropdownMenuItem`, so composing the full list
            // up front (this menu's content only exists while it's open
            // anyway) is not the LazyColumn's original virtualization was
            // guarding against anything expensive per row.
            Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                when {
                    state.isError -> DropdownMenuItem(
                        text = { Text("Couldn't load the list. Tap to retry.") },
                        onClick = onRetry,
                    )
                    state.isLoading && options.isEmpty() -> DropdownMenuItem(text = { Text("Loading…") }, onClick = {}, enabled = false)
                    options.isEmpty() -> DropdownMenuItem(text = { Text(if (text.isBlank()) "Loading…" else "No matches") }, onClick = {}, enabled = false)
                }
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
