package com.emfitsolutions.gopreach.ui.screens.territoryassignments

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.emfitsolutions.gopreach.ui.components.GroupColorPalette
import com.emfitsolutions.gopreach.ui.components.map.NamedBoundary
import com.emfitsolutions.gopreach.ui.components.map.OsmBoundaryMap

private enum class AllTerritoriesMode(val label: String) { MAP("Map View"), LIST("List View") }

/** One barangay across every Field Service Group, with the Group it belongs
 * to (for color + the province/municipality a boundary lookup needs). */
private data class AllTerritoriesEntry(
    val barangay: GroupTerritoryBarangay,
    val groupName: String,
    val groupColorHex: String?,
)

/**
 * "Show All Territories" — every barangay across every Field Service Group
 * in one place, as either a single combined map (each barangay outlined in
 * its own Group's color) or a spreadsheet-style list (Municipality, then one
 * column per Group, following the same "41475 - Territories" layout
 * Secretaries already print/share — see the reference image this was built
 * from) with each column's header tinted by that Group's own
 * [GroupColorPalette] color.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTerritoriesDialog(
    rows: List<GroupTerritoryRow>,
    onDismiss: () -> Unit,
    viewModel: TerritoryAssignmentsViewModel = hiltViewModel(),
) {
    var mode by remember { mutableStateOf(AllTerritoriesMode.MAP) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("All Territories") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close")
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        AllTerritoriesMode.entries.forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = { mode = m },
                                label = { Text(m.label) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (mode) {
                    AllTerritoriesMode.MAP -> AllTerritoriesMapView(rows = rows, viewModel = viewModel, modifier = Modifier.fillMaxSize())
                    AllTerritoriesMode.LIST -> AllTerritoriesListView(rows = rows, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun AllTerritoriesMapView(
    rows: List<GroupTerritoryRow>,
    viewModel: TerritoryAssignmentsViewModel,
    modifier: Modifier,
) {
    val entries = remember(rows) {
        rows.flatMap { row ->
            row.municipalities.flatMap { m ->
                m.barangays.map { b ->
                    AllTerritoriesEntry(
                        barangay = GroupTerritoryBarangay(row.provinceName, m.assignment.muncityName, b.barangayName),
                        groupName = row.group?.name ?: "Unknown Group",
                        groupColorHex = row.group?.color,
                    )
                }
            }
        }
    }
    var boundaries by remember(entries) { mutableStateOf<List<NamedBoundary>?>(null) }
    var drillDown by remember(entries) { mutableStateOf<AllTerritoriesEntry?>(null) }

    LaunchedEffect(entries) {
        boundaries = entries.mapNotNull { e ->
            viewModel.boundaryGeometry(e.barangay.province, e.barangay.municipality, e.barangay.barangayName)
                ?.let { geometryJson -> NamedBoundary(e.barangay.barangayName, geometryJson, e.groupColorHex) }
        }
    }

    Box(modifier = modifier) {
        val resolved = boundaries
        when {
            resolved == null -> Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            resolved.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No boundary map available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> OsmBoundaryMap(
                boundaries = resolved,
                onBoundaryClick = { name -> drillDown = entries.find { it.barangay.barangayName == name } },
                exportTitle = "All Territories",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    drillDown?.let { e ->
        BarangayBoundaryDialog(
            province = e.barangay.province,
            municipality = e.barangay.municipality,
            barangayName = e.barangay.barangayName,
            boundaryColorHex = e.groupColorHex,
            onDismiss = { drillDown = null },
        )
    }
}

/** One Municipality's own row in the list — [cells] is one entry per Group
 * (in the same order for every municipality), empty [Cell.barangayNames]
 * when that Group has nothing assigned there. */
private data class MunicipalityBlock(val municipalityName: String, val cells: List<Cell>)
private data class Cell(val groupName: String, val colorHex: String?, val barangayNames: List<String>)

@Composable
private fun AllTerritoriesListView(rows: List<GroupTerritoryRow>, modifier: Modifier) {
    val groups = remember(rows) {
        rows.mapNotNull { it.group }.distinctBy { it.id }.sortedBy { it.name }
    }
    val blocks = remember(rows, groups) {
        val municipalityNames = rows.flatMap { row -> row.municipalities.map { it.assignment.muncityName } }.distinct().sortedBy { it }
        municipalityNames.map { muniName ->
            val cells = groups.map { group ->
                val barangayNames = rows.find { it.group?.id == group.id }
                    ?.municipalities
                    ?.find { it.assignment.muncityName == muniName }
                    ?.barangays
                    ?.map { it.barangayName }
                    ?: emptyList()
                Cell(group.name, group.color, barangayNames)
            }
            MunicipalityBlock(muniName, cells)
        }
    }

    if (blocks.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "No territory assignments yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp),
    ) {
        items(blocks, key = { it.municipalityName }) { block ->
            Column {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        block.municipalityName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    block.cells.forEach { cell ->
                        val swatch = GroupColorPalette.parseHex(cell.colorHex ?: GroupColorPalette.UNASSIGNED_COLOR)
                        Column(modifier = Modifier.width(150.dp).padding(end = 6.dp)) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(swatch)
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    cell.groupName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                                    .background(swatch.copy(alpha = 0.16f))
                                    .padding(8.dp),
                            ) {
                                if (cell.barangayNames.isEmpty()) {
                                    Text(
                                        "—",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    cell.barangayNames.forEach { name ->
                                        Text(name, style = MaterialTheme.typography.bodySmall)
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
