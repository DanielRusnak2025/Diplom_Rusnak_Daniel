package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuditPageDto(
    val items: List<AuditLogItemDto>,
    val nextCursor: String?
)
