package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class FeedArticleDto(
    val id: Int,
    val title: String,
    val link: String,
    val publisher: String?,
    val language: String?,
    val region: String?,
    val score: Int,
    val rating: Int?,
    val likePercent: Int?,
    val tags: List<ArticleTagDto> = emptyList()
)
