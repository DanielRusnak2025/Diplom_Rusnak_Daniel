package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubtopicDto(val id: Int, val key: String, val name: String)
