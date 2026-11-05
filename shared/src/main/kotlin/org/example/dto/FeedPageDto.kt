package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class FeedPageDto(val items: List<FeedArticleDto>, val nextCursor: String?)
