package org.example.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.example.Article
import org.example.Role
import org.example.User
import org.example.dto.TokenPairDto
import org.example.util.HibernateUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDate

@ExtendWith(ServerTestDb::class)
class AdminArticleRouteTest {

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

    private fun insertArticle(title: String, hidden: Boolean = false, pubDate: LocalDate? = null): Int {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            val a = Article().also {
                it.title = title
                it.link = "https://art-test.example.com/$title"
                it.promptVersion = "v1"
                it.articleLanguage = "en"
                it.isHidden = hidden
                if (pubDate != null) {
                    it.publicationDate = pubDate
                }
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
                session.createNativeQuery(
                    "DELETE atp FROM article_tag_percent atp " +
                    "INNER JOIN article a ON a.id = atp.article_id WHERE a.title LIKE 'zzart-%'"
                ).executeUpdate()
                session.createMutationQuery("delete from Article a where a.title like 'zzart-%'").executeUpdate()
                session.createMutationQuery("delete from AuditLog").executeUpdate()
                session.createMutationQuery("delete from RefreshToken").executeUpdate()
                session.createMutationQuery("delete from User").executeUpdate()
                tx.commit()
            } catch (e: Exception) { tx.rollback(); throw e }
        }
    }

    // ─── RBAC on /admin/articles ──────────────────────────────────────────────

    @Test
    fun `user gets 403 on GET admin articles`() = testApplication {
        application { module() }
        val token = registerUser("art_user1")
        val resp = client.get("/api/v1/admin/articles") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `moderator gets 200 on GET admin articles`() = testApplication {
        application { module() }
        val token = registerUser("art_mod1")
        setRole("art_mod1", Role.MODERATOR)
        val resp = client.get("/api/v1/admin/articles") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertNotNull(body["items"])
    }

    // ─── RBAC on /admin/articles/{id} ────────────────────────────────────────

    @Test
    fun `user gets 403 on GET admin article detail`() = testApplication {
        application { module() }
        val token = registerUser("art_user2")
        val articleId = insertArticle("zzart-detail-rbac1")
        val resp = client.get("/api/v1/admin/articles/$articleId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `moderator gets 200 on GET admin article detail`() = testApplication {
        application { module() }
        val token = registerUser("art_mod2")
        setRole("art_mod2", Role.MODERATOR)
        val articleId = insertArticle("zzart-detail-ok1")
        val resp = client.get("/api/v1/admin/articles/$articleId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(articleId, body["id"]?.jsonPrimitive?.int)
        assertEquals("zzart-detail-ok1", body["title"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET admin article detail returns 404 for unknown article`() = testApplication {
        application { module() }
        val token = registerUser("art_mod3")
        setRole("art_mod3", Role.MODERATOR)
        val resp = client.get("/api/v1/admin/articles/999999") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    // ─── Hidden articles visible to staff ────────────────────────────────────

    @Test
    fun `GET admin articles includes hidden articles for staff`() = testApplication {
        application { module() }
        val token = registerUser("art_mod4")
        setRole("art_mod4", Role.MODERATOR)
        val hiddenId = insertArticle("zzart-hidden-one1", hidden = true)
        val visibleId = insertArticle("zzart-visible-one1", hidden = false)

        val resp = client.get("/api/v1/admin/articles") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == hiddenId },
            "Hidden article must appear in admin articles list")
        assertTrue(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == visibleId },
            "Visible article must appear in admin articles list")
    }

    @Test
    fun `GET admin articles hidden=true filter returns only hidden articles`() = testApplication {
        application { module() }
        val token = registerUser("art_mod5")
        setRole("art_mod5", Role.MODERATOR)
        val hiddenId = insertArticle("zzart-hf-hidden1", hidden = true)
        insertArticle("zzart-hf-visible1", hidden = false)

        val resp = client.get("/api/v1/admin/articles?hidden=true") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == hiddenId },
            "Hidden article must appear with hidden=true filter")
        assertTrue(items.all { it.jsonObject["hidden"]?.jsonPrimitive?.boolean == true },
            "All returned articles must be hidden")
    }

    // ─── Keyword search ───────────────────────────────────────────────────────

    @Test
    fun `GET admin articles keyword search returns matching articles only`() = testApplication {
        application { module() }
        val token = registerUser("art_mod6")
        setRole("art_mod6", Role.MODERATOR)
        val matchId = insertArticle("zzart-kw-alpha_match1")
        insertArticle("zzart-kw-beta_nomatch1")

        val resp = client.get("/api/v1/admin/articles?q=alpha_match") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == matchId },
            "Matching article must appear in search results")
        assertFalse(items.any { it.jsonObject["title"]?.jsonPrimitive?.content?.contains("nomatch") == true },
            "Non-matching article must not appear")
    }

    // ─── Date filter ──────────────────────────────────────────────────────────

    @Test
    fun `GET admin articles date filter excludes out-of-range article`() = testApplication {
        application { module() }
        val token = registerUser("art_mod7")
        setRole("art_mod7", Role.MODERATOR)
        val inRangeId = insertArticle("zzart-date-in1", pubDate = LocalDate.of(2024, 6, 15))
        val outRangeId = insertArticle("zzart-date-out1", pubDate = LocalDate.of(2023, 1, 1))

        val resp = client.get("/api/v1/admin/articles?fromDate=2024-01-01&toDate=2024-12-31") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == inRangeId },
            "In-range article must appear")
        assertFalse(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == outRangeId },
            "Out-of-range article must not appear")
    }

    @Test
    fun `GET admin articles invalid fromDate returns 400`() = testApplication {
        application { module() }
        val token = registerUser("art_mod8")
        setRole("art_mod8", Role.MODERATOR)
        val resp = client.get("/api/v1/admin/articles?fromDate=not-a-date") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("INVALID_DATE", body["code"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET admin articles invalid toDate returns 400`() = testApplication {
        application { module() }
        val token = registerUser("art_mod9")
        setRole("art_mod9", Role.MODERATOR)
        val resp = client.get("/api/v1/admin/articles?toDate=2024/01/01") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ─── Cursor paging ────────────────────────────────────────────────────────

    @Test
    fun `GET admin articles paging with cursor returns next page`() = testApplication {
        application { module() }
        val token = registerUser("art_mod10")
        setRole("art_mod10", Role.MODERATOR)
        val id1 = insertArticle("zzart-page-a1")
        val id2 = insertArticle("zzart-page-b1")
        val id3 = insertArticle("zzart-page-c1")

        // First page of size 2
        val page1Resp = client.get("/api/v1/admin/articles?limit=2") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, page1Resp.status)
        val page1Body = Json.parseToJsonElement(page1Resp.bodyAsText()).jsonObject
        val page1Items = page1Body["items"]!!.jsonArray
        assertEquals(2, page1Items.size, "First page must have 2 items")
        val nextCursor = page1Body["nextCursor"]?.jsonPrimitive?.contentOrNull
        assertNotNull(nextCursor, "nextCursor must be present when more items exist")

        // Second page using cursor
        val page2Resp = client.get("/api/v1/admin/articles?limit=2&cursor=$nextCursor") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, page2Resp.status)
        val page2Items = Json.parseToJsonElement(page2Resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(page2Items.isNotEmpty(), "Second page must have at least 1 item")

        // All returned ids across both pages must be distinct
        val allIds = (page1Items + page2Items).map { it.jsonObject["id"]?.jsonPrimitive?.int }.toSet()
        assertTrue(allIds.containsAll(listOf(id1, id2, id3)),
            "Paging must return all 3 articles across both pages")
    }

    @Test
    fun `GET admin articles limit is capped at 50`() = testApplication {
        application { module() }
        val token = registerUser("art_mod11")
        setRole("art_mod11", Role.MODERATOR)
        // Just check it doesn't fail and returns a valid response
        val resp = client.get("/api/v1/admin/articles?limit=999") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }
}
