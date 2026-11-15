package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminArticleDetailDto(
    val id: Int,
    val title: String?,
    val link: String?,
    val publisher: String?,
    val language: String?,
    val region: String?,
    val publicationDate: String?,
    val hidden: Boolean,
    val classified: Boolean,
    val tags: List<TagRefDto>,
    val likeCount: Int,
    val dislikeCount: Int,
    val complaintCount: Int,
    val complainants: List<AdminUserRefDto>
)
