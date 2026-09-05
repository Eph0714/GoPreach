package com.emfitsolutions.gopreach.domain

import android.util.Log
import com.emfitsolutions.gopreach.data.model.AdminRole
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleAssignmentStatus
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.data.model.UserAccessGrant
import com.emfitsolutions.gopreach.data.model.displayLabel
import com.emfitsolutions.gopreach.data.repository.AppLanguageRepository
import com.emfitsolutions.gopreach.data.repository.OfflineSessionMarker
import com.emfitsolutions.gopreach.data.repository.PersonRepository
import com.emfitsolutions.gopreach.data.repository.RoleAssignmentRepository
import com.emfitsolutions.gopreach.data.repository.UserAccessGrantRepository
import com.emfitsolutions.gopreach.data.repository.personIdFromAuthEmail
import com.emfitsolutions.gopreach.di.ApplicationScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** "Multiple Role Login Detection & Role Selection" spec §2 — one selectable
 * "account" a signed-in [Person] may operate the app as, backed by exactly
 * one of their own ACTIVE [RoleAssignment] rows. See [SessionState.roleOptions]
 * for how these are built. */
data class RoleOption(
    val assignment: RoleAssignment,
    val label: String,
)

data class SessionState(
    val isLoading: Boolean = true,
    val person: Person? = null,
    val roleAssignments: List<RoleAssignment> = emptyList(),
    /** Non-null only for a restricted (Circuit Overseer / custom) user — see
     * [UserAccessGrant] and [PermissionChecker.hasPermission]. */
    val grant: UserAccessGrant? = null,
    /** Spec §7 — set by [UserSession.selectRole] once the user picks an
     * account off the role-selection screen; cleared automatically on sign
     * out (see [UserSession]'s own flow chain). Only ever matters when
     * [roleOptions] has more than one entry — a single-role account never
     * needs this to resolve [activeRoleAssignment]. */
    val selectedRoleAssignmentId: String? = null,
) {
    val isSignedIn: Boolean get() = person != null

    /** Spec §9: an account can be deactivated/suspended without deleting it or
     * touching its RoleAssignments — this is what actually revokes the ability
     * to keep using an already-open session once that happens (the sign-in gate
     * in [com.emfitsolutions.gopreach.data.repository.AuthRepository.signIn]
     * only stops a *new* sign-in attempt). [GoPreachNavGraph] signs the session
     * out the moment this goes true. */
    val isAccountBlocked: Boolean get() = person != null && !PermissionChecker.isAccountUsable(person, roleAssignments)

    /** Source of truth for whether the forced-password-change flow (spec §4.5)
     * still applies to this signed-in session — survives app restarts, unlike the
     * one-shot flag [com.emfitsolutions.gopreach.ui.screens.login.LoginUiState] uses
     * right after a fresh sign-in. */
    val requiresPasswordChange: Boolean get() = person?.isTemporaryCredential == true

    /** "Automatically detect all roles associated with the authenticated
     * account... Check for: Admin, Coordinator Elder, Ministerial Servant,
     * Regular Elder, Publisher" — one option per distinct [AdminRole] value
     * this Person actively holds, plus one for an active Publisher category
     * if any. Never hard-coded: every entry here is backed by a real,
     * currently-ACTIVE [RoleAssignment] row (spec §5/§12). A Person holding
     * two RoleAssignments of the very same AdminRole (e.g. Regular Elder in
     * two different Groups) collapses to the first one — the same "pick one"
     * convention this app already uses everywhere else it resolves "a
     * person's own X" (see GoPreachNavGraph's ownGroupAssignment /
     * ownPublisherAssignment, which used to do this same firstOrNull scan
     * directly). */
    val roleOptions: List<RoleOption>
        get() {
            val active = roleAssignments.filter { it.status == RoleAssignmentStatus.ACTIVE }
            val adminOptions = AdminRole.entries.mapNotNull { role ->
                active.firstOrNull { (it.resolvedRoleTypeOrNull() as? RoleType.Admin)?.role == role }
                    ?.let { RoleOption(it, role.displayLabel()) }
            }
            val publisherOption = active.firstOrNull { it.resolvedRoleTypeOrNull() is RoleType.Publisher }
                ?.let { RoleOption(it, "Publisher") }
            return adminOptions + listOfNotNull(publisherOption)
        }

    /** Spec §3/§4 — the role-selection screen is skipped entirely for a
     * single-role account, and never shown before [roleOptions] has actually
     * loaded (so it can't flash on an empty list the instant sign-in
     * succeeds, before Firestore's first RoleAssignment snapshot arrives). */
    val needsRoleSelection: Boolean
        get() = !isLoading && person != null && roleOptions.size > 1 && activeRoleAssignment == null

    /** Spec §7 — "the selected role must control the session... do not
     * automatically combine permissions from all roles." The single
     * RoleAssignment every permission/navigation/scope decision in the app
     * is made from: automatic (the only one) for a single-role account,
     * otherwise whichever [roleOptions] entry [selectRole] chose. Resolves
     * to null while a multi-role account hasn't picked yet — see
     * [needsRoleSelection]. */
    val activeRoleAssignment: RoleAssignment?
        get() = if (roleOptions.size <= 1) {
            roleOptions.firstOrNull()?.assignment
        } else {
            // Only ever matches one of this account's OWN roleOptions — a
            // stale/foreign id (the role was revoked mid-session, say) simply
            // fails to resolve, re-triggering [needsRoleSelection] rather than
            // ever granting access under a role that isn't really active.
            roleOptions.firstOrNull { it.assignment.id == selectedRoleAssignmentId }?.assignment
        }

    val activeAdminRole: AdminRole?
        get() = (activeRoleAssignment?.resolvedRoleTypeOrNull() as? RoleType.Admin)?.role

    val isActivePublisherRole: Boolean
        get() = activeRoleAssignment?.resolvedRoleTypeOrNull() is RoleType.Publisher
}

