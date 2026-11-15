package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminInterestDto(
    val tagKey: String,
    val nameEn: String?,
    val nameRu: String?,
    val percent: Int
)
