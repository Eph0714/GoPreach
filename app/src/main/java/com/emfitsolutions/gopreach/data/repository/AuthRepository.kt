package com.emfitsolutions.gopreach.data.repository

import android.content.Context
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PasswordResetRequest
import com.emfitsolutions.gopreach.data.model.RoleAssignment
import com.emfitsolutions.gopreach.data.model.RoleType
import com.emfitsolutions.gopreach.domain.CredentialGenerator
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data class Success(val person: Person, val requiresPasswordChange: Boolean) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

data class TempCredentials(
    val username: String,
    val temporaryPassword: String,
    val shareableLink: String,
)

/**
 * Firebase Auth wiring for spec §4.5 (first-login/temp-credential flow) and §5.1/5.2
 * (username+password login, forgot password). See [authEmailFor] for how a
 * username-based login maps onto Firebase's email-based accounts.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val personRepository: PersonRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val auditLogRepository: AuditLogRepository,
) {
    val currentPersonId: String?
        get() = personIdFromAuthEmail(firebaseAuth.currentUser?.email)

    suspend fun findPersonByUsername(username: String): Person? =
        firestore.collection("people")
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.toObject(Person::class.java)

    suspend fun signIn(username: String, password: String): AuthResult {
        val person = runCatching { findPersonByUsername(username) }.getOrNull()
            ?: return AuthResult.Error("Invalid username or password.")
        return try {
            firebaseAuth.signInWithEmailAndPassword(authEmailFor(person.id), password).await()
            personRepository.save(person) // seed local cache for offline use this session
            auditLogRepository.log(actorPersonId = person.id, action = "SIGN_IN")
            AuthResult.Success(person, requiresPasswordChange = person.isTemporaryCredential)
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Invalid username or password.")
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }

    /**
     * Creates a Person + RoleAssignment with single-use temp credentials (spec
     * §4.2-4.4). Uses a throwaway secondary [FirebaseApp] instance to create the new
     * Auth account so it never disturbs [enrollingPersonId]'s active session — the
     * client SDK otherwise signs you in as whichever user you just created.
     */
    suspend fun createAccountWithTempCredentials(
        person: Person,
        roleAssignment: (personId: String) -> RoleAssignment,
        enrollingPersonId: String,
    ): TempCredentials {
        val personId = CredentialGenerator.newPersonId()
        val username = uniqueUsername(CredentialGenerator.baseUsername(person.firstName, person.lastName))
        val tempPassword = CredentialGenerator.temporaryPassword()

        createSecondaryAuthAccount(personId, tempPassword)

        val finalPerson = person.copy(
            id = personId,
            username = username,
            isTemporaryCredential = true,
            createdAt = System.currentTimeMillis(),
            createdByPersonId = enrollingPersonId,
        )
        personRepository.save(finalPerson)
        val assignment = roleAssignment(personId)
        roleAssignmentRepository.save(assignment)
        val actionRole = when (val type = assignment.resolvedRoleType()) {
            is RoleType.Admin -> type.role.name
            is RoleType.Publisher -> "PUBLISHER"
        }
        auditLogRepository.log(
            actorPersonId = enrollingPersonId,
            action = "ENROLL_$actionRole",
            targetType = "Person",
            targetId = personId,
            congregationId = assignment.congregationId,
        )

        return TempCredentials(
            username = username,
            temporaryPassword = tempPassword,
            shareableLink = CredentialGenerator.shareableSetupLink(username, tempPassword),
        )
    }

    private suspend fun createSecondaryAuthAccount(personId: String, tempPassword: String) {
        val appName = "temp-account-$personId"
        val secondaryApp = FirebaseApp.initializeApp(appContext, FirebaseApp.getInstance().options, appName)
        try {
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
            secondaryAuth.createUserWithEmailAndPassword(authEmailFor(personId), tempPassword).await()
            secondaryAuth.signOut()
        } finally {
            secondaryApp.delete()
        }
    }

    private suspend fun uniqueUsername(base: String): String {
        if (findPersonByUsername(base) == null) return base
        var suffix = 1
        while (findPersonByUsername("$base$suffix") != null) suffix++
        return "$base$suffix"
    }

    /** Spec §4.5 step 2-3: mandatory username+password change on first login, then
     * re-login with the new credentials — this only updates Auth + the Person
     * record and signs the user out; the caller navigates back to Login. */
    suspend fun forcedPasswordChange(newUsername: String, newPassword: String): AuthResult {
        val user = firebaseAuth.currentUser
            ?: return AuthResult.Error("Session expired — please log in again.")
        val personId = personIdFromAuthEmail(user.email)
            ?: return AuthResult.Error("Session expired — please log in again.")
        val existing = findPersonByUsername(newUsername)
        if (existing != null && existing.id != personId) {
            return AuthResult.Error("That username is already taken.")
        }
        val person = personRepository.get(personId)
            ?: return AuthResult.Error("Account record not found.")
        return try {
            user.updatePassword(newPassword).await()
            val updated = person.copy(username = newUsername, isTemporaryCredential = false)
            personRepository.save(updated) // local cache + offline queue, for consistency with the rest of the app
            // The queued write above happens asynchronously in the background — if it
            // hasn't landed by the time we sign out below, the write loses its
            // authenticated session and gets permanently stuck retrying against
            // security rules that require auth (confirmed by hand: race reproduces
            // every time otherwise). So also write synchronously, here, before
            // signing out — Firestore's native .set(pojo) skips the @DocumentId
            // field automatically, unlike the offline queue's Gson-based path.
            firestore.collection("people").document(personId).set(updated).await()
            firebaseAuth.signOut()
            AuthResult.Success(updated, requiresPasswordChange = false)
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Couldn't update your credentials.")
        }
    }

    /** Spec §4.5 step 4: lost credentials are recovered from whoever enrolled the
     * person, not a self-service email link (email is optional on [Person]). */
    suspend fun requestPasswordReset(username: String) {
        val person = runCatching { findPersonByUsername(username) }.getOrNull()
        val request = PasswordResetRequest(
            requestedUsername = username,
            personId = person?.id,
            targetPersonId = person?.createdByPersonId,
            requestedAt = System.currentTimeMillis(),
        )
        val id = firestore.collection("passwordResetRequests").document().id
        firestore.collection("passwordResetRequests").document(id).set(request.copy(id = id)).await()
    }
}
