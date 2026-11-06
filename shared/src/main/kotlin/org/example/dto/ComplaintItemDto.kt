package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class ComplaintItemDto(
    val reportId: Int,
    val articleId: Int,
    val articleTitle: String?,
    val userId: Int,
    val userNick: String,
    val reasonCode: ReportReasonDto,
    val date: String?
)
