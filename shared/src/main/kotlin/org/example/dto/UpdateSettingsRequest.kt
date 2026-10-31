package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateSettingsRequest(
    val languages: List<String>? = null,
    val regions: List<String>? = null,
    val notificationFrequency: String? = null
)
