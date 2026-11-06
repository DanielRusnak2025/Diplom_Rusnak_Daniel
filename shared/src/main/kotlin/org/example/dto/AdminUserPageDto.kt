package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserPageDto(
    val items: List<AdminUserDto>,
    val nextCursor: String?
)
