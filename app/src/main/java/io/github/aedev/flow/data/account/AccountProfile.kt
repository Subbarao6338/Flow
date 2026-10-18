package io.github.aedev.flow.data.account

import kotlinx.serialization.Serializable

@Serializable
data class AccountProfile(
    val id: String,
    val name: String,
    val email: String? = null,
    val channelHandle: String? = null,
    val avatarUrl: String? = null,
    val cookie: String,
    val dataSyncId: String? = null,
    val isActive: Boolean = false,
)
