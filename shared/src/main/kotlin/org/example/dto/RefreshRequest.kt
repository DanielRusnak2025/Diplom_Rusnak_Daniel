package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class RefreshRequest(val refreshToken: String)
