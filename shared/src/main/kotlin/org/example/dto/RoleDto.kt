package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
enum class RoleDto {
    USER,
    MODERATOR,
    ADMIN
}
