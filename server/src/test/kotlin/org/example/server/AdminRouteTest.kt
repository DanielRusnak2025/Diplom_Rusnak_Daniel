package org.example.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.example.*
import org.example.dto.TokenPairDto
import org.example.util.HibernateUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ServerTestDb::class)
class AdminRouteTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.registerUser(nick: String): String {
        val resp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"$nick","password":"Password1!","languages":[],"regions":[]}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status, "register failed for $nick: ${resp.bodyAsText()}")
        return json.decodeFromString<TokenPairDto>(resp.bodyAsText()).accessToken
    }

    private fun getUserId(nick: String): Int =
        HibernateUtil.getSessionFactory().openSession().use { session ->
            session.createSelectionQuery("FROM User u WHERE u.nick = :nick", User::class.java)
                .setParameter("nick", nick).uniqueResult().id
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

    private fun insertClassifiedArticle(title: String): Int {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            val a = Article().also {
                it.title = title
                it.link = "https://admin-test.example.com/$title"
                it.promptVersion = "v1"
                it.articleLanguage = "en"
            }
            session.persist(a)
            tx.commit()
            return a.id
        }
    }

    @AfterEach
    fun cleanup() {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            try {
                session.createMutationQuery("delete from Report").executeUpdate()
                session.createMutationQuery("delete from UserBlock").executeUpdate()
                session.createMutationQuery("delete from UserTagPercent").executeUpdate()
                session.createNativeQuery(
                    "DELETE atp FROM article_tag_percent atp " +
                    "INNER JOIN article a ON a.id = atp.article_id WHERE a.title LIKE 'adm_%'"
                ).executeUpdate()
                session.createMutationQuery("delete from Article a where a.title like 'adm_%'").executeUpdate()
                session.createMutationQuery("delete from AuditLog").executeUpdate()
                session.createMutationQuery("delete from RefreshToken").executeUpdate()
                session.createMutationQuery("delete from User").executeUpdate()
                tx.commit()
            } catch (e: Exception) { tx.rollback(); throw e }
        }
    }

    // ─── RBAC: USER gets 403 on all admin routes ──────────────────────────────

    @Test
    fun `user gets 403 on complaints`() = testApplication {
        application { module() }
        val token = registerUser("adm_user1")
        val resp = client.get("/api/v1/admin/complaints") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `user gets 403 on hide article`() = testApplication {
        application { module() }
        val token = registerUser("adm_user2")
        val articleId = insertClassifiedArticle("adm_article_hide1")
        val resp = client.put("/api/v1/admin/articles/$articleId/hidden") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"hidden":true}""")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `user gets 403 on admin-only routes`() = testApplication {
        application { module() }
        val token = registerUser("adm_user3")
        for (path in listOf("/api/v1/admin/users", "/api/v1/admin/audit", "/api/v1/admin/stats")) {
            val resp = client.get(path) { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(HttpStatusCode.Forbidden, resp.status, "Expected 403 on $path")
        }
    }

    // ─── RBAC: MODERATOR gets 403 on ADMIN-only routes ────────────────────────

    @Test
    fun `moderator gets 403 on admin-only routes`() = testApplication {
        application { module() }
        val token = registerUser("adm_mod1")
        setRole("adm_mod1", Role.MODERATOR)
        for (path in listOf("/api/v1/admin/users", "/api/v1/admin/audit", "/api/v1/admin/stats")) {
            val resp = client.get(path) { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(HttpStatusCode.Forbidden, resp.status, "MODERATOR should get 403 on $path")
        }
    }

    @Test
    fun `moderator can access complaints`() = testApplication {
        application { module() }
        val token = registerUser("adm_mod2")
        setRole("adm_mod2", Role.MODERATOR)
        val resp = client.get("/api/v1/admin/complaints") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ─── Demotion: demoted user loses access with old token ───────────────────

    @Test
    fun `demoted admin loses access immediately`() = testApplication {
        application { module() }
        val token = registerUser("adm_demoted1")
        setRole("adm_demoted1", Role.ADMIN)

        // Confirm access works while ADMIN
        val before = client.get("/api/v1/admin/stats") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, before.status, "Should be OK while ADMIN")

        // Demote to USER (simulates another admin changing role in DB)
        setRole("adm_demoted1", Role.USER)

        // Same JWT token now returns 403 because requireRoleFromDb checks DB
        val after = client.get("/api/v1/admin/stats") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Forbidden, after.status, "Should be 403 after demotion")
    }

    // ─── Hide article → gone from feed and /articles/{id} ────────────────────

    @Test
    fun `hidden article disappears from feed`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_hider1")
        setRole("adm_hider1", Role.ADMIN)
        val userToken = registerUser("adm_viewer1")
        val articleId = insertClassifiedArticle("adm_hidden_article1")

        // Article is in feed before hiding
        val feedBefore = client.get("/api/v1/feed") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.OK, feedBefore.status)
        val itemsBefore = Json.parseToJsonElement(feedBefore.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(itemsBefore.any { it.jsonObject["id"]?.jsonPrimitive?.int == articleId },
            "Article should be in feed before hiding")

        // Admin hides the article
        val hideResp = client.put("/api/v1/admin/articles/$articleId/hidden") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            setBody("""{"hidden":true}""")
        }
        assertEquals(HttpStatusCode.NoContent, hideResp.status)

        // Article is gone from feed
        val feedAfter = client.get("/api/v1/feed") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.OK, feedAfter.status)
        val itemsAfter = Json.parseToJsonElement(feedAfter.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertFalse(itemsAfter.any { it.jsonObject["id"]?.jsonPrimitive?.int == articleId },
            "Article should be gone from feed after hiding")

        // /articles/{id} also returns 404
        val articleResp = client.get("/api/v1/articles/$articleId") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.NotFound, articleResp.status, "Hidden article should return 404")
    }

    // ─── Self role-change → 409 ───────────────────────────────────────────────

    @Test
    fun `admin cannot change own role`() = testApplication {
        application { module() }
        val token = registerUser("adm_self1")
        setRole("adm_self1", Role.ADMIN)
        val ownId = getUserId("adm_self1")

        val resp = client.put("/api/v1/admin/users/$ownId/role") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"role":"USER"}""")
        }
        assertEquals(HttpStatusCode.Conflict, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("SELF_ROLE_CHANGE", body["code"]?.jsonPrimitive?.content)
    }

    // ─── Admin can change another user's role ─────────────────────────────────

    @Test
    fun `admin can change another user role`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_changer1")
        setRole("adm_changer1", Role.ADMIN)
        registerUser("adm_target1")
        val targetId = getUserId("adm_target1")

        val resp = client.put("/api/v1/admin/users/$targetId/role") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            setBody("""{"role":"MODERATOR"}""")
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)

        val updatedRole = HibernateUtil.getSessionFactory().openSession().use { session ->
            session.find(User::class.java, targetId).role
        }
        assertEquals(Role.MODERATOR, updatedRole)
    }

    // ─── Stats returns counts ─────────────────────────────────────────────────

    @Test
    fun `admin stats returns expected shape`() = testApplication {
        application { module() }
        val token = registerUser("adm_stats1")
        setRole("adm_stats1", Role.ADMIN)

        val resp = client.get("/api/v1/admin/stats") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertTrue(body.containsKey("totalArticles"))
        assertTrue(body.containsKey("classifiedArticles"))
        assertTrue(body.containsKey("pendingQueue"))
        assertTrue(body.containsKey("totalUsers"))
        assertTrue(body.containsKey("openComplaints"))
        val total = body["totalArticles"]!!.jsonPrimitive.long
        val classified = body["classifiedArticles"]!!.jsonPrimitive.long
        val pending = body["pendingQueue"]!!.jsonPrimitive.long
        assertEquals(total - classified, pending)
    }
}
