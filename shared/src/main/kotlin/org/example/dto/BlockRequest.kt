package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class BlockRequest(
    val type: BlockTypeDto,
    val tagId: Int? = null,
    val regionKey: String? = null,
    val publisher: String? = null
)
