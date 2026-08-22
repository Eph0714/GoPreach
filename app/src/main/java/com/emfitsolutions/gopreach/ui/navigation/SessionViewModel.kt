package com.emfitsolutions.gopreach.ui.navigation

import androidx.lifecycle.ViewModel
import com.emfitsolutions.gopreach.domain.SessionState
import com.emfitsolutions.gopreach.domain.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Thin Compose-facing wrapper so [GoPreachNavGraph] can drive routing off the
 * app-wide [UserSession] without every screen needing its own copy of it. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    userSession: UserSession,
) : ViewModel() {
    val state: StateFlow<SessionState> = userSession.state
}
