package com.emfitsolutions.gopreach.ui.screens.territories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.Group
import com.emfitsolutions.gopreach.data.model.Territory
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.TerritoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TerritoryRow(val territory: Territory, val groupName: String?)

/** Spec §3/§5.1 — Territory Master File. */
@HiltViewModel
class ManageTerritoriesViewModel @Inject constructor(
    private val territoryRepository: TerritoryRepository,
    groupRepository: GroupRepository,
    congregationRepository: CongregationRepository,
) : ViewModel() {

    val groups: Flow<List<Group>> = groupRepository.observeAll()

    /** Only needed by a Super-Admin — lets them pick which congregation a new
     * territory belongs to (Admin/Coordinator Elder already have exactly one). */
    val congregations: Flow<List<Congregation>> = congregationRepository.observeAll()

    fun rowsFor(congregationId: String?): Flow<List<TerritoryRow>> =
        combine(territoryRepository.observeAll(), groups) { territories, groups ->
            territories
                .filter { congregationId == null || it.congregationId == congregationId }
                .map { territory ->
                    val groupName = groups.firstOrNull { it.id == territory.assignedGroupId }?.name
                    TerritoryRow(territory, groupName)
                }
                .sortedBy { it.territory.name }
        }

    fun save(territory: Territory) {
        viewModelScope.launch { territoryRepository.save(territory) }
    }

    fun delete(territoryId: String) {
        viewModelScope.launch { territoryRepository.delete(territoryId) }
    }
}
