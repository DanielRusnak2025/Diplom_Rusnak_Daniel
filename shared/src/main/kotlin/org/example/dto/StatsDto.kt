package org.example.dto

import kotlinx.serialization.Serializable

@Serializable
data class StatsDto(
    val totalArticles: Long,
    val classifiedArticles: Long,
    val pendingQueue: Long,
    val totalUsers: Long,
    val openComplaints: Long
)
