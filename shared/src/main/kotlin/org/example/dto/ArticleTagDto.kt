package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class ArticleTagDto(
    val tagKey: String,
    val name: String,
    val kind: String,
    val percent: Int,
    val parentKey: String? = null
)
