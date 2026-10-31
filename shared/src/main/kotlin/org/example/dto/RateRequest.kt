package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class RateRequest(val articleId: Int, val rating: Int)
