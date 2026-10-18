package io.github.aedev.flow.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.account.AccountProfile
import io.github.aedev.flow.data.account.AccountRepository
import io.github.aedev.flow.data.account.auth.CookieAuthManager
import io.github.aedev.flow.data.account.auth.YouTubeTvAuthManager
import io.github.aedev.flow.data.account.notifications.YouTubeNotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
        private val youtubeNotificationRepository: YouTubeNotificationRepository,
    ) : ViewModel() {
        val profiles = accountRepository.profiles
        val activeAccount = accountRepository.activeAccount

        private val _tvAuthState = MutableStateFlow<YouTubeTvAuthManager.TvAuthState>(YouTubeTvAuthManager.TvAuthState.Initial)
        val tvAuthState: StateFlow<YouTubeTvAuthManager.TvAuthState> = _tvAuthState.asStateFlow()

        private val _cookieError = MutableStateFlow<String?>(null)
        val cookieError: StateFlow<String?> = _cookieError.asStateFlow()

        private val _isSyncingNotifications = MutableStateFlow(false)
        val isSyncingNotifications: StateFlow<Boolean> = _isSyncingNotifications.asStateFlow()

        init {
            viewModelScope.launch {
                accountRepository.initialize()
            }
        }

        fun switchAccount(accountId: String?) {
            viewModelScope.launch {
                accountRepository.switchActiveAccount(accountId)
            }
        }

        fun removeAccount(accountId: String) {
            viewModelScope.launch {
                accountRepository.removeProfile(accountId)
            }
        }

        fun startTvActivation() {
            viewModelScope.launch {
                YouTubeTvAuthManager.startTvActivation().collect { state ->
                    _tvAuthState.value = state
                    if (state is YouTubeTvAuthManager.TvAuthState.Success) {
                        accountRepository.addOrUpdateProfile(state.profile, setAsActive = true)
                    }
                }
            }
        }

        fun resetTvActivationState() {
            _tvAuthState.value = YouTubeTvAuthManager.TvAuthState.Initial
        }

        fun authenticateWithCookie(cookieString: String) {
            viewModelScope.launch {
                _cookieError.value = null
                val result = CookieAuthManager.authenticateWithCookie(cookieString)
                result
                    .onSuccess { profile ->
                        accountRepository.addOrUpdateProfile(profile, setAsActive = true)
                    }.onFailure { error ->
                        _cookieError.value = error.message ?: "Failed to validate cookie"
                    }
            }
        }

        fun clearCookieError() {
            _cookieError.value = null
        }

        fun syncYouTubeNotifications() {
            viewModelScope.launch {
                _isSyncingNotifications.value = true
                youtubeNotificationRepository.fetchAndSyncYouTubeNotifications()
                _isSyncingNotifications.value = false
            }
        }
    }
