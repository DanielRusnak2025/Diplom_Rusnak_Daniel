package org.example.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.example.dto.ErrorResponse
import org.example.dto.TokenPairDto
import org.example.util.HibernateUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ServerTestDb::class)
class AuthRouteTest {

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
    fun `POST register creates user and returns 201 with token pair`() = testApplication {
        application { module() }
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_reg","password":"Password1!","languages":[],"regions":[]}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<TokenPairDto>(response.bodyAsText())
        assertTrue(body.accessToken.isNotBlank())
        assertTrue(body.refreshToken.isNotBlank())
        assertEquals(15 * 60L, body.accessExpiresIn)
    }

    @Test
    fun `POST register duplicate nick returns 409`() = testApplication {
        application { module() }
        client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_dup","password":"Password1!","languages":[],"regions":[]}""")
        }
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_dup","password":"Password2!","languages":[],"regions":[]}""")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals("NICK_TAKEN", body.code)
    }

    @Test
    fun `POST register short password returns 400`() = testApplication {
        application { module() }
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_shortpw","password":"short","languages":[],"regions":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST login returns 200 with token pair`() = testApplication {
        application { module() }
        client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_login","password":"Password1!","languages":[],"regions":[]}""")
        }
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"test_login","password":"Password1!"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<TokenPairDto>(response.bodyAsText())
        assertTrue(body.accessToken.isNotBlank())
        assertTrue(body.refreshToken.isNotBlank())
    }

    @Test
    fun `POST login wrong password returns 401`() = testApplication {
        application { module() }
        client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_wrongpw","password":"Password1!","languages":[],"regions":[]}""")
        }
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"test_wrongpw","password":"WrongPassword!"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals("INVALID_CREDENTIALS", body.code)
    }

    @Test
    fun `POST login unknown nick returns 401 via same timing path`() = testApplication {
        application { module() }
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"test_nobody","password":"Password1!"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals("INVALID_CREDENTIALS", body.code)
    }

    @Test
    fun `POST login growing delay returns 429 after repeated failures`() = testApplication {
        application { module() }
        client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_brute","password":"Password1!","languages":[],"regions":[]}""")
        }
        // First failed attempt — records failure
        client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"test_brute","password":"wrong"}""")
        }
        // Second attempt is within the 1-second window → 429
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"test_brute","password":"wrong"}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals("TOO_MANY_ATTEMPTS", body.code)
        assertNotNull(response.headers["Retry-After"])
    }

    @Test
    fun `issued JWT has correct sub (userId), role, jti and exp claims`() = testApplication {
        application { module() }
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"test_jwt","password":"Password1!","languages":[],"regions":[]}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<TokenPairDto>(response.bodyAsText())

        val testSecret = System.getProperty("newsdove.jwt.secret")!!
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer("newsdove").build()
        val decoded = verifier.verify(body.accessToken)
        // sub must be an integer user id, not the nick
        assertNotNull(decoded.subject)
        assertNotNull(decoded.subject.toIntOrNull(), "sub should be a valid integer userId, got: ${decoded.subject}")
        assertEquals("USER", decoded.getClaim("role").asString())
        assertNotNull(decoded.expiresAt)
        assertNotNull(decoded.id)
        assertTrue(decoded.id.isNotBlank(), "JWT jti should be non-blank")
    }

    @Test
    fun `malformed JSON on register returns 4xx not 500`() = testApplication {
        application { module() }
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("{not valid json}")
        }
        assertTrue(
            response.status.value in 400..499,
            "Expected 4xx but got ${response.status}"
        )
    }

    @Test
    fun `malformed JSON on login returns 4xx not 500`() = testApplication {
        application { module() }
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("{not valid json}")
        }
        assertTrue(
            response.status.value in 400..499,
            "Expected 4xx but got ${response.status}"
        )
    }
}
