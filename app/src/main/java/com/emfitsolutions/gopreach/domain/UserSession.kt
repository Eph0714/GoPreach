package com.emfitsolutions.gopreach.domain

import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.UserAccessGrant
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Which side of the app a signed-in [Person] should land on — either can carry the
 * other module's nav destinations reachable from a "switch context" affordance,
 * since one Person may hold both an Admin-track role and a Publisher category
 * (spec §3, §5). */
enum class SessionContext { ADMIN, PUBLISHER, BOTH, NONE }

data class SessionState(
    val isLoading: Boolean = true,
    val person: Person? = null,
    val roleAssignments: List<RoleAssignment> = emptyList(),
    /** Non-null only for a restricted (Circuit Overseer / custom) user — see
     * [UserAccessGrant] and [PermissionChecker.hasPermission]. */
    val grant: UserAccessGrant? = null,
) {
    val isSignedIn: Boolean get() = person != null

    /** Spec §9: an account can be deactivated/suspended without deleting it or
     * touching its RoleAssignments — this is what actually revokes the ability
     * to keep using an already-open session once that happens (the sign-in gate
     * in [com.emfitsolutions.gopreach.data.repository.AuthRepository.signIn]
     * only stops a *new* sign-in attempt). [GoPreachNavGraph] signs the session
     * out the moment this goes true. */
    val isAccountBlocked: Boolean get() = person != null && !PermissionChecker.isAccountUsable(person)

    /** Source of truth for whether the forced-password-change flow (spec §4.5)
     * still applies to this signed-in session — survives app restarts, unlike the
     * one-shot flag [com.emfitsolutions.gopreach.ui.screens.login.LoginUiState] uses
     * right after a fresh sign-in. */
    val requiresPasswordChange: Boolean get() = person?.isTemporaryCredential == true

    val availableContext: SessionContext
        get() {
            val hasAdmin = PermissionChecker.highestAdminRole(roleAssignments) != null
            val hasPublisher = PermissionChecker.isActivePublisher(roleAssignments)
            return when {
                hasAdmin && hasPublisher -> SessionContext.BOTH
                hasAdmin -> SessionContext.ADMIN
                hasPublisher -> SessionContext.PUBLISHER
                else -> SessionContext.NONE
            }
        }
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
    @ApplicationScope appScope: CoroutineScope,
) {
    private fun authStateFlow(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(personIdFromAuthEmail(auth.currentUser?.email))
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    val state: StateFlow<SessionState> = authStateFlow()
        .flatMapLatest { personId ->
            if (personId == null) {
                flowOf(SessionState(isLoading = false, person = null, roleAssignments = emptyList()))
            } else {
                combine(
                    personRepository.observeAll().map { people -> people.firstOrNull { it.id == personId } },
                    roleAssignmentRepository.observeForPerson(personId),
                    userAccessGrantRepository.observeForPerson(personId),
                ) { person, roles, grant -> SessionState(isLoading = false, person = person, roleAssignments = roles, grant = grant) }
            }
        }
        .stateIn(appScope, SharingStarted.Eagerly, SessionState(isLoading = true))
}
