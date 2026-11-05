package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: Int,
    val key: String,
    val name: String,
    val subtopics: List<SubtopicDto>
)
