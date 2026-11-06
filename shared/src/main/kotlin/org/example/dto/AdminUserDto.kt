package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserDto(
    val id: Int,
    val nick: String,
    val email: String?,
    val role: RoleDto,
    val registrationDate: String?
)
