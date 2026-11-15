package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserRefDto(
    val userId: Int,
    val nick: String,
    val reasonCode: ReportReasonDto?
)
