package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuditLogItemDto(
    val id: Long,
    val actorId: Int?,
    val actorNick: String?,
    val action: String,
    val targetType: String?,
    val targetId: String?,
    val details: String?,
    val createdAt: String?
)
