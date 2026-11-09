package org.example.androidapp.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.example.dto.LoginRequest
import org.example.dto.RefreshRequest
import org.example.dto.RegisterRequest
import org.example.dto.TokenPairDto
import org.example.dto.UserProfileDto

class AuthApi(
    private val baseUrl: String,
    private val anonClient: HttpClient,
    private val authClient: HttpClient,
) {
    suspend fun register(req: RegisterRequest): TokenPairDto =
        anonClient.post("$baseUrl/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    suspend fun login(req: LoginRequest): TokenPairDto =
        anonClient.post("$baseUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()

    suspend fun logout(refreshToken: String) {
        anonClient.post("$baseUrl/api/v1/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }
    }

    suspend fun me(): UserProfileDto =
        authClient.get("$baseUrl/api/v1/me").body()
}
