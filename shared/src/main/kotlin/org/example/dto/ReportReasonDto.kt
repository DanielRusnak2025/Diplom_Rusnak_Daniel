package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
enum class ReportReasonDto {
    NOT_INTERESTED_TOPIC,
    UNWANTED_REGION,
    UNWANTED_PUBLISHER,
    NOT_AN_ARTICLE,
    BROKEN_LINK,
    MISLEADING,
    OTHER
}
