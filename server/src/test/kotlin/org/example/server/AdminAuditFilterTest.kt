package org.example.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.example.AuditLog
import org.example.Role
import org.example.User
import org.example.dto.TokenPairDto
import org.example.util.HibernateUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ServerTestDb::class)
class AdminAuditFilterTest {

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

    private fun insertAuditEntry(action: String, actorNick: String? = null) {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            val al = AuditLog()
            al.action = action
            if (actorNick != null) {
                al.actor = session.createSelectionQuery("FROM User u WHERE u.nick = :nick", User::class.java)
                    .setParameter("nick", actorNick).uniqueResult()
            }
            session.persist(al)
            tx.commit()
        }
    }

    @AfterEach
    fun cleanup() {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            try {
                session.createMutationQuery("delete from AuditLog").executeUpdate()
                session.createMutationQuery("delete from RefreshToken").executeUpdate()
                session.createMutationQuery("delete from User").executeUpdate()
                tx.commit()
            } catch (e: Exception) { tx.rollback(); throw e }
        }
    }

    @Test
    fun `MODERATOR is forbidden from audit endpoint`() = testApplication {
        application { module() }
        val token = registerUser("zzaf_mod")
        setRole("zzaf_mod", Role.MODERATOR)

        val resp = client.get("/api/v1/admin/audit") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `ADMIN receives 200 on audit endpoint`() = testApplication {
        application { module() }
        val token = registerUser("zzaf_admin")
        setRole("zzaf_admin", Role.ADMIN)

        val resp = client.get("/api/v1/admin/audit") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `actions filter returns only entries with matching action codes`() = testApplication {
        application { module() }
        val token = registerUser("zzaf_admin2")
        setRole("zzaf_admin2", Role.ADMIN)

        insertAuditEntry("ARTICLE_HIDDEN")
        insertAuditEntry("ARTICLE_HIDDEN")
        insertAuditEntry("USER_ROLE_CHANGED")
        insertAuditEntry("LOGIN")

        val resp = client.get("/api/v1/admin/audit?actions=ARTICLE_HIDDEN") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.isNotEmpty(), "Expected ARTICLE_HIDDEN entries")
        assertTrue(items.all { it.jsonObject["action"]?.jsonPrimitive?.content == "ARTICLE_HIDDEN" },
            "All returned entries must have action=ARTICLE_HIDDEN")
    }

    @Test
    fun `unknown action code in filter returns 200 with no matching entries`() = testApplication {
        application { module() }
        val token = registerUser("zzaf_admin3")
        setRole("zzaf_admin3", Role.ADMIN)

        insertAuditEntry("ARTICLE_HIDDEN")
        insertAuditEntry("LOGIN")

        val resp = client.get("/api/v1/admin/audit?actions=NONEXISTENT_ACTION") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.isEmpty(), "Unknown action code should match nothing")
    }

    @Test
    fun `multi-action filter returns entries matching any of the specified codes`() = testApplication {
        application { module() }
        val token = registerUser("zzaf_admin4")
        setRole("zzaf_admin4", Role.ADMIN)

        insertAuditEntry("ARTICLE_HIDDEN")
        insertAuditEntry("USER_ROLE_CHANGED")
        insertAuditEntry("LOGIN")
        insertAuditEntry("REGISTER")

        val resp = client.get("/api/v1/admin/audit?actions=ARTICLE_HIDDEN,USER_ROLE_CHANGED") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertEquals(2, items.size, "Expected exactly 2 items for ARTICLE_HIDDEN,USER_ROLE_CHANGED")
        val actions = items.map { it.jsonObject["action"]?.jsonPrimitive?.content }.toSet()
        assertTrue(actions.containsAll(setOf("ARTICLE_HIDDEN", "USER_ROLE_CHANGED")))
    }
}
