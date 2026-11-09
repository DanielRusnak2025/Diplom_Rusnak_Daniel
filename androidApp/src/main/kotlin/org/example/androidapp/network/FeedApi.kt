package org.example.androidapp.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.example.dto.ComplaintRequest
import org.example.dto.FeedPageDto
import org.example.dto.RateRequest
import org.example.dto.ReportReasonDto

class FeedApi(private val baseUrl: String, private val authClient: HttpClient) {

    suspend fun feed(cursor: String? = null, limit: Int = 20): FeedPageDto =
        authClient.get("$baseUrl/api/v1/feed") {
            if (cursor != null) parameter("cursor", cursor)
            parameter("limit", limit)
        }.body()

    suspend fun rate(articleId: Int, rating: Int) {
        authClient.post("$baseUrl/api/v1/articles/$articleId/rate") {
            contentType(ContentType.Application.Json)
            setBody(RateRequest(rating))
        }
    }

    suspend fun deleteRate(articleId: Int) {
        authClient.delete("$baseUrl/api/v1/articles/$articleId/rate")
    }

    suspend fun complain(articleId: Int, reason: ReportReasonDto) {
        authClient.post("$baseUrl/api/v1/articles/$articleId/complain") {
            contentType(ContentType.Application.Json)
            setBody(ComplaintRequest(reason))
        }
    }
}
