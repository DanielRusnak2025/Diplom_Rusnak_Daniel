package org.example.androidapp.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.example.dto.InterestDto
import org.example.dto.SetInterestsRequest
import org.example.dto.UpdateSettingsRequest
import org.example.dto.UserProfileDto

class MeApi(
    private val baseUrl: String,
    private val authClient: HttpClient,
) {
    suspend fun me(): UserProfileDto =
        authClient.get("$baseUrl/api/v1/me").body()

    suspend fun interests(): List<InterestDto> =
        authClient.get("$baseUrl/api/v1/me/interests").body()

    suspend fun setInterests(categoryIds: List<Int>) {
        authClient.put("$baseUrl/api/v1/me/interests") {
            contentType(ContentType.Application.Json)
            setBody(SetInterestsRequest(categoryIds))
        }
    }

    suspend fun updateSettings(languages: List<String>, regions: List<String>) {
        authClient.put("$baseUrl/api/v1/me/settings") {
            contentType(ContentType.Application.Json)
            setBody(UpdateSettingsRequest(languages = languages, regions = regions))
        }
    }
}
