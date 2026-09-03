package com.emfitsolutions.gopreach.ui.screens.territories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.location.formatCoordinatesDms
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.ui.components.openCoordinatesInMaps

/**
 * "Territory Module" — no longer a Territory Master File CRUD (Add/Edit/
 * Delete a Territory entity); a read-only directory of every Searching/
 * Return Visit/Bible Study record that has a saved GPS location, searchable
 * by name or location, e.g. "Name: Richard Ortega. Status: Bible Study.
 * Location: F5M2+57Q, 1, Bayombong, Nueva Vizcaya." Tapping a row opens that
 * location in Google Maps (or whatever the device offers for a `geo:` URI) —
 * same as every other saved coordinate in this app (see
 * [com.emfitsolutions.gopreach.ui.components.ClickableCoordinatesText]),
 * rather than drawing its own embedded map, consistent with how Find
 * Location/Share Location already hand off actual map rendering to the
 * device's own Maps app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerritoryMapScreen(
    fixedCongregationId: String?,
    onBack: () -> Unit,
    viewModel: TerritoryMapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val rowsFlow = remember(fixedCongregationId) { viewModel.rowsFor(fixedCongregationId) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var searchQuery by remember { mutableStateOf("") }

    // Matches the resolved (reverse-geocoded) address once it's in, but also
    // the person's own typed address and raw coordinates, so a search never
    // has to wait on that network lookup landing for every row first.
    val filtered = remember(rows, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            rows
        } else {
            rows.filter { row ->
                row.person.name.contains(query, ignoreCase = true) ||
                    row.person.address.contains(query, ignoreCase = true) ||
                    row.congregationName.contains(query, ignoreCase = true) ||
                    row.resolvedLocation?.contains(query, ignoreCase = true) == true ||
                    (row.person.gpsLat != null && row.person.gpsLng != null &&
                        formatCoordinatesDms(row.person.gpsLat, row.person.gpsLng).contains(query, ignoreCase = true))
            }
        }
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
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by Name or Location") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                visualTransformation = VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (rows.isEmpty()) "No saved locations yet — every Searching, Return Visit, and Bible Study record with a saved location will show up here." else "No matches for \"$searchQuery\".",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.person.id }) { row ->
                        val lat = row.person.gpsLat
                        val lng = row.person.gpsLng
                        Card(
                            modifier = Modifier.fillMaxWidth().let {
                                if (lat != null && lng != null) it.clickable { openCoordinatesInMaps(context, lat, lng, row.person.name) } else it
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(row.person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Status: ${row.person.pipelineStage.statusLabel()}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Location: ${row.resolvedLocation ?: (if (lat != null && lng != null) formatCoordinatesDms(lat, lng) else "—")}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text("Congregation: ${row.congregationName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun PipelineStage.statusLabel(): String = when (this) {
    PipelineStage.SEARCHING -> "Searching"
    PipelineStage.RETURN_VISIT -> "Return Visit"
    PipelineStage.BIBLE_STUDY -> "Bible Study"
}
