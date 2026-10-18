package io.github.aedev.flow.data.account

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.innertube.YouTube
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val preferences = AccountPreferences(context)

        val profiles: Flow<List<AccountProfile>> = preferences.profiles
        val activeAccountId: Flow<String?> = preferences.activeAccountId

        val activeAccount: Flow<AccountProfile?> =
            combine(profiles, activeAccountId) { list, activeId ->
                list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
            }

        suspend fun initialize() {
            val active = activeAccount.first()
            applyActiveAccountToYouTube(active)
        }

        suspend fun addOrUpdateProfile(
            profile: AccountProfile,
            setAsActive: Boolean = true,
        ) {
            val currentProfiles = profiles.first().toMutableList()
            val index = currentProfiles.indexOfFirst { it.id == profile.id }
            if (index >= 0) {
                currentProfiles[index] = profile
            } else {
                currentProfiles.add(profile)
            }

            val activeId = if (setAsActive) profile.id else (activeAccountId.first() ?: profile.id)
            val updatedProfiles =
                currentProfiles.map { p ->
                    p.copy(isActive = (p.id == activeId))
                }

            preferences.saveProfiles(updatedProfiles, activeId)
            val active = updatedProfiles.firstOrNull { it.id == activeId }
            applyActiveAccountToYouTube(active)
        }

        suspend fun switchActiveAccount(accountId: String?) {
            val currentProfiles = profiles.first()
            val targetId =
                accountId?.takeIf { id -> currentProfiles.any { it.id == id } }
                    ?: currentProfiles.firstOrNull()?.id

            val updatedProfiles =
                currentProfiles.map { p ->
                    p.copy(isActive = (p.id == targetId))
                }

            preferences.saveProfiles(updatedProfiles, targetId)
            val active = updatedProfiles.firstOrNull { it.id == targetId }
            applyActiveAccountToYouTube(active)
        }

        suspend fun removeProfile(accountId: String) {
            val currentProfiles = profiles.first().filter { it.id != accountId }
            val currentActiveId = activeAccountId.first()
            val newActiveId =
                if (currentActiveId == accountId) {
                    currentProfiles.firstOrNull()?.id
                } else {
                    currentActiveId
                }

            val updatedProfiles =
                currentProfiles.map { p ->
                    p.copy(isActive = (p.id == newActiveId))
                }

            preferences.saveProfiles(updatedProfiles, newActiveId)
            val active = updatedProfiles.firstOrNull { it.id == newActiveId }
            applyActiveAccountToYouTube(active)
        }

        fun applyActiveAccountToYouTube(profile: AccountProfile?) {
            if (profile != null && profile.cookie.isNotBlank()) {
                YouTube.cookie = profile.cookie
                YouTube.dataSyncId = profile.dataSyncId
                YouTube.useLoginForBrowse = true
            } else {
                YouTube.cookie = null
                YouTube.dataSyncId = null
                YouTube.useLoginForBrowse = false
            }
        }
    }
