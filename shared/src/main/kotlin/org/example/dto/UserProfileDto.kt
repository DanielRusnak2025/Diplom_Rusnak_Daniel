package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDto(
    val id: Int,
    val nick: String,
    val email: String?,
    val role: RoleDto,
    val languages: List<String>,
    val regions: List<String>,
    val notificationFrequency: String
)
