package com.emfitsolutions.gopreach.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.repository.AuthRepository
import com.emfitsolutions.gopreach.data.sync.ConnectivityObserver
import com.emfitsolutions.gopreach.data.sync.OfflineFirestoreRepository
import com.emfitsolutions.gopreach.data.sync.SyncScheduler
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
    connectivityObserver: ConnectivityObserver,
    offlineFirestoreRepository: OfflineFirestoreRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    val state: StateFlow<SessionState> = userSession.state

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), connectivityObserver.isOnline())

    val pendingSyncCount: StateFlow<Int> = offlineFirestoreRepository.observePendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun signOut() = authRepository.signOut()

    /** Pull-to-refresh on the main dashboard (spec: "show the update when the
     * user refreshes the main form") — flushes any queued offline writes and
     * (via the Activity-scoped [com.emfitsolutions.gopreach.ui.components.update.UpdateViewModel]
     * the screen also calls) re-checks for an app update, surfacing the same
     * "Update Available"/"GoPreach is up to date" result Settings' manual
     * check shows. Every other data flow on this screen already updates live
     * off its own Firestore listener, so there's no separate "reload" step
     * needed for the numbers themselves — this is what a "refresh" gesture
     * can actually still *do* in an offline-first, always-live app. */
    fun refreshData() = syncScheduler.requestSyncNow()
}
