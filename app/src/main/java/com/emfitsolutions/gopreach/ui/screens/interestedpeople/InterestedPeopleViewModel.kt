package com.emfitsolutions.gopreach.ui.screens.interestedpeople

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.Visit
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Spec §6.3 — Interested People Records, publisher-managed, each with multiple visits. */
@HiltViewModel
class InterestedPeopleViewModel @Inject constructor(
    private val interestedPersonRepository: InterestedPersonRepository,
    private val visitRepository: VisitRepository,
) : ViewModel() {

    fun peopleFor(publisherPersonId: String): Flow<List<InterestedPerson>> =
        interestedPersonRepository.observeAll().map { list -> list.filter { it.publisherPersonId == publisherPersonId } }

    fun save(person: InterestedPerson) {
        viewModelScope.launch { interestedPersonRepository.save(person) }
    }

    fun delete(personId: String) {
        viewModelScope.launch { interestedPersonRepository.delete(personId) }
    }

    fun visitsFor(interestedPersonId: String): Flow<List<Visit>> =
        visitRepository.observeForInterestedPerson(interestedPersonId)

    fun startVisitSync(interestedPersonId: String): Flow<Unit> = visitRepository.startRemoteSync(interestedPersonId)

    fun saveVisit(visit: Visit) {
        viewModelScope.launch { visitRepository.save(visit) }
    }

    fun deleteVisit(interestedPersonId: String, visitId: String) {
        viewModelScope.launch { visitRepository.delete(interestedPersonId, visitId) }
    }
}
