package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val code: String, val message: String)
