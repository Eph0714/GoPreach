package com.emfitsolutions.gopreach.ui.components.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.BuildConfig
import com.emfitsolutions.gopreach.data.sync.ConnectivityObserver
import com.emfitsolutions.gopreach.data.update.ApkDownloader
import com.emfitsolutions.gopreach.data.update.DownloadEvent
import com.emfitsolutions.gopreach.data.update.InstalledUpdateInfo
import com.emfitsolutions.gopreach.data.update.InstalledUpdateInfoStore
import com.emfitsolutions.gopreach.data.update.UpdateInfo
import com.emfitsolutions.gopreach.data.update.UpdateInstaller
import com.emfitsolutions.gopreach.data.update.UpdateManifestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "UpdateViewModel"

/** Mirrors the exact flow the app's auto-update UI walks through. */
sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data class UpToDate(val version: String) : UpdateCheckState()
    data class Available(val info: UpdateInfo, val currentVersion: String) : UpdateCheckState()
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateCheckState()
    data class ReadyToInstall(val info: UpdateInfo, val apkFile: File) : UpdateCheckState()
    data class Failed(val info: UpdateInfo?, val message: String) : UpdateCheckState()
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manifestRepository: UpdateManifestRepository,
    private val downloader: ApkDownloader,
    private val installer: UpdateInstaller,
    private val installedUpdateInfoStore: InstalledUpdateInfoStore,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val state: StateFlow<UpdateCheckState> = _state.asStateFlow()

    private var hasAutoChecked = false
    private val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** "Add a details of the newly installed update" — Settings' "App
     * Version" section. Non-null only when the last update this app
     * installed *through its own updater* is the version actually running
     * right now (see [InstalledUpdateInfoStore]'s doc comment for why that
     * can be null: a fresh install/sideload/Play install has no such
     * history yet). */
    val installedUpdateInfo: InstalledUpdateInfo?
        get() = installedUpdateInfoStore.read()?.takeIf { it.version == currentVersion }

    init {
        // "Refresh, Automatic Updates, Offline Sync" spec §12: "When the device
        // becomes online: Automatically check for application updates" — entirely
        // separate from [checkOnAppStart] (the once-per-process check) and never
        // triggered by Refresh or Sync to Server. Only fires on an actual
        // offline→online *transition*, not on the initial subscribe (which would
        // otherwise double up with checkOnAppStart for a launch that's already
        // online) and not repeatedly while already online.
        var wasOnline: Boolean? = null
        connectivityObserver.observe()
            .onEach { online ->
                val previouslyOffline = wasOnline == false
                wasOnline = online
                if (online && previouslyOffline) check(silent = true)
            }
            .launchIn(viewModelScope)
    }

    /** Called once from the app's root composable — checks silently and only
     * surfaces anything when an update genuinely exists, so a fresh launch
     * that's already current shows nothing at all (spec: don't repeatedly
     * annoy the user with unnecessary notifications). */
    fun checkOnAppStart() {
        if (hasAutoChecked) return
        hasAutoChecked = true
        check(silent = true)
    }

    /** Called from Settings' "Check for Updates" — always shows a result,
     * including the explicit "GoPreach is up to date" message. */
    fun checkManually() = check(silent = false)

    private fun check(silent: Boolean) {
        _state.value = UpdateCheckState.Checking
        viewModelScope.launch {
            manifestRepository.fetchLatest()
                .onSuccess { info ->
                    _state.value = if (manifestRepository.isNewer(currentVersion, info.version)) {
                        UpdateCheckState.Available(info, currentVersion)
                    } else {
                        UpdateCheckState.UpToDate(currentVersion)
                    }
                }
                .onFailure { e ->
                    Log.w(TAG, "Update check failed: ${e.message}")
                    // A failed *check* (e.g. offline) isn't the "Update Failed" flow —
                    // that's reserved for a failed download/install. Silently drop back
                    // to Idle so the app just proceeds as normal.
                    _state.value = UpdateCheckState.Idle
                }
            if (silent && _state.value is UpdateCheckState.UpToDate) {
                _state.value = UpdateCheckState.Idle
            }
        }
    }

    fun dismiss() {
        _state.value = UpdateCheckState.Idle
    }

    /** [UPDATE NOW] — download, then request installation immediately.
     * Per explicit request, GoPreach's own post-download checksum check
     * (the "Verifying Update..." step) is skipped — the APK goes straight
     * from download to the install handoff. Android's own Package Installer
     * still enforces its own signature check on the way in (an update
     * signed with a different key than the installed app is refused
     * regardless of anything here) — that's the OS-level guarantee this app
     * was never trying to duplicate; only GoPreach's *own additional*
     * integrity check is what's being skipped now. */
    fun updateNow() {
        val info = (_state.value as? UpdateCheckState.Available)?.info
            ?: (_state.value as? UpdateCheckState.Failed)?.info
            ?: return
        viewModelScope.launch {
            try {
                downloader.clearDownloads()
                val fileName = "GoPreach-${info.version}.apk"
                var lastFile: File? = null
                downloader.download(info.apkUrl, fileName).collect { event ->
                    when (event) {
                        is DownloadEvent.Progress -> _state.value = UpdateCheckState.Downloading(info, event.percent)
                        is DownloadEvent.Done -> lastFile = event.file
                    }
                }
                val apkFile = lastFile ?: throw Exception("Download did not complete")

                // Recorded *before* handing off to the Package Installer —
                // this is the last point the app is definitely still
                // running to write it; the process may well be replaced
                // once installation actually happens.
                installedUpdateInfoStore.save(
                    InstalledUpdateInfo(
                        version = info.version,
                        releaseNotes = info.releaseNotes,
                        apkUrl = info.apkUrl,
                        installedAt = System.currentTimeMillis(),
                    ),
                )
                _state.value = UpdateCheckState.ReadyToInstall(info, apkFile)
            } catch (e: Exception) {
                Log.w(TAG, "Update failed: ${e.message}", e)
                _state.value = UpdateCheckState.Failed(info, "The new version could not be downloaded. Your current version is still available.")
            }
        }
    }

    /** Called by the UI right before launching the install Intent — Android's
     * own Package Installer takes over from here (progress, signature check,
     * confirmation), which is why there's no "Installing…" state machine step
     * beyond this: this app hands off and gets out of the way, per spec §3. */
    fun installIntentFor(apkFile: File): Intent = installer.installIntent(apkFile)

    fun canInstall(): Boolean = installer.canInstall()
    fun requestInstallPermissionIntent(): Intent = installer.requestInstallPermissionIntent()

    fun retry() {
        val info = (_state.value as? UpdateCheckState.Failed)?.info
        _state.value = if (info != null) UpdateCheckState.Available(info, currentVersion) else UpdateCheckState.Idle
    }

    /** The text handed to Android's native share sheet — always the *current*
     * latest APK URL fetched moments ago, never a version baked into the app. */
    fun shareText(info: UpdateInfo): String =
        "GoPreach ${info.version} is now available!\nDownload the latest version here:\n${info.apkUrl}"

    /** "Add a link for the updated apk file after installation so that the
     * user can share the app to others" — Settings' "Share App" button.
     * Always fetches fresh from GitHub Releases rather than reusing
     * [installedUpdateInfo], so it keeps working (and points at the right
     * link) for a device with no update history yet, and never shares a
     * stale link if a newer release has shipped since this device last
     * updated. */
    suspend fun fetchLatestForShare(): Result<UpdateInfo> = manifestRepository.fetchLatest()
}
