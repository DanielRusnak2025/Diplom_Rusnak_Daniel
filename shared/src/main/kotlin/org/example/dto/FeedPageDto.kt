package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class FeedPageDto(
    val articles: List<FeedArticleDto>,
    val page: Int,
    val pageSize: Int,
    val hasMore: Boolean
)
