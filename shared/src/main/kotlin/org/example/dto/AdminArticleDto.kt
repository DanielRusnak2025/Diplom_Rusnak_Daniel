package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminArticleDto(
    val id: Int,
    val title: String?,
    val link: String?,
    val publisher: String?,
    val language: String?,
    val region: String?,
    val publicationDate: String?,
    val hidden: Boolean,
    val classified: Boolean
)
