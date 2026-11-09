package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class ComplaintRequest(val reasonCode: ReportReasonDto)
