package com.emfitsolutions.gopreach.ui.screens.home

import androidx.lifecycle.ViewModel
import com.emfitsolutions.gopreach.data.repository.AuthRepository
import com.emfitsolutions.gopreach.domain.SessionState
import com.emfitsolutions.gopreach.domain.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    userSession: UserSession,
    private val authRepository: AuthRepository,
) : ViewModel() {
    val state: StateFlow<SessionState> = userSession.state

    fun signOut() = authRepository.signOut()
}
