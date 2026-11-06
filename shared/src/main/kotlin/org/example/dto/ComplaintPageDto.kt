package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class ComplaintPageDto(
    val items: List<ComplaintItemDto>,
    val nextCursor: String?
)
