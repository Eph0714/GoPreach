package com.emfitsolutions.gopreach.ui.screens.interestedpeople

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.Visit
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.VisitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Spec §6.3 — Interested People Records, publisher-managed, each with multiple visits. */
@HiltViewModel
class InterestedPeopleViewModel @Inject constructor(
    private val interestedPersonRepository: InterestedPersonRepository,
    private val visitRepository: VisitRepository,
    private val auditLogRepository: AuditLogRepository,
) : ViewModel() {

    fun peopleFor(publisherPersonId: String): Flow<List<InterestedPerson>> =
        interestedPersonRepository.observeAll().map { list -> list.filter { it.publisherPersonId == publisherPersonId } }

    fun save(person: InterestedPerson) {
        viewModelScope.launch { interestedPersonRepository.save(person) }
    }

    /** "Move to Inactive" / reactivate — the record and all its visits are kept,
     * untouched; this only hides it from the normal active list. */
    fun setStatus(person: InterestedPerson, status: RecordStatus, actorPersonId: String) {
        viewModelScope.launch {
            val previous = person.status
            interestedPersonRepository.save(person.copy(status = status))
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "CHANGE_INTERESTED_PERSON_STATUS",
                targetType = "InterestedPerson",
                targetId = person.id,
                details = "status: $previous -> $status (${person.name})",
            )
        }
    }

    /** Only Super-Admin ever sees this (see BUILD_PLAN.md scoping). Cascades to
     * every child Visit document first — the old [delete] never did this,
     * silently orphaning them despite UI copy claiming otherwise; fixed here. */
    fun permanentlyDelete(person: InterestedPerson, actorPersonId: String) {
        viewModelScope.launch {
            visitRepository.observeForInterestedPerson(person.id).first()
                .forEach { visit -> visitRepository.delete(person.id, visit.id) }
            interestedPersonRepository.delete(person.id)
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "PERMANENT_DELETE_INTERESTED_PERSON",
                targetType = "InterestedPerson",
                targetId = person.id,
                details = person.name,
            )
        }
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
