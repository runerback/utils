package com.runerback.ntfymgr.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
class LoginResponse(
    val token: String,
)

@Serializable
class AccessItem(
    val topic: String,
    val permission: String,
    val provisioned: Boolean = false,
)

@Serializable
class TokenItem(
    val value: String,
    val label: String? = null,
    val expires: String,
    @SerialName("last_origin") val lastOrigin: String,
    @SerialName("last_access") val lastAccess: String,
    val provisioned: Boolean = false,
)

@Serializable
class UserItem(
    val name: String,
    val role: String,
    val tier: String,
    val provisioned: Boolean = false,
    val accesses: List<AccessItem> = emptyList(),
    val tokens: List<TokenItem> = emptyList(),
)

@Serializable
class TopicAccessor(
    val username: String,
    val permission: String,
)

@Serializable
class TopicItem(
    val name: String,
    val accessors: List<TopicAccessor> = emptyList(),
)

@Serializable
class UserCreateRequest(
    val username: String,
    val password: String,
)

@Serializable
class AccessRequest(
    val topic: String,
    val permission: String = "read-write",
)

@Serializable
class TopicAccessRequest(
    val username: String,
    val permission: String = "read-write",
)

@Serializable
class TokenCreateRequest(
    val expires: String = "",
    val label: String = "",
)

@Serializable
class MessageResponse(
    val detail: String,
)
