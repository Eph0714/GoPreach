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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts every collection-wide `startRemoteSync()` Firestore listener, and
 * re-starts them on every sign-in/sign-out. Without this, the offline cache
 * only ever contains documents *this device* wrote itself — anything created
 * elsewhere (another admin's device, a directly-provisioned account, a
 * teammate's enrollment) never reaches this device's Room cache or its
 * permission checks, since [com.emfitsolutions.gopreach.domain.UserSession]
 * and every repository read from that cache, not Firestore directly.
 *
 * Why re-start on every auth change (not just once at boot, as a first pass
 * of this used to do): a Firestore snapshot listener that hits a permission
 * error (e.g. registered while signed out, against a collection the security
 * rules gate on `isSignedIn()`) *terminates* — [FirestoreMirror]'s error
 * branch swallows the error rather than crashing, but the underlying
 * registration is still dead and will never emit again, even after the user
 * later signs in. Collections readable while signed out (like `people`,
 * per `firestore.rules`) never hit that error so looked fine; anything else —
 * `roleAssignments` in particular — would register at process start (before
 * any sign-in), die on the first `PERMISSION_DENIED`, and silently never
 * sync again, which surfaced as a signed-in user's dashboard showing "Unknown
 * role" despite their RoleAssignment existing server-side. Re-subscribing
 * fresh on every auth-state change (via `flatMapLatest`, which cancels the
 * old listener and attaches a brand new one) means every collection gets a
 * listener registered *while already authenticated* right after login.
 * (Per-parent-document listeners, like
 * [com.emfitsolutions.gopreach.data.repository.VisitRepository], are started
 * on demand by their own screens instead, since there's no fixed set of them
 * to start up front.)
 */
@Singleton
class RemoteSyncCoordinator @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
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

    /** Emits the signed-in uid (or null) whenever Firebase Auth's state changes. */
    private fun authUidFlow(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser?.uid) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    fun startAll() {
        if (started) return
        started = true
        val uidChanged = authUidFlow()
        uidChanged.flatMapLatest { personRepository.startRemoteSync() }.launchIn(appScope)
        uidChanged.flatMapLatest { roleAssignmentRepository.startRemoteSync() }.launchIn(appScope)
        uidChanged.flatMapLatest { congregationRepository.startRemoteSync() }.launchIn(appScope)
        uidChanged.flatMapLatest { groupRepository.startRemoteSync() }.launchIn(appScope)
        uidChanged.flatMapLatest { elderTitleRepository.startRemoteSync() }.launchIn(appScope)
        uidChanged.flatMapLatest { territoryRepository.startRemoteSync() }.launchIn(appScope)
        uidChanged.flatMapLatest { scheduleRepository.startRemoteSync() }.launchIn(appScope)
        uidChanged.flatMapLatest { bibleStudyRepository.startRemoteSync() }.launchIn(appScope)
        uidChanged.flatMapLatest { interestedPersonRepository.startRemoteSync() }.launchIn(appScope)
        uidChanged.flatMapLatest { monthlyReportRepository.startRemoteSync() }.launchIn(appScope)
        uidChanged.flatMapLatest { auditLogRepository.startRemoteSync() }.launchIn(appScope)
        uidChanged.flatMapLatest { appSettingsRepository.startRemoteSync() }.launchIn(appScope)
        uidChanged.flatMapLatest { sharedLocationRepository.startRemoteSync() }.launchIn(appScope)
    }
}
