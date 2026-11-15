package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminArticlePageDto(
    val items: List<AdminArticleDto>,
    val nextCursor: String?
)
