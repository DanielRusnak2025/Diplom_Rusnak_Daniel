package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class SetRoleRequest(val role: RoleDto)
