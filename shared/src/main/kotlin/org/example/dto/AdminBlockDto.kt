package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminBlockDto(
    val blockType: BlockTypeDto,
    val tagKey: String?,
    val region: String?,
    val publisher: String?
)
