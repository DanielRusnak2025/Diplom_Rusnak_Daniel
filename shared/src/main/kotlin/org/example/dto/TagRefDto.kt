package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class TagRefDto(val id: Int, val key: String, val name: String, val percent: Int)
