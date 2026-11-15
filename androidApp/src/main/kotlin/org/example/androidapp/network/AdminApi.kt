package org.example.androidapp.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.example.dto.ComplaintPageDto
import org.example.dto.HiddenRequest

class AdminApi(
    private val baseUrl: String,
    private val authClient: HttpClient,
) {
    suspend fun complaints(cursor: String? = null, limit: Int = 20): ComplaintPageDto =
        authClient.get("$baseUrl/api/v1/admin/complaints") {
            if (cursor != null) parameter("cursor", cursor)
            parameter("limit", limit)
        }.body()

    suspend fun hideArticle(articleId: Int, hidden: Boolean) {
        authClient.put("$baseUrl/api/v1/admin/articles/$articleId/hidden") {
            contentType(ContentType.Application.Json)
            setBody(HiddenRequest(hidden))
        }
    }
}
