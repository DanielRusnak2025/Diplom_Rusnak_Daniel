package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
enum class BlockTypeDto {
    TAG,
    REGION,
    PUBLISHER
}
