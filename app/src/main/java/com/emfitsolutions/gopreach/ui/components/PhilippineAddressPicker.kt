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

@HiltViewModel
class PhilippineAddressPickerViewModel @Inject constructor(
    private val repository: PhilippineLocationRepository,
) : ViewModel() {
    private val _provinceOptions = MutableStateFlow<List<PsgcOption>>(emptyList())
    val provinceOptions: StateFlow<List<PsgcOption>> = _provinceOptions
    private val _cityOptions = MutableStateFlow<List<PsgcOption>>(emptyList())
    val cityOptions: StateFlow<List<PsgcOption>> = _cityOptions
    private val _barangayOptions = MutableStateFlow<List<PsgcOption>>(emptyList())
    val barangayOptions: StateFlow<List<PsgcOption>> = _barangayOptions

    private var searchJob: Job? = null

    // Bug fix ("selecting a province... the system is closing" — in every
    // module that uses this picker): none of these five queries had
    // anywhere to catch a failure — a plain Room/SQLite exception from any
    // one of them, left to propagate out of a bare viewModelScope.launch (or
    // out of the composable's own LaunchedEffect for the two suspend
    // functions below), had nothing downstream to stop it and took down the
    // whole app process, exactly the crash pattern already fixed elsewhere
    // in this app (see PipelineViewModel.save's own doc comment). Every path
    // here is now defensive: a failed query just leaves that dropdown
    // showing no matches instead of crashing.
    fun searchProvinces(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _provinceOptions.value = runCatching { repository.searchProvinces(query) }
                .onFailure { android.util.Log.e(TAG, "searchProvinces('$query') failed", it) }
                .getOrDefault(emptyList())
                .also { android.util.Log.d(TAG, "searchProvinces('$query') -> ${it.size} rows") }
        }
    }

    fun searchCities(provinceId: Int?, query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _cityOptions.value = runCatching { repository.searchCitiesMunicipalities(provinceId, query) }
                .onFailure { android.util.Log.e(TAG, "searchCities(province=$provinceId, '$query') failed", it) }
                .getOrDefault(emptyList())
                .also { android.util.Log.d(TAG, "searchCities(province=$provinceId, '$query') -> ${it.size} rows") }
        }
    }

    fun searchBarangays(muncityId: Int, query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _barangayOptions.value = runCatching { repository.searchBarangays(muncityId, query) }
                .onFailure { android.util.Log.e(TAG, "searchBarangays(muncity=$muncityId, '$query') failed", it) }
                .getOrDefault(emptyList())
                .also { android.util.Log.d(TAG, "searchBarangays(muncity=$muncityId, '$query') -> ${it.size} rows") }
        }
    }

    private companion object {
        const val TAG = "PhilippineAddressPicker"
    }

    suspend fun resolveProvinceId(name: String): Int? = runCatching { repository.findProvinceByName(name)?.id }.getOrNull()
    suspend fun resolveCityId(name: String, provinceId: Int?): Int? = runCatching { repository.findMuncityByName(name, provinceId)?.id }.getOrNull()
}

/**
 * "Province/City (Dropdown/required), Municipality (Dropdown/required),
 * Barangay (Dropdown/required)... The publisher will browse manually" —
 * three cascading, searchable, all-required dropdowns over the bundled PSGC
 * data (see [PhilippineLocationRepository]). "Province/City" — not just
 * "Province" — because a Highly Urbanized/Independent City (Davao City,
 * Quezon City, every city in Metro Manila, ...) sits at this same top level
 * in the PSGC, standing on its own rather than belonging to any province;
 * for one of those, "Municipality" then has exactly one selectable option
 * (the city itself, matching the PSGC's own one-city-is-both-levels
 * structure) — still a required, explicit tap, never auto-selected, so the
 * three-required-dropdowns rule holds with no silent exception. Selecting
 * Province/City narrows the Municipality search to it (leaving it blank
 * searches nationwide, for a publisher who only knows the city); selecting
 * a Municipality is required before Barangay can be searched at all —
 * there's no such thing as a barangay without a parent municipality/city.
 *
 * Callers enforce "required" themselves at submit time (this composable
 * only renders and reports changes, same division of responsibility as
 * every plain [androidx.compose.material3.OutlinedTextField] elsewhere in
 * these forms) — see each screen's own `requiredFieldsMessage` call.
 *
 * Picking a *different* Province/City clears Municipality and Barangay
 * (they'd no longer be valid children); picking a different Municipality
 * clears Barangay the same way. [province]/[cityMunicipality]/[barangay]
 * are plain names (this composable re-resolves their ids itself on first
 * composition, via [PhilippineAddressPickerViewModel.resolveProvinceId]/
 * `resolveCityId`, so a value that arrived as a name only — a loaded
 * record, or the automatic GPS fill-up — still cascades correctly);
 * [onChanged] is called with the full three-field selection on every
 * change, never just the one field that moved, so a caller can save it in
 * one shot.
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
    // becomes relevant: Province/City on first composition, Municipality
    // whenever the resolved Province/City id changes (including to "every
    // province" when cleared), Barangay whenever the resolved Municipality
    // id becomes available.
    LaunchedEffect(Unit) { viewModel.searchProvinces(provinceText) }
    LaunchedEffect(provinceId) { viewModel.searchCities(provinceId, cityText) }
    LaunchedEffect(cityId) { cityId?.let { viewModel.searchBarangays(it, barangayText) } }

    val provinceOptions by viewModel.provinceOptions.collectAsStateWithLifecycle()
    val cityOptions by viewModel.cityOptions.collectAsStateWithLifecycle()
    val barangayOptions by viewModel.barangayOptions.collectAsStateWithLifecycle()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SearchableDropdown(
            label = "Province/City",
            text = provinceText,
            options = provinceOptions,
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
            label = "Municipality",
            text = cityText,
            options = cityOptions,
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
            options = barangayOptions,
            enabled = cityId != null,
            supportingText = if (cityId == null) "Select a Municipality first" else null,
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
    options: List<PsgcOption>,
    onTextChange: (String) -> Unit,
    onOptionSelected: (PsgcOption) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
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
            Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                if (options.isEmpty()) {
                    DropdownMenuItem(text = { Text(if (text.isBlank()) "Loading…" else "No matches") }, onClick = {}, enabled = false)
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
