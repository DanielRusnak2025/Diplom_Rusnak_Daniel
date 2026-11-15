package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserRatingsDto(
    val likeCount: Int,
    val dislikeCount: Int,
    val complaintCount: Int
)
