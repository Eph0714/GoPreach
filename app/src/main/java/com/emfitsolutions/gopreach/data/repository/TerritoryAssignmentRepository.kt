package com.emfitsolutions.gopreach.data.repository

import com.emfitsolutions.gopreach.data.model.TerritoryAssignment
import com.emfitsolutions.gopreach.data.model.TerritoryAssignmentBarangay
import com.emfitsolutions.gopreach.data.sync.ConnectivityObserver
import com.emfitsolutions.gopreach.data.sync.OfflineFirestoreRepository
import com.emfitsolutions.gopreach.data.sync.mirrorFirestoreCollection
import com.emfitsolutions.gopreach.di.ApplicationScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val ASSIGNMENTS_COLLECTION = "territoryAssignments"
private const val BARANGAYS_COLLECTION = "territoryAssignmentBarangays"

/** Firestore caps a single transaction at ~500 document writes — this stays
 * well under that even after accounting for the header doc, so a save never
 * hits a cryptic Firestore-side limit error; the caller gets this app's own
 * clear message instead (see [TerritoryAssignmentResult.Error] callers). */
const val MAX_BARANGAYS_PER_SAVE = 400

sealed class TerritoryAssignmentResult {
    data class Success(val assignmentId: String) : TerritoryAssignmentResult()

    /** A barangay in this save attempt is already claimed by a different
     * assignment — the transaction's own conflict check (not just the
     * wizard's client-side "already taken" hint) caught it. */
    data class Conflict(val barangayName: String, val takenByGroupName: String) : TerritoryAssignmentResult()

    data class Offline(
        val message: String = "An internet connection is required to save a territory assignment. Connect and try again.",
    ) : TerritoryAssignmentResult()

    data class Error(val message: String) : TerritoryAssignmentResult()
}

/** Thrown only inside a [FirebaseFirestore.runTransaction] lambda below, so
 * the transaction aborts (none of its writes apply) and the surrounding
 * try/catch can turn it into a typed [TerritoryAssignmentResult.Conflict] —
 * this exception never escapes [TerritoryAssignmentRepository] itself. */
private class TerritoryConflictException(val barangayName: String, val takenByGroupName: String) : Exception()

/**
 * Territory Assignment module — assigns every barangay of one municipality to
 * exactly one FS Group per congregation. Unlike every other repository in
 * this app, the write methods here go **straight to Firestore inside a real
 * transaction**, not through [OfflineFirestoreRepository]'s normal queue-and-
 * flush-later outbox: the thing that must be atomic isn't "did my write
 * land" (which the outbox already guarantees, eventually) but "did nobody
 * else's write land on the same barangay between my read and my write" —
 * exactly the correctness guarantee a transaction provides and a queued
 * write cannot. This mirrors the precedent [AuthRepository
 * .createAccountWithTempCredentials]/[AuthRepository.forcedPasswordChange]
 * already set for "this operation must be confirmed by the server right
 * now, not whenever the sync queue next flushes."
 *
 * Each barangay claim's document id is deterministic —
 * `"${congregationId}_${barangayId}"` (see [TerritoryAssignmentBarangay]'s
 * own doc comment) — so "is this barangay already assigned in this
 * congregation" is answered by a single, cheap `transaction.get()` on a known
 * path, and "claim it" is a `transaction.set()` on that same path that the
 * transaction guarantees cannot race a concurrent claim of the same barangay.
 */
