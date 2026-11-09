@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.example.androidapp.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.androidapp.data.FakeTokenRepository
import org.example.androidapp.data.TokenPair
import org.example.dto.ErrorResponse
import org.example.dto.LoginRequest
import org.example.dto.RegisterRequest
import org.example.dto.TokenPairDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

private val testJson = Json { ignoreUnknownKeys = true }

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

class ApiClientTest {

    @Test
    fun `5 concurrent 401s call refresh exactly once`() = runTest {
        val refreshCount = AtomicInteger(0)
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("old-access", "old-refresh"))

        val engine = MockEngine { req ->
            when {
                req.url.encodedPath.contains("/auth/refresh") -> {
                    refreshCount.incrementAndGet()
                    respond(
                        content = testJson.encodeToString(TokenPairDto("new-access", "new-refresh", 900L)),
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
                req.headers[HttpHeaders.Authorization] == "Bearer old-access" ->
                    respond("", HttpStatusCode.Unauthorized)
                else ->
                    respond("{}", HttpStatusCode.OK, jsonHeaders())
            }
        }

        val client = ApiClient(fakeRepo, "https://test.example.com", engine, engine)
        val statuses = java.util.Collections.synchronizedList(mutableListOf<Int>())

        coroutineScope {
            (1..5).map {
                async {
                    try {
                        val resp = client.authClient.get("https://test.example.com/api/v1/feed")
                        statuses.add(resp.status.value)
                    } catch (_: Exception) { }
                }
            }.forEach { it.await() }
        }

        assertEquals(1, refreshCount.get())
        assertEquals("new-access", fakeRepo.loadOnce()?.access)
        assertEquals(5, statuses.size)
        assertTrue(statuses.all { it == 200 })
    }

    @Test
    fun `TOKEN_REUSED 403 clears tokens and emits logout event`() = runTest {
        val fakeRepo = FakeTokenRepository()
        fakeRepo.save(TokenPair("old-access", "old-refresh"))

        val engine = MockEngine { req ->
            when {
                req.url.encodedPath.contains("/auth/refresh") ->
                    respond(
                        content = testJson.encodeToString(ErrorResponse("TOKEN_REUSED", "Reuse detected")),
                        status = HttpStatusCode.Forbidden,
                        headers = jsonHeaders(),
                    )
                else -> respond("", HttpStatusCode.Unauthorized)
            }
        }

        val client = ApiClient(fakeRepo, "https://test.example.com", engine, engine)

        val logoutCodes = mutableListOf<String>()
        val job = launch { client.logoutEvents.collect { logoutCodes.add(it) } }
        advanceUntilIdle()  // let the collector start and subscribe before the request fires

        try { client.authClient.get("https://test.example.com/api/v1/feed") }
        catch (_: Exception) { }

        advanceUntilIdle()  // let the collector coroutine process the emitted event
        job.cancel()

        assertEquals(listOf("TOKEN_REUSED"), logoutCodes)
        assertNull(fakeRepo.loadOnce())
    }

    @Test
    fun `login success parses TokenPairDto`() = runTest {
        val expected = TokenPairDto("acc-tok", "ref-tok", 900L)
        val fakeRepo = FakeTokenRepository()

        val engine = MockEngine { req ->
            when {
                req.url.encodedPath.contains("/auth/login") ->
                    respond(testJson.encodeToString(expected), HttpStatusCode.OK, jsonHeaders())
                else ->
                    respond("", HttpStatusCode.NotFound)
            }
        }

        val client = ApiClient(fakeRepo, "https://test.example.com", engine, engine)
        val api = AuthApi("https://test.example.com", client.anonClient, client.authClient)

        val result = api.login(LoginRequest("user", "pass"))
        assertEquals(expected, result)
    }

    @Test
    fun `error body becomes ApiException with code`() = runTest {
        val fakeRepo = FakeTokenRepository()

        val engine = MockEngine {
            respond(
                content = testJson.encodeToString(ErrorResponse("NICK_TAKEN", "Nick already taken")),
                status = HttpStatusCode.Conflict,
                headers = jsonHeaders(),
            )
        }

        val client = ApiClient(fakeRepo, "https://test.example.com", engine, engine)
        val api = AuthApi("https://test.example.com", client.anonClient, client.authClient)

        var caught: ApiException? = null
        try {
            api.register(RegisterRequest("taken", "password"))
        } catch (e: ApiException) {
            caught = e
        }

        assertNotNull(caught)
        assertEquals("NICK_TAKEN", caught!!.code)
        assertEquals(409, caught.httpStatus)
    }
}
