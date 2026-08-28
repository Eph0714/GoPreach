package com.emfitsolutions.gopreach.ui.screens.contactrecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.data.model.PublisherCategory
import com.emfitsolutions.gopreach.data.model.RecordStatus
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One entry in the consolidated directory — a Person-backed role (Publisher/
 * Coordinator Elder/Service Overseer/Ministerial Servant) or an Interested
 * Person at whichever pipeline stage they're currently at. [sourceLabels] can
 * hold more than one entry for the same real person (e.g. a Coordinator Elder
 * who is also a Regular Pioneer) — same "merge, don't duplicate the row"
 * reasoning [com.emfitsolutions.gopreach.ui.screens.dashboard
 * .computeStatMembers] already uses for the dashboard's own drill-downs. An
 * Interested Person always gets its own row: they have no Person doc to merge
 * against, and moving through Searching → Return Visit → Bible Study is a
 * single record changing stage, not three. */
data class ContactRow(
    val name: String,
    val sourceLabels: Set<String>,
    /** Phone number for a Person-backed row; blank for an Interested Person
     * (this app's [com.emfitsolutions.gopreach.data.model.InterestedPerson]
     * has no phone field — see its own doc comment — so [address] is the
     * only contact detail available for that source). */
    val contact: String,
    val address: String,
    val congregationId: String,
    val congregationName: String,
)

/** Every source label a [ContactRow] can carry — shared with
 * [ContactRecordScreen]'s "By Status" filter so that dropdown's options can
 * never drift out of sync with what this ViewModel actually produces. */
val CONTACT_SOURCE_LABELS = listOf(PUBLISHER, SEARCHING, RETURN_VISIT, BIBLE_STUDY, COORDINATOR_ELDER, SERVICE_OVERSEER, MINISTERIAL_SERVANT)

private const val PUBLISHER = "Publisher"
private const val SEARCHING = "Searching"
private const val RETURN_VISIT = "Return Visit"
private const val BIBLE_STUDY = "Bible Study"
private const val COORDINATOR_ELDER = "Coordinator Elder"
private const val SERVICE_OVERSEER = "Service Overseer"
private const val MINISTERIAL_SERVANT = "Ministerial Servant"

/** "Contact Record" module — one consolidated directory of every Publisher,
 * Interested Person (Searching/Return Visit/Bible Study), Coordinator Elder,
 * Service Overseer, and Ministerial Servant's contact details, in one place
 * instead of hunting through five separate Manage screens. Visible to
 * Coordinator Elder, Super-Admin, and Regular Elder (wired from
 * GoPreachNavGraph/AdminHomeScreen's canViewContactRecord) — congregation-
 * scoped for the first and third, all congregations for Super-Admin, the same
 * `visibleCongregationId == null` convention every other Manage screen here
 * already uses.
 */
@HiltViewModel
class ContactRecordViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val interestedPersonRepository: InterestedPersonRepository,
    private val congregationRepository: CongregationRepository,
) : ViewModel() {

    /** For the "By Congregation" filter — Super-Admin only in practice
     * (everyone else's rows are already fixed to their own single
     * congregation upstream, same `visibleCongregationId` scoping every
     * other Manage screen here uses). */
    val congregations: StateFlow<List<Congregation>> =
        congregationRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun rowsFor(visibleCongregationId: String?): Flow<List<ContactRow>> = combine(
        personRepository.observeAll(),
        roleAssignmentRepository.observeAll(),
        interestedPersonRepository.observeAll(),
        congregationRepository.observeAll(),
    ) { people, assignments, interestedPeople, congregations ->
        val peopleById = people.associateBy { it.id }
        val congregationNameById = congregations.associateBy({ it.id }, { it.name })
        fun congregationNameFor(congregationId: String?) = congregationNameById[congregationId] ?: "Unassigned"

        data class PersonKey(val personId: String, val congregationId: String)
        val labelsByPerson = mutableMapOf<PersonKey, MutableSet<String>>()

        assignments
            .filter { it.status == RoleAssignmentStatus.ACTIVE && it.personId in peopleById }
            .forEach { assignment ->
                val congregationId = assignment.congregationId ?: return@forEach
                val label = when (val role = assignment.resolvedRoleTypeOrNull()) {
                    is RoleType.Admin -> when (role.role) {
                        AdminRole.COORDINATOR_ELDER -> COORDINATOR_ELDER
                        AdminRole.SERVICE_OVERSEER -> SERVICE_OVERSEER
                        AdminRole.MINISTERIAL_SERVANT -> MINISTERIAL_SERVANT
                        else -> null
                    }
                    is RoleType.Publisher -> if (role.category != PublisherCategory.REMOVED_PUBLISHER) PUBLISHER else null
                    null -> null
                } ?: return@forEach
                val key = PersonKey(assignment.personId, congregationId)
                labelsByPerson.getOrPut(key) { mutableSetOf() }.add(label)
            }

        val personRows = labelsByPerson.mapNotNull { (key, labels) ->
            if (visibleCongregationId != null && key.congregationId != visibleCongregationId) return@mapNotNull null
            val person = peopleById[key.personId] ?: return@mapNotNull null
            ContactRow(
                name = person.fullName,
                sourceLabels = labels,
                contact = person.contact,
                address = person.address,
                congregationId = key.congregationId,
                congregationName = congregationNameFor(key.congregationId),
            )
        }

        val interestedPersonRows = interestedPeople
            .filter { it.status == RecordStatus.ACTIVE }
            .filter { visibleCongregationId == null || it.congregationId == visibleCongregationId }
            .map { interestedPerson ->
                val label = when (interestedPerson.pipelineStage) {
                    PipelineStage.SEARCHING -> SEARCHING
                    PipelineStage.RETURN_VISIT -> RETURN_VISIT
                    PipelineStage.BIBLE_STUDY -> BIBLE_STUDY
                }
                ContactRow(
                    name = interestedPerson.name,
                    sourceLabels = setOf(label),
                    contact = "",
                    address = interestedPerson.address,
                    congregationId = interestedPerson.congregationId,
                    congregationName = congregationNameFor(interestedPerson.congregationId),
                )
            }

        (personRows + interestedPersonRows).sortedBy { it.name }
    }
}
