package com.emfitsolutions.gopreach.ui.screens.territories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.location.LocationTracker
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

/** One row on the Territory Map — every Searching/Return Visit/Bible Study
 * record that has a saved GPS location, regardless of which of the three
 * stages it's currently at. [resolvedLocation] is the on-device reverse-
 * geocoded address for [InterestedPerson.gpsLat]/[gpsLng] (spec's own
 * example: "Location: F5M2+57Q, 1, Bayombong, Nueva Vizcaya" — a real
 * street/Plus-Code address, not the raw coordinate pair) — null while it's
 * still resolving, empty once resolved with nothing found (falls back to
 * the raw coordinates in that case; see [TerritoryMapScreen]). */
data class TerritoryMapRow(
    val person: InterestedPerson,
    val congregationName: String,
    val resolvedLocation: String?,
)

/**
 * "The Territory Module will be a map of location of every Search,
 * Interested, Return Visit, Bible Study [record]" — replaces the old
 * Territory Master File CRUD (create/edit/delete a Territory entity) with a
 * read-only directory of every InterestedPerson record (any of the three
 * pipeline stages) that has a saved GPS location, searchable by name or
 * location.
 */
@HiltViewModel
class TerritoryMapViewModel @Inject constructor(
    private val interestedPersonRepository: InterestedPersonRepository,
    private val congregationRepository: CongregationRepository,
    private val locationTracker: LocationTracker,
) : ViewModel() {

    private val _resolvedAddresses = MutableStateFlow<Map<String, String>>(emptyMap())

    // Guards against re-launching a reverse-geocode lookup for the same
    // person on every recomposition of the combine below (a plain "is it in
    // the resolved map yet" check alone would re-fire once per person for
    // every recombination while the very first lookup is still in flight,
    // since none of those in-flight lookups have written back yet) — a
    // synchronized Set since this is read/written from whichever dispatcher
    // the combine happens to run on plus every launched lookup coroutine.
    private val requestedIds = Collections.synchronizedSet(mutableSetOf<String>())

    /** [congregationId] null means every congregation (Super-Admin). Only
     * [RecordStatus.ACTIVE] records with a saved location are shown — same
     * "active records only" convention [FindLocationViewModel.recordsFor]
     * already uses for its own record picker. */
    fun rowsFor(congregationId: String?): Flow<List<TerritoryMapRow>> =
        combine(
            interestedPersonRepository.observeAll(),
            congregationRepository.observeAll(),
            _resolvedAddresses,
        ) { people, congregations, resolved ->
            val withLocation = people
                .filter { it.status == RecordStatus.ACTIVE && it.hasGpsLocation }
                .filter { congregationId == null || it.congregationId == congregationId }

            withLocation.forEach { person ->
                if (resolved[person.id] == null && requestedIds.add(person.id)) {
                    viewModelScope.launch {
                        val address = runCatching { locationTracker.reverseGeocode(person.gpsLat!!, person.gpsLng!!) }.getOrNull()
                        _resolvedAddresses.update { it + (person.id to address.orEmpty()) }
                    }
                }
            }

            withLocation
                .map { person ->
                    TerritoryMapRow(
                        person = person,
                        congregationName = congregations.firstOrNull { it.id == person.congregationId }?.name ?: "—",
                        resolvedLocation = resolved[person.id]?.ifBlank { null },
                    )
                }
                .sortedBy { it.person.name }
        }
}
