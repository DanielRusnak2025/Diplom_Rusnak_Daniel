package org.example.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.example.Role
import org.example.User
import org.example.dto.TokenPairDto
import org.example.util.HibernateUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ServerTestDb::class)
class AdminUserDetailRouteTest {

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun ApplicationTestBuilder.registerUser(nick: String): String {
        val resp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"$nick","password":"Password1!","languages":[],"regions":[]}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status, "register failed for $nick: ${resp.bodyAsText()}")
        return json.decodeFromString<TokenPairDto>(resp.bodyAsText()).accessToken
    }

    private fun setRole(nick: String, role: Role) {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            val user = session.createSelectionQuery("FROM User u WHERE u.nick = :nick", User::class.java)
                .setParameter("nick", nick).uniqueResult()
            user.role = role
            tx.commit()
        }
    }

    private fun getUserId(nick: String): Int =
        HibernateUtil.getSessionFactory().openSession().use { session ->
            session.createSelectionQuery("FROM User u WHERE u.nick = :nick", User::class.java)
                .setParameter("nick", nick).uniqueResult().id
        }

    @AfterEach
    fun cleanup() {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            try {
                session.createMutationQuery("delete from Report").executeUpdate()
                session.createMutationQuery("delete from UserBlock").executeUpdate()
                session.createMutationQuery("delete from UserTagPercent").executeUpdate()
                session.createMutationQuery("delete from AuditLog").executeUpdate()
                session.createMutationQuery("delete from RefreshToken").executeUpdate()
                session.createMutationQuery("delete from User").executeUpdate()
                tx.commit()
            } catch (e: Exception) { tx.rollback(); throw e }
        }
    }

    // ─── RBAC on /admin/users/{id} ────────────────────────────────────────────

    @Test
    fun `user gets 403 on GET admin user detail`() = testApplication {
        application { module() }
        val actorToken = registerUser("ud_user1")
        val targetId = getUserId("ud_user1")
        val resp = client.get("/api/v1/admin/users/$targetId") {
            header(HttpHeaders.Authorization, "Bearer $actorToken")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `moderator gets 403 on GET admin user detail`() = testApplication {
        application { module() }
        val actorToken = registerUser("ud_mod1")
        setRole("ud_mod1", Role.MODERATOR)
        registerUser("ud_target1")
        val targetId = getUserId("ud_target1")
        val resp = client.get("/api/v1/admin/users/$targetId") {
            header(HttpHeaders.Authorization, "Bearer $actorToken")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `admin gets 200 on GET admin user detail`() = testApplication {
        application { module() }
        val adminToken = registerUser("ud_admin1")
        setRole("ud_admin1", Role.ADMIN)
        registerUser("ud_target2")
        val targetId = getUserId("ud_target2")
        val resp = client.get("/api/v1/admin/users/$targetId") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(targetId, body["id"]?.jsonPrimitive?.int)
        assertEquals("ud_target2", body["nick"]?.jsonPrimitive?.content)
        assertEquals("USER", body["role"]?.jsonPrimitive?.content)
        assertNotNull(body["languages"])
        assertNotNull(body["regions"])
        assertNotNull(body["interests"])
        assertNotNull(body["blocks"])
        assertNotNull(body["ratings"])
    }

    @Test
    fun `GET admin user detail returns 404 for unknown user`() = testApplication {
        application { module() }
        val adminToken = registerUser("ud_admin2")
        setRole("ud_admin2", Role.ADMIN)
        val resp = client.get("/api/v1/admin/users/999999") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("NOT_FOUND", body["code"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET admin user detail includes email and ratings structure`() = testApplication {
        application { module() }
        val adminToken = registerUser("ud_admin3")
        setRole("ud_admin3", Role.ADMIN)
        registerUser("ud_target3")
        val targetId = getUserId("ud_target3")
        val resp = client.get("/api/v1/admin/users/$targetId") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        // email field present (may be null for test user registered without email field)
        assertTrue(body.containsKey("email"), "Response must include email field")
        // ratings object structure
        val ratings = body["ratings"]?.jsonObject
        assertNotNull(ratings, "ratings must be present")
        assertNotNull(ratings!!["likeCount"], "ratings.likeCount must be present")
        assertNotNull(ratings["dislikeCount"], "ratings.dislikeCount must be present")
        assertNotNull(ratings["complaintCount"], "ratings.complaintCount must be present")
        assertEquals(0, ratings["likeCount"]?.jsonPrimitive?.int, "new user has 0 likes")
        assertEquals(0, ratings["dislikeCount"]?.jsonPrimitive?.int, "new user has 0 dislikes")
        assertEquals(0, ratings["complaintCount"]?.jsonPrimitive?.int, "new user has 0 complaints")
    }
}
