package com.emfitsolutions.gopreach.data.sync

import com.emfitsolutions.gopreach.data.repository.AppSettingsRepository
import com.emfitsolutions.gopreach.data.repository.AuditLogRepository
import com.emfitsolutions.gopreach.data.repository.BibleStudyRepository
import com.emfitsolutions.gopreach.data.repository.CongregationRepository
import com.emfitsolutions.gopreach.data.repository.ElderTitleRepository
import com.emfitsolutions.gopreach.data.repository.GroupRepository
import com.emfitsolutions.gopreach.data.repository.InterestedPersonRepository
import com.emfitsolutions.gopreach.data.repository.MonthlyReportRepository
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import com.emfitsolutions.gopreach.data.repository.ScheduleRepository
import com.emfitsolutions.gopreach.data.repository.SharedLocationRepository
import com.emfitsolutions.gopreach.data.repository.TerritoryRepository
import com.emfitsolutions.gopreach.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts every collection-wide `startRemoteSync()` Firestore listener exactly
 * once, for the lifetime of the process. Without this, the offline cache only
 * ever contains documents *this device* wrote itself — anything created
 * elsewhere (another admin's device, a directly-provisioned account, a
 * teammate's enrollment) never reaches this device's Room cache or its
 * permission checks, since [com.emfitsolutions.gopreach.domain.UserSession]
 * and every repository read from that cache, not Firestore directly.
 *
 * Registering these listeners while signed out is fine: Firestore's SDK
 * quietly denies/permission-errors them until the user authenticates, then
 * picks the *same* registered listener back up automatically — no restart
 * needed on sign-in. (Per-parent-document listeners, like
 * [com.emfitsolutions.gopreach.data.repository.VisitRepository], are started
 * on demand by their own screens instead, since there's no fixed set of them
 * to start up front.)
 */
@Singleton
class RemoteSyncCoordinator @Inject constructor(
    private val personRepository: PersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val congregationRepository: CongregationRepository,
    private val groupRepository: GroupRepository,
    private val elderTitleRepository: ElderTitleRepository,
    private val territoryRepository: TerritoryRepository,
    private val scheduleRepository: ScheduleRepository,
    private val bibleStudyRepository: BibleStudyRepository,
    private val interestedPersonRepository: InterestedPersonRepository,
    private val monthlyReportRepository: MonthlyReportRepository,
    private val auditLogRepository: AuditLogRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val sharedLocationRepository: SharedLocationRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private var started = false

    fun startAll() {
        if (started) return
        started = true
        personRepository.startRemoteSync().launchIn(appScope)
        roleAssignmentRepository.startRemoteSync().launchIn(appScope)
        congregationRepository.startRemoteSync().launchIn(appScope)
        groupRepository.startRemoteSync().launchIn(appScope)
        elderTitleRepository.startRemoteSync().launchIn(appScope)
        territoryRepository.startRemoteSync().launchIn(appScope)
        scheduleRepository.startRemoteSync().launchIn(appScope)
        bibleStudyRepository.startRemoteSync().launchIn(appScope)
        interestedPersonRepository.startRemoteSync().launchIn(appScope)
        monthlyReportRepository.startRemoteSync().launchIn(appScope)
        auditLogRepository.startRemoteSync().launchIn(appScope)
        appSettingsRepository.startRemoteSync().launchIn(appScope)
        sharedLocationRepository.startRemoteSync().launchIn(appScope)
    }
}