/**
 * App-wide observable session, rebuilt from Firebase Auth's persisted sign-in state
 * (survives process death — no separate "remember me" needed) plus the signed-in
 * Person's [RoleAssignment]s, which drive every permission check via
 * [PermissionChecker].
 */
@Singleton
class UserSession @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val personRepository: PersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val userAccessGrantRepository: UserAccessGrantRepository,
    private val offlineSessionMarker: OfflineSessionMarker,
    private val appLanguageRepository: AppLanguageRepository,
    @ApplicationScope appScope: CoroutineScope,
) {
    private fun authStateFlow(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(personIdFromAuthEmail(auth.currentUser?.email))
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    /** Firebase's own persisted sign-in wins when present (the normal case —
     * survives app restart with no network needed on its own); [OfflineSessionMarker]
     * only matters for [com.emfitsolutions.gopreach.data.repository.AuthRepository
     * .offlineSignIn], which Firebase's SDK has no way to reflect into
     * `currentUser` itself since it never makes a network call. */
    private fun signedInPersonIdFlow(): Flow<String?> =
        combine(authStateFlow(), offlineSessionMarker.personId) { firebaseId, offlineId -> firebaseId ?: offlineId }

    /** Spec §7 — which of the signed-in Person's own [RoleOption]s is active
     * for this session. Reset to null every time [signedInPersonIdFlow] moves
     * to a different (or no) person, so a fresh sign-in — including signing
     * straight back in as someone else without the process restarting —
     * always starts from "not yet chosen" rather than inheriting whatever the
     * previous session picked. Deliberately in-memory only: spec §11's "use
     * another role -> log out, log in again, select another role" already
     * implies this doesn't need to survive a process death either — a cold
     * start naturally re-asks, exactly like a fresh login would. */
    private val _selectedRoleAssignmentId = MutableStateFlow<String?>(null)

    fun selectRole(assignmentId: String) {
        _selectedRoleAssignmentId.value = assignmentId
    }

    /** Group Chat Setting's security-rules trick (see [Person
     * .activeCongregationId]'s doc comment) — keeps that denormalized copy
     * current every time this session's own active role changes. Admin/
     * Coordinator Elder/Service Overseer/Ministerial Servant get their
     * congregation; a Regular Elder or Publisher's own group/congregation
     * isn't a "manage every group chat in this congregation" scope the same
     * way, so those resolve to null here (unaffected: they can still be
     * added as a participant and reach a chat through [GroupChat
     * .participantIds] alone, which needs no congregation match). Fires
     * once per actual change, not on every recomposition-driven re-read of
     * [state] — [distinctUntilChanged] on the resolved pair below, not on
     * [SessionState] itself (which differs on every roleAssignments/grant
     * emission even when the *active* role hasn't moved).
     */
    private fun syncActiveRoleContext(appScope: CoroutineScope, personRepository: PersonRepository) {
        appScope.launch {
            state
                .map { s ->
                    val person = s.person ?: return@map null
                    val role = (s.activeRoleAssignment?.resolvedRoleTypeOrNull() as? RoleType.Admin)?.role
                    val congregationId = s.activeRoleAssignment?.congregationId.takeIf {
                        role in setOf(AdminRole.ADMIN_PER_CONGREGATION, AdminRole.COORDINATOR_ELDER, AdminRole.SERVICE_OVERSEER, AdminRole.MINISTERIAL_SERVANT)
                    }
                    Triple(person, congregationId, role?.name)
                }
                .distinctUntilChanged { old, new -> old?.second == new?.second && old?.third == new?.third && old?.first?.id == new?.first?.id }
                .collect { triple ->
                    val (person, congregationId, roleName) = triple ?: return@collect
                    if (person.activeCongregationId != congregationId || person.activeAdminRole != roleName) {
                        runCatching {
                            personRepository.saveNow(person.copy(activeCongregationId = congregationId, activeAdminRole = roleName))
                        }.onFailure { Log.w("UserSession", "Failed to sync active role context: ${it.message}") }
                    }
                }
        }
    }

    val state: StateFlow<SessionState> = signedInPersonIdFlow()
        .let { personIdFlow ->
            var lastPersonId: String? = null
            personIdFlow.flatMapLatest { personId ->
                if (personId != lastPersonId) {
                    lastPersonId = personId
                    _selectedRoleAssignmentId.value = null
                }
                if (personId == null) {
                    flowOf(SessionState(isLoading = false, person = null, roleAssignments = emptyList()))
                } else {
                    combine(
                        personRepository.observeAll().map { people -> people.firstOrNull { it.id == personId } },
                        roleAssignmentRepository.observeForPerson(personId),
                        userAccessGrantRepository.observeForPerson(personId),
                        _selectedRoleAssignmentId,
                    ) { person, roles, grant, selectedRoleAssignmentId ->
                        SessionState(
                            isLoading = false,
                            person = person,
                            roleAssignments = roles,
                            grant = grant,
                            selectedRoleAssignmentId = selectedRoleAssignmentId,
                        )
                    }
                }
            }
        }
        .stateIn(appScope, SharingStarted.Eagerly, SessionState(isLoading = true))

    /** "The same user logs in from Android [and] Desktop... should detect
     * the [saved] language preference" — the cross-device half of this
     * feature ([AppLanguageRepository] itself only ever applies a language
     * already chosen, it doesn't know about Firestore). Fires once per
     * signed-in Person (keyed on their id, not on every unrelated
     * [SessionState] emission), and only actually calls
     * [AppLanguageRepository.applyLanguage] when their stored
     * [Person.language] doesn't already match what's applied on this
     * device — so signing in fresh on a new device picks up whatever
     * language they last chose elsewhere, but this never fights a change
     * the user is mid-making on *this* device (see [SettingsViewModel
     * .setLanguage], which applies locally first and saves to Firestore
     * second — by the time that save round-trips back through this same
     * flow, [AppLanguage.fromCode] already agrees, so this is a no-op). */
    private fun syncLanguagePreference(appScope: CoroutineScope) {
        appScope.launch {
            state
                .map { it.person }
                .distinctUntilChanged { old, new -> old?.id == new?.id && old?.language == new?.language }
                .collect { person ->
                    val desired = AppLanguage.fromCode(person?.language)
                    if (appLanguageRepository.current.value != desired) {
                        appLanguageRepository.applyLanguage(desired)
                    }
                }
        }
    }

    init {
        syncActiveRoleContext(appScope, personRepository)
        syncLanguagePreference(appScope)
    }
}
