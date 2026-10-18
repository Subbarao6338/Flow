package io.github.aedev.flow.data.account.auth

import io.github.aedev.flow.data.account.AccountProfile
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.utils.parseCookieString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object CookieAuthManager {
    /**
     * Validates a raw cookie string by attempting to fetch the account menu from InnerTube.
     * Returns an [AccountProfile] on success, or throws an [IllegalArgumentException] if invalid.
     */
    suspend fun authenticateWithCookie(cookieString: String): Result<AccountProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanedCookie = cookieString.trim()
                require(cleanedCookie.isNotBlank()) { "Cookie string cannot be empty" }

                val cookieMap = parseCookieString(cleanedCookie)
                require(cookieMap.containsKey("SAPISID") || cookieMap.containsKey("SID")) {
                    "Invalid YouTube cookie: Missing SAPISID or SID token"
                }

                // Temporarily apply cookie to fetch account info
                val previousCookie = YouTube.cookie
                val previousDataSyncId = YouTube.dataSyncId
                val previousUseLogin = YouTube.useLoginForBrowse

                try {
                    YouTube.cookie = cleanedCookie
                    YouTube.useLoginForBrowse = true

                    val accountInfoResult = YouTube.accountInfo()
                    val accountInfo = accountInfoResult.getOrThrow()

                    val accountId =
                        cookieMap["SAPISID"]?.take(16)
                            ?: accountInfo.channelHandle?.removePrefix("@")
                            ?: UUID.randomUUID().toString()

                    AccountProfile(
                        id = accountId,
                        name = accountInfo.name,
                        email = accountInfo.email,
                        channelHandle = accountInfo.channelHandle,
                        avatarUrl = accountInfo.thumbnailUrl,
                        cookie = cleanedCookie,
                        dataSyncId = YouTube.dataSyncId,
                        isActive = true,
                    )
                } finally {
                    // Restore previous static state (the repository will apply active profile)
                    YouTube.cookie = previousCookie
                    YouTube.dataSyncId = previousDataSyncId
                    YouTube.useLoginForBrowse = previousUseLogin
                }
            }
        }
}
