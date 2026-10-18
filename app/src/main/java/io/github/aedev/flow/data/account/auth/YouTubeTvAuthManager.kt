package io.github.aedev.flow.data.account.auth

import io.github.aedev.flow.data.account.AccountProfile
import io.github.aedev.flow.innertube.YouTube
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

object YouTubeTvAuthManager {
    private const val CLIENT_ID = "861556737365-9au7k39sc18v2uf58832322if30ch815.apps.googleusercontent.com"
    private const val SCOPE = "https://www.googleapis.com/auth/youtube"

    private const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"

    @OptIn(ExperimentalSerializationApi::class)
    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
        }
    }

    @Serializable
    data class DeviceCodeResponse(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_url") val verificationUrl: String = "https://www.youtube.com/activate",
        @SerialName("expires_in") val expiresIn: Int = 1800,
        @SerialName("interval") val interval: Int = 5,
    )

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        @SerialName("token_type") val tokenType: String? = null,
        @SerialName("error") val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )

    sealed interface TvAuthState {
        data object Initial : TvAuthState

        data class CodeReceived(
            val userCode: String,
            val verificationUrl: String,
            val expiresInSeconds: Int,
        ) : TvAuthState

        data object Polling : TvAuthState

        data class Success(
            val profile: AccountProfile,
        ) : TvAuthState

        data class Error(
            val message: String,
        ) : TvAuthState
    }

    fun startTvActivation(): Flow<TvAuthState> =
        flow {
            emit(TvAuthState.Initial)

            val codeResult =
                runCatching {
                    httpClient
                        .submitForm(
                            url = DEVICE_CODE_URL,
                            formParameters =
                                parameters {
                                    append("client_id", CLIENT_ID)
                                    append("scope", SCOPE)
                                },
                        ).body<DeviceCodeResponse>()
                }

            val codeResponse =
                codeResult.getOrElse { error ->
                    emit(TvAuthState.Error(error.message ?: "Failed to request device activation code"))
                    return@flow
                }

            emit(
                TvAuthState.CodeReceived(
                    userCode = codeResponse.userCode,
                    verificationUrl = codeResponse.verificationUrl,
                    expiresInSeconds = codeResponse.expiresIn,
                ),
            )

            val intervalMs = (codeResponse.interval.coerceAtLeast(3)) * 1000L
            val maxAttempts = (codeResponse.expiresIn / codeResponse.interval.coerceAtLeast(3))

            for (attempt in 0 until maxAttempts) {
                delay(intervalMs)

                val tokenResult =
                    runCatching {
                        httpClient
                            .submitForm(
                                url = TOKEN_URL,
                                formParameters =
                                    parameters {
                                        append("client_id", CLIENT_ID)
                                        append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                                        append("device_code", codeResponse.deviceCode)
                                    },
                            ).body<TokenResponse>()
                    }

                val tokenResponse = tokenResult.getOrNull()
                if (tokenResponse != null) {
                    if (!tokenResponse.accessToken.isNullOrBlank()) {
                        // Authenticated successfully with access token
                        val accessToken = tokenResponse.accessToken
                        val refreshToken = tokenResponse.refreshToken.orEmpty()
                        val cookieString = "Authorization=Bearer $accessToken; OAuthRefreshToken=$refreshToken"

                        // Fetch profile details
                        val previousCookie = YouTube.cookie
                        try {
                            YouTube.cookie = cookieString
                            YouTube.useLoginForBrowse = true

                            val accountInfo = YouTube.accountInfo().getOrNull()

                            val accountId =
                                accountInfo?.channelHandle?.removePrefix("@")
                                    ?: UUID.randomUUID().toString()
                            val profile =
                                AccountProfile(
                                    id = accountId,
                                    name = accountInfo?.name ?: "YouTube Account",
                                    email = accountInfo?.email,
                                    channelHandle = accountInfo?.channelHandle,
                                    avatarUrl = accountInfo?.thumbnailUrl,
                                    cookie = cookieString,
                                    dataSyncId = YouTube.dataSyncId,
                                    isActive = true,
                                )

                            emit(TvAuthState.Success(profile))
                            return@flow
                        } catch (e: Exception) {
                            emit(TvAuthState.Error("Failed to fetch account profile: ${e.message}"))
                            return@flow
                        } finally {
                            YouTube.cookie = previousCookie
                        }
                    } else if (tokenResponse.error == "authorization_pending") {
                        // Still waiting for user to authorize on youtube.com/activate
                        continue
                    } else if (tokenResponse.error == "slow_down") {
                        delay(intervalMs)
                        continue
                    } else if (tokenResponse.error != null) {
                        emit(TvAuthState.Error(tokenResponse.errorDescription ?: tokenResponse.error))
                        return@flow
                    }
                }
            }

            emit(TvAuthState.Error("Activation code expired. Please try again."))
        }.flowOn(Dispatchers.IO)
}
