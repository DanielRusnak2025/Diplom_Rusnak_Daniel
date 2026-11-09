package org.example.androidapp.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.dto.FeedPageDto
import org.example.dto.ReportReasonDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedApiTest {

    private val baseUrl = "https://localhost:8443"
    private val emptyFeedJson = Json.encodeToString(FeedPageDto(emptyList(), null))

    private fun OutgoingContent.readBodyString(): String = when (this) {
        is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
        else -> ""
    }

    private fun mockClient(handler: io.ktor.client.engine.mock.MockRequestHandler): HttpClient =
        HttpClient(MockEngine { handler(it) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    @Test
    fun `feed GET sends cursor and limit as query params`() = runTest {
        var capturedUrl: String? = null
        val client = mockClient { request ->
            capturedUrl = request.url.toString()
            respond(
                emptyFeedJson,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        FeedApi(baseUrl, client).feed(cursor = "abc123", limit = 10)

        val url = capturedUrl!!
        assertTrue(url.contains("cursor=abc123"), "Expected cursor param, got: $url")
        assertTrue(url.contains("limit=10"), "Expected limit param, got: $url")
    }

    @Test
    fun `feed GET omits cursor when null`() = runTest {
        var capturedUrl: String? = null
        val client = mockClient { request ->
            capturedUrl = request.url.toString()
            respond(
                emptyFeedJson,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        FeedApi(baseUrl, client).feed(cursor = null, limit = 20)

        val url = capturedUrl!!
        assertFalse(url.contains("cursor"), "Expected no cursor param, got: $url")
        assertTrue(url.contains("limit=20"), "Expected limit param, got: $url")
    }

    @Test
    fun `rate POST sends correct body with rating only`() = runTest {
        var capturedBody = ""
        var capturedMethod: HttpMethod? = null
        var capturedPath = ""
        val client = mockClient { request ->
            capturedBody = request.body.readBodyString()
            capturedMethod = request.method
            capturedPath = request.url.encodedPath
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }

        FeedApi(baseUrl, client).rate(articleId = 42, rating = 1)

        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("/api/v1/articles/42/rate", capturedPath)
        assertEquals("""{"rating":1}""", capturedBody)
    }

    @Test
    fun `deleteRate sends DELETE to correct path`() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedPath = ""
        val client = mockClient { request ->
            capturedMethod = request.method
            capturedPath = request.url.encodedPath
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }

        FeedApi(baseUrl, client).deleteRate(articleId = 7)

        assertEquals(HttpMethod.Delete, capturedMethod)
        assertEquals("/api/v1/articles/7/rate", capturedPath)
    }

    @Test
    fun `complain POST sends correct body with reasonCode`() = runTest {
        var capturedBody = ""
        var capturedMethod: HttpMethod? = null
        var capturedPath = ""
        val client = mockClient { request ->
            capturedBody = request.body.readBodyString()
            capturedMethod = request.method
            capturedPath = request.url.encodedPath
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }

        FeedApi(baseUrl, client).complain(articleId = 5, reason = ReportReasonDto.NOT_AN_ARTICLE)

        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("/api/v1/articles/5/complain", capturedPath)
        assertEquals("""{"reasonCode":"NOT_AN_ARTICLE"}""", capturedBody)
    }
}
