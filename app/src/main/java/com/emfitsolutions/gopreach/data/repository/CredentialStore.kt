package com.emfitsolutions.gopreach.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Remember me" storage for the login screen (spec-adjacent UX addition, not part
 * of the original role/permission model). Backed by [EncryptedSharedPreferences]
 * (AES-256, key material held in the Android Keystore) rather than plain
 * SharedPreferences, since this is the one place in the app that persists a raw
 * password on-device.
 *
 * A stored credential pair is what the biometric sign-in flow unlocks — biometric
 * auth never talks to Firebase itself, it just gates read access to what's saved
 * here, then the normal [AuthRepository.signIn] call runs as usual.
 */
@Singleton
class CredentialStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "gopreach_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(username: String, password: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun read(): Pair<String, String>? {
        val username = prefs.getString(KEY_USERNAME, null)
        val password = prefs.getString(KEY_PASSWORD, null)
        return if (username != null && password != null) username to password else null
    }

    fun hasSavedCredentials(): Boolean = read() != null

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}
