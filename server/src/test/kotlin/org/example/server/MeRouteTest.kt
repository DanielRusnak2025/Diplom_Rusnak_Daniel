package org.example.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.example.dto.ChangePasswordRequest
import org.example.dto.TokenPairDto
import org.example.dto.UserProfileDto
import org.example.util.HibernateUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Date
import java.util.UUID

@ExtendWith(ServerTestDb::class)
class MeRouteTest {

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

    private suspend fun ApplicationTestBuilder.registerAndGetPair(nick: String): TokenPairDto {
        val resp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"$nick","password":"Password1!","languages":[],"regions":[]}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status, "Register failed for $nick")
        return json.decodeFromString(resp.bodyAsText())
    }

    @Test
    fun `GET me without token returns 401`() = testApplication {
        application { module() }
        val resp = client.get("/api/v1/me")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET me with forged token returns 401`() = testApplication {
        application { module() }
        val forged = JWT.create()
            .withIssuer("newsdove")
            .withSubject("1")
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("role", "USER")
            .withExpiresAt(Date(System.currentTimeMillis() + 15 * 60_000L))
            .sign(Algorithm.HMAC256("wrong-secret-of-at-least-32-bytes-pad!"))
        val resp = client.get("/api/v1/me") {
            header(HttpHeaders.Authorization, "Bearer $forged")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET me with expired access token returns 401`() = testApplication {
        application { module() }
        val secret = System.getProperty("newsdove.jwt.secret")!!
        val expired = JWT.create()
            .withIssuer("newsdove")
            .withSubject("99999")
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("role", "USER")
            .withExpiresAt(Date(System.currentTimeMillis() - 1_000L))
            .sign(Algorithm.HMAC256(secret))
        val resp = client.get("/api/v1/me") {
            header(HttpHeaders.Authorization, "Bearer $expired")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET me with deleted user returns 401`() = testApplication {
        application { module() }
        val pair = registerAndGetPair("test_me_deleted")
        val userId = JWT.decode(pair.accessToken).subject.toInt()

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                session.createMutationQuery(
                    "delete from AuditLog a where a.actor is not null and a.actor.id = :uid"
                ).setParameter("uid", userId).executeUpdate()
                session.createMutationQuery(
                    "delete from RefreshToken r where r.user.id = :uid"
                ).setParameter("uid", userId).executeUpdate()
                session.createMutationQuery(
                    "delete from User u where u.id = :uid"
                ).setParameter("uid", userId).executeUpdate()
                tx.commit()
            }
        }

        val resp = client.get("/api/v1/me") {
            header(HttpHeaders.Authorization, "Bearer ${pair.accessToken}")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET me returns user profile`() = testApplication {
        application { module() }
        val pair = registerAndGetPair("test_me_profile")
        val resp = client.get("/api/v1/me") {
            header(HttpHeaders.Authorization, "Bearer ${pair.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val profile = json.decodeFromString<UserProfileDto>(resp.bodyAsText())
        assertEquals("test_me_profile", profile.nick)
        assertEquals(org.example.dto.RoleDto.USER, profile.role)
    }

    @Test
    fun `PUT me settings updates languages`() = testApplication {
        application { module() }
        val pair = registerAndGetPair("test_me_settings")
        val resp = client.put("/api/v1/me/settings") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${pair.accessToken}")
            setBody("""{"languages":["en","de"]}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val profile = json.decodeFromString<UserProfileDto>(resp.bodyAsText())
        assertEquals(setOf("en", "de"), profile.languages.toSet())
    }

    @Test
    fun `PUT me password with weak new password returns 422`() = testApplication {
        application { module() }
        val pair = registerAndGetPair("test_me_weakpw")
        val resp = client.put("/api/v1/me/password") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${pair.accessToken}")
            setBody("""{"currentPassword":"Password1!","newPassword":"short"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, resp.status)
    }

    @Test
    fun `PUT me password with reused password returns 422`() = testApplication {
        application { module() }
        val pair = registerAndGetPair("test_me_reusedpw")
        val resp = client.put("/api/v1/me/password") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${pair.accessToken}")
            setBody("""{"currentPassword":"Password1!","newPassword":"Password1!"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, resp.status)
    }

    @Test
    fun `PUT me password success revokes all refresh tokens`() = testApplication {
        application { module() }
        val pair = registerAndGetPair("test_me_pwchange")

        val changeResp = client.put("/api/v1/me/password") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${pair.accessToken}")
            setBody("""{"currentPassword":"Password1!","newPassword":"NewPassword1!"}""")
        }
        assertEquals(HttpStatusCode.NoContent, changeResp.status)

        val refreshResp = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"${pair.refreshToken}"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, refreshResp.status)
    }
}
