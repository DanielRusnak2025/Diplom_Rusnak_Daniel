package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserDetailDto(
    val id: Int,
    val nick: String,
    val email: String?,
    val registrationDate: String?,
    val role: RoleDto,
    val languages: List<String>,
    val regions: List<String>,
    val interests: List<AdminInterestDto>,
    val blocks: List<AdminBlockDto>,
    val ratings: AdminUserRatingsDto
)
