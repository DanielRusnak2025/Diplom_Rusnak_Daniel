package org.example.server

import com.auth0.jwt.JWT
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.example.RefreshToken
import org.example.User
import org.example.dto.ErrorResponse
import org.example.dto.TokenPairDto
import org.example.server.auth.RefreshTokenService
import org.example.util.HibernateUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(ServerTestDb::class)
class RefreshLogoutTest {

    private val json = Json { ignoreUnknownKeys = true }

    @AfterEach
    fun cleanup() {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            try {
                session.createMutationQuery("delete from AuditLog").executeUpdate()
                session.createMutationQuery("delete from RefreshToken").executeUpdate()
                session.createMutationQuery("delete from User").executeUpdate()
                tx.commit()
            } catch (e: Exception) {
                tx.rollback()
                throw e
            }
        }
    }

    @Test
    fun `refresh happy path rotates token and returns new pair`() = testApplication {
        application { module() }

        val regResp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_refresh_ok","password":"Password1!","languages":[],"regions":[]}""")
        }
        assertEquals(HttpStatusCode.Created, regResp.status)
        val firstPair = json.decodeFromString<TokenPairDto>(regResp.bodyAsText())

        val refResp = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"${firstPair.refreshToken}"}""")
        }
        assertEquals(HttpStatusCode.OK, refResp.status)
        val secondPair = json.decodeFromString<TokenPairDto>(refResp.bodyAsText())
        assertTrue(secondPair.accessToken.isNotBlank())
        assertTrue(secondPair.refreshToken.isNotBlank())
        // New tokens must differ from the original
        assertNotEquals(firstPair.refreshToken, secondPair.refreshToken)
    }

    @Test
    fun `refresh reuse detection revokes entire family and returns 403`() = testApplication {
        application { module() }

        val regResp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_reuse","password":"Password1!","languages":[],"regions":[]}""")
        }
        val rt1 = json.decodeFromString<TokenPairDto>(regResp.bodyAsText()).refreshToken

        // First use: rotates RT1 → RT2
        val rotateResp = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$rt1"}""")
        }
        assertEquals(HttpStatusCode.OK, rotateResp.status)
        val rt2 = json.decodeFromString<TokenPairDto>(rotateResp.bodyAsText()).refreshToken

        // Reuse RT1 (already revoked) → 403, whole family revoked
        val reuseResp = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$rt1"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, reuseResp.status)
        val body = json.decodeFromString<ErrorResponse>(reuseResp.bodyAsText())
        assertEquals("TOKEN_REUSED", body.code)

        // RT2 should also be revoked now (same family)
        val rt2Resp = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$rt2"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, rt2Resp.status)
    }

    @Test
    fun `logout revokes token, subsequent refresh returns 401`() = testApplication {
        application { module() }

        val regResp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_logout","password":"Password1!","languages":[],"regions":[]}""")
        }
        val refreshToken = json.decodeFromString<TokenPairDto>(regResp.bodyAsText()).refreshToken

        val logoutResp = client.post("/api/v1/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        assertEquals(HttpStatusCode.NoContent, logoutResp.status)

        // Trying to refresh the revoked token → 401
        val refreshAfterLogout = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, refreshAfterLogout.status)
    }

    @Test
    fun `logout is idempotent — second logout of same token returns 204`() = testApplication {
        application { module() }

        val regResp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_logout2","password":"Password1!","languages":[],"regions":[]}""")
        }
        val refreshToken = json.decodeFromString<TokenPairDto>(regResp.bodyAsText()).refreshToken

        client.post("/api/v1/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        // Second logout should still return 204 (idempotent)
        val resp = client.post("/api/v1/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)
    }

    @Test
    fun `refresh with unknown token returns 401`() = testApplication {
        application { module() }
        val resp = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"nonexistent-token-that-does-not-exist"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        assertEquals("INVALID_TOKEN", json.decodeFromString<ErrorResponse>(resp.bodyAsText()).code)
    }

    @Test
    fun `refresh with expired token returns 401`() = testApplication {
        application { module() }

        // Register to create a user
        val regResp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_expired","password":"Password1!","languages":[],"regions":[]}""")
        }
        val userId = JWT.decode(
            json.decodeFromString<TokenPairDto>(regResp.bodyAsText()).accessToken
        ).subject.toInt()

        // Insert an already-expired refresh token directly
        val rawToken = "expired-test-token-${UUID.randomUUID()}"
        val hash = RefreshTokenService.sha256hex(rawToken)
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val user = session.find(User::class.java, userId)
                val token = RefreshToken().apply {
                    this.user = user
                    tokenHash = hash
                    familyId = UUID.randomUUID().toString()
                    expiresAt = LocalDateTime.now().minusDays(1)
                }
                session.persist(token)
                tx.commit()
            }
        }

        val resp = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$rawToken"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        assertEquals("TOKEN_EXPIRED", json.decodeFromString<ErrorResponse>(resp.bodyAsText()).code)
    }

    @Test
    fun `concurrent refresh with same token — exactly one succeeds`() = testApplication {
        application { module() }

        val regResp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_concurrent","password":"Password1!","languages":[],"regions":[]}""")
        }
        assertEquals(HttpStatusCode.Created, regResp.status)
        val firstPair = json.decodeFromString<TokenPairDto>(regResp.bodyAsText())

        val statuses = coroutineScope {
            val r1 = async {
                client.post("/api/v1/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"refreshToken":"${firstPair.refreshToken}"}""")
                }.status
            }
            val r2 = async {
                client.post("/api/v1/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"refreshToken":"${firstPair.refreshToken}"}""")
                }.status
            }
            listOf(r1.await(), r2.await())
        }

        assertEquals(1, statuses.count { it == HttpStatusCode.OK },
            "Expected exactly one 200 OK, got: $statuses")
    }
}
