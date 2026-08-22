package com.emfitsolutions.gopreach.data.repository

import android.net.Uri
import com.emfitsolutions.gopreach.data.model.AppSettings
import com.emfitsolutions.gopreach.data.sync.OfflineFirestoreRepository
import com.emfitsolutions.gopreach.data.sync.mirrorFirestoreCollection
import com.emfitsolutions.gopreach.di.ApplicationScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val COLLECTION = "appSettings"

/**
 * Global app settings — currently just the Super-Admin-customizable logo (spec §1,
 * Control Panel module in spec §5.1/§3). One document ([AppSettings.GLOBAL_ID]),
 * modeled as a one-row "collection" so it reuses the same offline cache/sync path
 * as everything else (see [OfflineFirestoreRepository]).
 */
@Singleton
class AppSettingsRepository @Inject constructor(
    private val offline: OfflineFirestoreRepository,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auditLogRepository: AuditLogRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    fun observe(): Flow<AppSettings> =
        offline.observeCollection<AppSettings>(COLLECTION).map { list ->
            list.firstOrNull { it.id == AppSettings.GLOBAL_ID } ?: AppSettings()
        }

    /** Uploads [imageUri] to Storage and points [AppSettings.logoUrl] at it. Super-Admin
     * only — enforced by the Control Panel screen's visibility, mirrored server-side
     * by Firestore/Storage security rules on this path. */
    suspend fun uploadLogo(imageUri: Uri, updatedByPersonId: String) {
        val ref = storage.reference.child("app-settings/logo.png")
        ref.putFile(imageUri).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        offline.save(
            COLLECTION,
            AppSettings.GLOBAL_ID,
            AppSettings(
                logoUrl = downloadUrl,
                updatedAt = System.currentTimeMillis(),
                updatedByPersonId = updatedByPersonId,
            ),
        )
        auditLogRepository.log(actorPersonId = updatedByPersonId, action = "UPLOAD_LOGO")
    }

    fun startRemoteSync(): Flow<Unit> =
        mirrorFirestoreCollection(firestore, offline, appScope, COLLECTION, AppSettings::class.java) { it.id }
}
