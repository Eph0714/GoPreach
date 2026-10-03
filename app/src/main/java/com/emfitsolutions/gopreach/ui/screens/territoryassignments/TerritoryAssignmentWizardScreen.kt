package com.emfitsolutions.gopreach.ui.screens.territoryassignments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.repository.PsgcOption
import com.emfitsolutions.gopreach.data.repository.TerritoryAssignmentResult
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import com.emfitsolutions.gopreach.ui.components.requiredFieldsMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

private const val STEP_GROUP = 0
private const val STEP_MUNICIPALITY = 1
private const val STEP_BARANGAYS = 2
private const val STEP_CONFIRM = 3
private const val STEP_COUNT = 4

/**
 * Add/Edit Territory Assignment — a dedicated full-screen wizard rather than
 * [com.emfitsolutions.gopreach.ui.components.FormDialog] (checked that
 * component's own sizing — a fixed-height scrollable dialog — against what
 * this flow needs: a Group pick, a Province/Municipality cascade, a
 * Barangay checklist that can run into the hundreds with per-row
 * explanations, then a confirmation summary; four genuinely sequential
 * concerns is a wizard shape, not a compact form). [assignmentId] null means
 * Add; non-null means Edit (congregationId becomes immutable once loaded,
 * matching [com.emfitsolutions.gopreach.data.repository
 * .TerritoryAssignmentRepository.updateAssignment]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerritoryAssignmentWizardScreen(
    assignmentId: String?,
    fixedCongregationId: String?,
    currentPersonId: String,
    onDone: () -> Unit,
    viewModel: TerritoryAssignmentWizardViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableStateOf(STEP_GROUP) }
    // Reset the instant the step changes — advancing to a new step should
    // never carry over "you tried to skip this" styling from the step before.
    var nextTappedWhileInvalid by remember(step) { mutableStateOf(false) }
    var isLoadingExisting by remember { mutableStateOf(assignmentId != null) }

    // Super-Admin only (fixedCongregationId == null) and only for a brand-new
    // assignment — editing always keeps the loaded assignment's own
    // congregationId, never offers a picker (congregationId is immutable on
    // edit).
    var pickedCongregationId by rememberSaveable { mutableStateOf(fixedCongregationId) }
    val congregationId = fixedCongregationId ?: pickedCongregationId
    val congregations by viewModel.congregations.collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var provinceOption by remember { mutableStateOf<PsgcOption?>(null) }
    var muncityOption by remember { mutableStateOf<PsgcOption?>(null) }
    var selectedBarangays by remember { mutableStateOf<List<PsgcOption>>(emptyList()) }

    val groups by (congregationId?.let { viewModel.groupsFor(it) } ?: flowOf(emptyList()))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedGroup = groups.firstOrNull { it.id == selectedGroupId }

    // Edit pre-fill — loads once, before the user can interact with any step.
    LaunchedEffect(assignmentId) {
        if (assignmentId == null) {
            isLoadingExisting = false
            return@LaunchedEffect
        }
        val assignment = viewModel.getAssignment(assignmentId)
        if (assignment != null) {
            pickedCongregationId = assignment.congregationId
            selectedGroupId = assignment.groupId
            provinceOption = PsgcOption(assignment.provinceId, assignment.provinceName)
            muncityOption = PsgcOption(assignment.muncityId, assignment.muncityName)
            selectedBarangays = viewModel.existingBarangaysFor(assignmentId)
                .map { PsgcOption(it.barangayId, it.barangayName) }
                .sortedBy { it.name }
        }
        isLoadingExisting = false
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showToast = rememberActionToast()
    var hasConfirmed by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saveResult) {
        when (val result = uiState.saveResult) {
            is TerritoryAssignmentResult.Success -> {
                showToast(if (assignmentId == null) "Territory assignment added." else "Territory assignment saved.")
                viewModel.consumeSaveResult()
                onDone()
            }
            is TerritoryAssignmentResult.Conflict, is TerritoryAssignmentResult.Offline, is TerritoryAssignmentResult.Error -> {
                hasConfirmed = false
            }
            null -> Unit
        }
    }

    fun submit() {
        if (hasConfirmed) return
        hasConfirmed = true
        val cid = congregationId ?: return
        val group = selectedGroup ?: return
        val province = provinceOption ?: return
        val muncity = muncityOption ?: return
        if (assignmentId == null) {
            viewModel.createAssignment(
                cid, group.id, group.name, province.id, province.name, muncity.id, muncity.name, selectedBarangays, currentPersonId,
            )
        } else {
            viewModel.updateAssignment(
                assignmentId, cid, group.id, group.name, province.id, province.name, muncity.id, muncity.name, selectedBarangays, currentPersonId,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (assignmentId == null) "Add Territory Assignment" else "Edit Territory Assignment") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (isLoadingExisting) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = { (step + 1f) / STEP_COUNT },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Step ${step + 1} of $STEP_COUNT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )

            // Bug fix — "app closes after clicking Next after selecting
            // municipality": the Barangays step's own LazyColumn used to sit
            // inside this shared container's Modifier.verticalScroll(), which
            // Compose explicitly disallows (a scrollable-in-scrollable nests
            // an unbounded height constraint into the LazyColumn and crashes
            // with IllegalStateException the instant that step renders — see
            // CheckScrollableContainerConstraints). Steps 1/2/4 are short
            // forms that still want to scroll as a plain Column; only the
            // Barangays step gets the bounded, unscrolled container its own
            // LazyColumn needs (that step scrolls itself).
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                when (step) {
                    STEP_GROUP -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        GroupStep(
                            congregationId = congregationId,
                            fixedCongregationId = fixedCongregationId,
                            isEditing = assignmentId != null,
                            congregations = congregations,
                            onCongregationSelected = { pickedCongregationId = it; selectedGroupId = null },
                            groups = groups,
                            selectedGroupId = selectedGroupId,
                            onGroupSelected = { selectedGroupId = it },
                        )
                    }
                    STEP_MUNICIPALITY -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        MunicipalityStep(
                            viewModel = viewModel,
                            province = provinceOption,
                            onProvinceSelected = { provinceOption = it; muncityOption = null },
                            muncity = muncityOption,
                            onMuncitySelected = { muncityOption = it },
                        )
                    }
                    STEP_BARANGAYS -> BarangaysStep(
                        viewModel = viewModel,
                        congregationId = congregationId,
                        assignmentId = assignmentId,
                        muncity = muncityOption,
                        selected = selectedBarangays,
                        onSelectedChange = { selectedBarangays = it },
                    )
                    STEP_CONFIRM -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ConfirmStep(
                            group = selectedGroup,
                            province = provinceOption,
                            muncity = muncityOption,
                            barangays = selectedBarangays,
                            saveResult = uiState.saveResult,
                        )
                    }
                }
            }

            val nextEnabled = when (step) {
                STEP_GROUP -> congregationId != null && selectedGroupId != null
                STEP_MUNICIPALITY -> muncityOption != null
                STEP_BARANGAYS -> selectedBarangays.isNotEmpty()
                else -> true
            }
            val stepErrorMessage = when (step) {
                STEP_GROUP -> requiredFieldsMessage(
                    "Congregation" to (congregationId != null),
                    "Field Service Group" to (selectedGroupId != null),
                )
                STEP_MUNICIPALITY -> requiredFieldsMessage("Municipality / City" to (muncityOption != null))
                STEP_BARANGAYS -> requiredFieldsMessage("At least one barangay" to selectedBarangays.isNotEmpty())
                else -> null
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (step > STEP_GROUP) {
                    TextButton(onClick = { step-- }, enabled = !uiState.isSaving) { Text("Back") }
                } else {
                    TextButton(onClick = onDone, enabled = !uiState.isSaving) { Text("Cancel") }
                }
                if (step < STEP_CONFIRM) {
                    TextButton(onClick = { if (nextEnabled) step++ else nextTappedWhileInvalid = true }) { Text("Next") }
                } else {
                    TextButton(onClick = ::submit, enabled = !hasConfirmed && !uiState.isSaving) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        }
                        Text(if (assignmentId == null) "Create Assignment" else "Save Changes")
                    }
                }
            }
            if (step < STEP_CONFIRM && stepErrorMessage != null && !nextEnabled && nextTappedWhileInvalid) {
                // Only surfaced once Next has actually been tapped while
                // incomplete (nextTappedWhileInvalid) — not live on every
                // recomposition, which would show a scary red error the
                // instant this step is even opened, before the user has had
                // a chance to fill anything in.
                Text(
                    stepErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupStep(
    congregationId: String?,
    fixedCongregationId: String?,
    isEditing: Boolean,
    congregations: List<Congregation>,
    onCongregationSelected: (String) -> Unit,
    groups: List<Group>,
    selectedGroupId: String?,
    onGroupSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Field Service Group", style = MaterialTheme.typography.titleMedium)
        if (fixedCongregationId == null && !isEditing) {
            SimpleDropdown(
                label = "Congregation",
                selectedLabel = congregations.firstOrNull { it.id == congregationId }?.name ?: "",
                options = congregations.map { it.id to it.name },
                onSelected = onCongregationSelected,
            )
        }
        if (congregationId == null) {
            Text("Select a congregation first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (groups.isEmpty()) {
            Text("This congregation has no active Field Service Groups yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            SimpleDropdown(
                label = "Field Service Group",
                selectedLabel = groups.firstOrNull { it.id == selectedGroupId }?.name ?: "",
                options = groups.map { it.id to it.name },
                onSelected = onGroupSelected,
            )
        }
    }
}

@Composable
private fun MunicipalityStep(
    viewModel: TerritoryAssignmentWizardViewModel,
    province: PsgcOption?,
    onProvinceSelected: (PsgcOption) -> Unit,
    muncity: PsgcOption?,
    onMuncitySelected: (PsgcOption) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Municipality / City", style = MaterialTheme.typography.titleMedium)
        PsgcSearchField(
            label = "Province",
            selected = province,
            enabled = true,
            search = { query -> viewModel.searchProvinces(query) },
            onSelected = onProvinceSelected,
        )
        PsgcSearchField(
            label = "Municipality / City",
            selected = muncity,
            enabled = province != null,
            search = { query -> province?.let { viewModel.searchMunicipalities(it.id, query) } ?: emptyList() },
            onSelected = onMuncitySelected,
        )
        if (province == null) {
            Text("Select a province to browse its municipalities.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BarangaysStep(
    viewModel: TerritoryAssignmentWizardViewModel,
    congregationId: String?,
    assignmentId: String?,
    muncity: PsgcOption?,
    selected: List<PsgcOption>,
    onSelectedChange: (List<PsgcOption>) -> Unit,
) {
    var allBarangays by remember(muncity?.id) { mutableStateOf<List<PsgcOption>>(emptyList()) }
    var taken by remember(muncity?.id) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var isLoading by remember(muncity?.id) { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }

    LaunchedEffect(muncity?.id, congregationId) {
        if (muncity == null || congregationId == null) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        allBarangays = viewModel.searchBarangays(muncity.id, "")
        taken = viewModel.takenBarangays(congregationId, assignmentId)
        isLoading = false
    }

    val selectedIds = selected.map { it.id }.toSet()
    val visible = allBarangays.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
    val selectableVisible = visible.filter { it.id !in taken }

    // fillMaxSize, not just wrap-content — this Column sits directly in the
    // caller's bounded (non-scrolling) container, and its LazyColumn below
    // needs a real bounded max height via weight(1f) rather than fillMaxSize
    // alone (see this screen's own doc comment on why Barangays doesn't
    // share the other steps' Modifier.verticalScroll(Column)).
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Barangays in ${muncity?.name ?: "—"}", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search barangay") },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                onSelectedChange((selected + selectableVisible).distinctBy { it.id })
            }) { Text("Select All") }
            OutlinedButton(onClick = {
                val visibleIds = visible.map { it.id }.toSet()
                onSelectedChange(selected.filterNot { it.id in visibleIds })
            }) { Text("Clear Selection") }
        }
        Text(
            "${selected.size} barangay${if (selected.size == 1) "" else "s"} selected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        } else if (visible.isEmpty()) {
            Text("No barangays match this search.", style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(visible, key = { it.id }) { barangay ->
                    val takenBy = taken[barangay.id]
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = barangay.id in selectedIds,
                                enabled = takenBy == null,
                                onCheckedChange = { checked ->
                                    onSelectedChange(
                                        if (checked) (selected + barangay).distinctBy { it.id }
                                        else selected.filterNot { it.id == barangay.id },
                                    )
                                },
                            )
                            Text(
                                barangay.name,
                                modifier = Modifier.padding(top = 14.dp),
                                color = if (takenBy != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (takenBy != null) {
                            Text(
                                "Already assigned to $takenBy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 48.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmStep(
    group: Group?,
    province: PsgcOption?,
    muncity: PsgcOption?,
    barangays: List<PsgcOption>,
    saveResult: TerritoryAssignmentResult?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Confirm", style = MaterialTheme.typography.titleMedium)
        Text("Field Service Group: ${group?.name ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text("Province: ${province?.name ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text("Municipality / City: ${muncity?.name ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "${barangays.size} barangay${if (barangays.size == 1) "" else "s"}: ${barangays.sortedBy { it.name }.joinToString(", ") { it.name }}",
            style = MaterialTheme.typography.bodyMedium,
        )
        when (saveResult) {
            is TerritoryAssignmentResult.Conflict ->
                Text(
                    "${saveResult.barangayName} is already assigned to ${saveResult.takenByGroupName} in this congregation.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            is TerritoryAssignmentResult.Offline ->
                Text(saveResult.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            is TerritoryAssignmentResult.Error ->
                Text(saveResult.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            else -> Unit
        }
    }
}

/** Minimal search-as-you-type PSGC field — debounced local search over
 * [viewModel]'s suspend lookups, no persistent dropdown state machine (this
 * module's own, simpler counterpart to [com.emfitsolutions.gopreach.ui
 * .components.PhilippineAddressPicker]'s private SearchableDropdown, which
 * isn't reusable here since it only ever surfaces plain names — see this
 * screen's own file-level doc comment). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PsgcSearchField(
    label: String,
    selected: PsgcOption?,
    enabled: Boolean,
    search: suspend (String) -> List<PsgcOption>,
    onSelected: (PsgcOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(selected?.id) { mutableStateOf(selected?.name ?: "") }
    var options by remember { mutableStateOf<List<PsgcOption>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query, expanded, enabled) {
        if (!expanded || !enabled) return@LaunchedEffect
        isLoading = true
        delay(250)
        options = runCatching { search(query) }.getOrDefault(emptyList())
        isLoading = false
    }

    val showMenu = expanded && enabled
    ExposedDropdownMenuBox(expanded = showMenu, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; expanded = true },
            label = { Text(label) },
            enabled = enabled,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMenu) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            when {
                isLoading -> DropdownMenuItem(text = { Text("Searching…") }, onClick = {})
                options.isEmpty() -> DropdownMenuItem(text = { Text("No matches") }, onClick = {})
                else -> options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name) },
                        onClick = {
                            query = option.name
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelected(id); expanded = false })
            }
        }
    }
}
