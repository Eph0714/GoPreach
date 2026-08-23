package com.emfitsolutions.gopreach.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.repository.AuthRepository
import com.emfitsolutions.gopreach.data.sync.ConnectivityObserver
import com.emfitsolutions.gopreach.data.sync.OfflineFirestoreRepository
import com.emfitsolutions.gopreach.domain.SessionState
import com.emfitsolutions.gopreach.domain.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    userSession: UserSession,
    private val authRepository: AuthRepository,
    private val connectivityObserver: ConnectivityObserver,
    offlineFirestoreRepository: OfflineFirestoreRepository,
) : ViewModel() {
    val state: StateFlow<SessionState> = userSession.state

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), connectivityObserver.isOnline())

    val pendingSyncCount: StateFlow<Int> = offlineFirestoreRepository.observePendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun signOut() = authRepository.signOut()

    /** Pull-to-refresh on the Main Form — per the "Refresh, Automatic Updates,
     * Offline Sync" spec's explicit separation requirement (§1/§18), Refresh
     * must **never** check for an app update and must **never** upload pending
     * local changes (that's [com.emfitsolutions.gopreach.ui.components.update
     * .UpdateViewModel] and [com.emfitsolutions.gopreach.ui.components
     * .SyncToServerButton]'s job respectively — this function used to call
     * both, which was exactly the bug this spec called out).
     *
     * What Refresh actually has left to *do*: every screen already renders off
     * a Room cache kept continuously current by live Firestore listeners while
     * online (or the last-synced snapshot while offline) — there's no separate
     * "fetch" step to trigger for the data itself. Returns whether the device
     * was online at the moment of refresh purely so the UI can show a distinct
     * "Refreshed"/"Showing offline data" moment; it doesn't affect what data is
     * shown, since that's already live either way. */
    fun refreshData(): Boolean = connectivityObserver.isOnline()
}
