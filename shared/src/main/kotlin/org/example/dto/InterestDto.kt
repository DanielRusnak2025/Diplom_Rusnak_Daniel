package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class InterestDto(val tagId: Int, val key: String, val name: String, val percent: Int)
