package org.example.androidapp.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.example.dto.CategoryDto
import org.example.dto.LanguageDto
import org.example.dto.RegionDto

class MetaApi(
    private val baseUrl: String,
    private val authClient: HttpClient,
) {
    suspend fun regions(): List<RegionDto> =
        authClient.get("$baseUrl/api/v1/regions").body()

    suspend fun languages(): List<LanguageDto> =
        authClient.get("$baseUrl/api/v1/languages").body()

    suspend fun categories(): List<CategoryDto> =
        authClient.get("$baseUrl/api/v1/categories").body()
}