@Singleton
class TerritoryAssignmentRepository @Inject constructor(
    private val offline: OfflineFirestoreRepository,
    private val firestore: FirebaseFirestore,
    private val connectivityObserver: ConnectivityObserver,
    private val auditLogRepository: AuditLogRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    fun observeAssignments(): Flow<List<TerritoryAssignment>> = offline.observeCollection(ASSIGNMENTS_COLLECTION)
    fun observeBarangayClaims(): Flow<List<TerritoryAssignmentBarangay>> = offline.observeCollection(BARANGAYS_COLLECTION)

    fun startRemoteSync(): Flow<Unit> = merge(
        mirrorFirestoreCollection(firestore, offline, appScope, ASSIGNMENTS_COLLECTION, TerritoryAssignment::class.java) { it.id },
        mirrorFirestoreCollection(firestore, offline, appScope, BARANGAYS_COLLECTION, TerritoryAssignmentBarangay::class.java) { it.id },
    )

    private fun claimId(congregationId: String, barangayId: Int) = "${congregationId}_$barangayId"

    suspend fun createAssignment(
        congregationId: String,
        groupId: String,
        groupName: String,
        provinceId: Int,
        provinceName: String,
        muncityId: Int,
        muncityName: String,
        barangays: List<PsgcOption>,
        actorPersonId: String,
    ): TerritoryAssignmentResult {
        if (!connectivityObserver.isOnline()) return TerritoryAssignmentResult.Offline()
        if (barangays.isEmpty()) return TerritoryAssignmentResult.Error("Select at least one barangay.")
        if (barangays.size > MAX_BARANGAYS_PER_SAVE) {
            return TerritoryAssignmentResult.Error(
                "You can assign at most $MAX_BARANGAYS_PER_SAVE barangays in one save. Split this into two assignments.",
            )
        }
        val assignmentRef = firestore.collection(ASSIGNMENTS_COLLECTION).document()
        val now = System.currentTimeMillis()
        val assignment = TerritoryAssignment(
            id = assignmentRef.id,
            congregationId = congregationId,
            groupId = groupId,
            provinceId = provinceId,
            provinceName = provinceName,
            muncityId = muncityId,
            muncityName = muncityName,
            createdAt = now,
            createdByPersonId = actorPersonId,
            updatedAt = now,
            updatedByPersonId = actorPersonId,
        )
        val claims = barangays.map { b ->
            TerritoryAssignmentBarangay(
                id = claimId(congregationId, b.id),
                congregationId = congregationId,
                assignmentId = assignmentRef.id,
                groupId = groupId,
                groupName = groupName,
                provinceId = provinceId,
                muncityId = muncityId,
                muncityName = muncityName,
                barangayId = b.id,
                barangayName = b.name,
                createdAt = now,
                createdByPersonId = actorPersonId,
            )
        }
        return try {
            firestore.runTransaction { txn ->
                // All reads before any write — a Firestore transaction
                // requirement, and also exactly what makes this check atomic
                // with the claim below: nothing can slip in between this get
                // and this transaction's eventual commit.
                for (claim in claims) {
                    val ref = firestore.collection(BARANGAYS_COLLECTION).document(claim.id)
                    val snap = txn.get(ref)
                    if (snap.exists()) {
                        throw TerritoryConflictException(claim.barangayName, snap.getString("groupName") ?: "another group")
                    }
                }
                txn.set(assignmentRef, assignment)
                for (claim in claims) {
                    txn.set(firestore.collection(BARANGAYS_COLLECTION).document(claim.id), claim)
                }
            }.await()
            // Write straight into the local cache now that the server has
            // confirmed it — same reasoning as OfflineFirestoreRepository
            // .saveNow's own doc comment: this screen needs the result
            // immediately, not whenever the next mirror snapshot happens to
            // arrive.
            offline.cacheFromServer(ASSIGNMENTS_COLLECTION, assignment.id, assignment)
            claims.forEach { offline.cacheFromServer(BARANGAYS_COLLECTION, it.id, it) }
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "ADD_TERRITORY_ASSIGNMENT",
                targetType = "TerritoryAssignment",
                targetId = assignment.id,
                congregationId = congregationId,
                details = "group=$groupName municipality=$muncityName barangays=${barangays.size}",
            )
            TerritoryAssignmentResult.Success(assignment.id)
        } catch (e: TerritoryConflictException) {
            TerritoryAssignmentResult.Conflict(e.barangayName, e.takenByGroupName)
        } catch (e: Exception) {
            TerritoryAssignmentResult.Error(e.localizedMessage ?: "Couldn't save this territory assignment.")
        }
    }

    suspend fun updateAssignment(
        assignmentId: String,
        congregationId: String,
        groupId: String,
        groupName: String,
        provinceId: Int,
        provinceName: String,
        muncityId: Int,
        muncityName: String,
        newBarangays: List<PsgcOption>,
        actorPersonId: String,
    ): TerritoryAssignmentResult {
        if (!connectivityObserver.isOnline()) return TerritoryAssignmentResult.Offline()
        if (newBarangays.isEmpty()) return TerritoryAssignmentResult.Error("Select at least one barangay.")
        if (newBarangays.size > MAX_BARANGAYS_PER_SAVE) {
            return TerritoryAssignmentResult.Error(
                "You can assign at most $MAX_BARANGAYS_PER_SAVE barangays in one save. Split this into two assignments.",
            )
        }
        // Computed before the transaction opens — the transaction only
        // needs to re-verify the barangays actually being newly claimed
        // ([toAdd]); anything already held by this same assignment
        // ([unchanged]) needs no existence re-check, and anything dropped
        // ([toRemove]) is simply released.
        val existingClaims = observeBarangayClaims().first().filter { it.assignmentId == assignmentId }
        val oldIds = existingClaims.map { it.barangayId }.toSet()
        val newIds = newBarangays.map { it.id }.toSet()
        val toAdd = newBarangays.filter { it.id !in oldIds }
        val toRemoveIds = oldIds - newIds
        val unchanged = newBarangays.filter { it.id in oldIds }
        val now = System.currentTimeMillis()
        val assignmentRef = firestore.collection(ASSIGNMENTS_COLLECTION).document(assignmentId)

        return try {
            firestore.runTransaction { txn ->
                for (b in toAdd) {
                    val ref = firestore.collection(BARANGAYS_COLLECTION).document(claimId(congregationId, b.id))
                    val snap = txn.get(ref)
                    if (snap.exists()) {
                        throw TerritoryConflictException(b.name, snap.getString("groupName") ?: "another group")
                    }
                }
                txn.update(
                    assignmentRef,
                    mapOf(
                        "groupId" to groupId,
                        "provinceId" to provinceId,
                        "provinceName" to provinceName,
                        "muncityId" to muncityId,
                        "muncityName" to muncityName,
                        "updatedAt" to now,
                        "updatedByPersonId" to actorPersonId,
                    ),
                )
                for (barangayId in toRemoveIds) {
                    txn.delete(firestore.collection(BARANGAYS_COLLECTION).document(claimId(congregationId, barangayId)))
                }
                for (b in toAdd) {
                    txn.set(
                        firestore.collection(BARANGAYS_COLLECTION).document(claimId(congregationId, b.id)),
                        TerritoryAssignmentBarangay(
                            id = claimId(congregationId, b.id), congregationId = congregationId, assignmentId = assignmentId,
                            groupId = groupId, groupName = groupName, provinceId = provinceId, muncityId = muncityId,
                            muncityName = muncityName, barangayId = b.id, barangayName = b.name,
                            createdAt = now, createdByPersonId = actorPersonId,
                        ),
                    )
                }
                // Group/municipality display fields on an unchanged claim
                // only need rewriting if the group itself changed — cheap to
                // always do, keeps denormalized groupName correct even if
                // only the Group was edited on this save.
                for (b in unchanged) {
                    txn.update(
                        firestore.collection(BARANGAYS_COLLECTION).document(claimId(congregationId, b.id)),
                        mapOf("groupId" to groupId, "groupName" to groupName, "muncityName" to muncityName),
                    )
                }
            }.await()

            offline.cacheFromServer(
                ASSIGNMENTS_COLLECTION, assignmentId,
                TerritoryAssignment(
                    id = assignmentId, congregationId = congregationId, groupId = groupId,
                    provinceId = provinceId, provinceName = provinceName, muncityId = muncityId, muncityName = muncityName,
                    createdAt = existingClaims.minOfOrNull { it.createdAt } ?: now, createdByPersonId = actorPersonId,
                    updatedAt = now, updatedByPersonId = actorPersonId,
                ),
            )
            toRemoveIds.forEach { offline.deleteFromServer(BARANGAYS_COLLECTION, claimId(congregationId, it)) }
            newBarangays.forEach { b ->
                offline.cacheFromServer(
                    BARANGAYS_COLLECTION, claimId(congregationId, b.id),
                    TerritoryAssignmentBarangay(
                        id = claimId(congregationId, b.id), congregationId = congregationId, assignmentId = assignmentId,
                        groupId = groupId, groupName = groupName, provinceId = provinceId, muncityId = muncityId,
                        muncityName = muncityName, barangayId = b.id, barangayName = b.name,
                        createdAt = now, createdByPersonId = actorPersonId,
                    ),
                )
            }
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "EDIT_TERRITORY_ASSIGNMENT",
                targetType = "TerritoryAssignment",
                targetId = assignmentId,
                congregationId = congregationId,
                details = "group=$groupName municipality=$muncityName barangays=${newBarangays.size}",
            )
            TerritoryAssignmentResult.Success(assignmentId)
        } catch (e: TerritoryConflictException) {
            TerritoryAssignmentResult.Conflict(e.barangayName, e.takenByGroupName)
        } catch (e: Exception) {
            TerritoryAssignmentResult.Error(e.localizedMessage ?: "Couldn't save this territory assignment.")
        }
    }

    /** Hard delete — see [TerritoryAssignment]'s own doc comment for why this
     * module has no Inactive state: an assignment that still "existed" would
     * keep every one of its barangays unavailable to every other Group. */
    suspend fun removeAssignment(assignmentId: String, congregationId: String, actorPersonId: String): TerritoryAssignmentResult {
        if (!connectivityObserver.isOnline()) return TerritoryAssignmentResult.Offline()
        return try {
            // Firestore transactions can't run an arbitrary query, only
            // get() on already-known refs — so the claim docs belonging to
            // this assignment are found first, outside the transaction.
            val claimDocs = firestore.collection(BARANGAYS_COLLECTION)
                .whereEqualTo("assignmentId", assignmentId)
                .get().await()
            val assignmentRef = firestore.collection(ASSIGNMENTS_COLLECTION).document(assignmentId)
            firestore.runTransaction { txn ->
                for (doc in claimDocs.documents) {
                    // Re-check inside the transaction: only delete a claim
                    // that still points at this exact assignment — guards
                    // the narrow race of someone editing this assignment
                    // (reassigning one of its barangays elsewhere) between
                    // the query above and this transaction's commit.
                    val snap = txn.get(doc.reference)
                    if (snap.getString("assignmentId") == assignmentId) txn.delete(doc.reference)
                }
                txn.delete(assignmentRef)
            }.await()
            offline.deleteFromServer(ASSIGNMENTS_COLLECTION, assignmentId)
            claimDocs.documents.forEach { offline.deleteFromServer(BARANGAYS_COLLECTION, it.id) }
            auditLogRepository.log(
                actorPersonId = actorPersonId,
                action = "REMOVE_TERRITORY_ASSIGNMENT",
                targetType = "TerritoryAssignment",
                targetId = assignmentId,
                congregationId = congregationId,
                details = "barangays=${claimDocs.size()}",
            )
            TerritoryAssignmentResult.Success(assignmentId)
        } catch (e: Exception) {
            TerritoryAssignmentResult.Error(e.localizedMessage ?: "Couldn't remove this territory assignment.")
        }
    }
}
