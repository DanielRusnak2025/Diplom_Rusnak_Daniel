package org.example.androidapp.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import org.example.androidapp.data.TokenPair
import org.example.androidapp.data.TokenRepository
import org.example.dto.ErrorResponse
import org.example.dto.RefreshRequest
import org.example.dto.TokenPairDto

class ApiClient(
    val tokenRepository: TokenRepository,
    val baseUrl: String,
    anonEngine: HttpClientEngine = OkHttp.create(),
    authEngine: HttpClientEngine = OkHttp.create(),
) {
    private val _logoutEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val logoutEvents: SharedFlow<String> = _logoutEvents.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true }

    val anonClient: HttpClient = HttpClient(anonEngine) {
        install(ContentNegotiation) { json(json) }
        expectSuccess = false
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status.value !in 200..299) {
                    val err = try { response.body<ErrorResponse>() }
                    catch (_: Exception) { ErrorResponse("HTTP_ERROR", response.status.description) }
                    throw ApiException(err.code, err.message, response.status.value)
                }
            }
        }
    }

    val authClient: HttpClient = HttpClient(authEngine) {
        install(ContentNegotiation) { json(json) }
        expectSuccess = false
        install(Auth) {
            bearer {
                loadTokens {
                    tokenRepository.loadOnce()?.let { BearerTokens(it.access, it.refresh) }
                }
                refreshTokens {
                    val oldRefresh = oldTokens?.refreshToken ?: return@refreshTokens null
                    try {
                        val dto = anonClient.post("$baseUrl/api/v1/auth/refresh") {
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequest(oldRefresh))
                        }.body<TokenPairDto>()
                        val pair = TokenPair(dto.accessToken, dto.refreshToken)
                        tokenRepository.save(pair)
                        BearerTokens(dto.accessToken, dto.refreshToken)
                    } catch (e: ApiException) {
                        tokenRepository.clear()
                        _logoutEvents.tryEmit(e.code)
                        null
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status.value !in 200..299) {
                    val err = try { response.body<ErrorResponse>() }
                    catch (_: Exception) { ErrorResponse("HTTP_ERROR", response.status.description) }
                    throw ApiException(err.code, err.message, response.status.value)
                }
            }
        }
    }
}
