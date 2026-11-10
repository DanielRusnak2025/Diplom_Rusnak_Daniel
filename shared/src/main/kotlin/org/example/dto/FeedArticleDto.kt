package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class FeedArticleDto(
    val id: Int,
    val title: String?,
    val link: String?,
    val publisher: String?,
    val publicationDate: String?,
    val language: String?,
    val region: String?,
    val mainCategory: TagRefDto?,
    val subtopic: TagRefDto?,
    val myRating: Int?,
    val matchPercent: Int?
)
