package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val nick: String,
    val password: String,
    val email: String? = null,
    val languages: List<String> = emptyList(),
    val regions: List<String> = emptyList()
)
