package org.example.server

import com.auth0.jwt.JWT
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ServerTestDb::class)
class RateRouteTest {

    private val json = Json { ignoreUnknownKeys = true }
    private var catTagId = 0

    @BeforeEach
    fun setup() {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            try {
                val cat = Tag("RateTestCategory").also {
                    it.kind = TagKind.CATEGORY; it.tagKey = "cat_ratetest"
                }
                session.persist(cat)
                session.flush()
                catTagId = cat.id
                tx.commit()
            } catch (e: Exception) { tx.rollback(); throw e }
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
                    "INNER JOIN article a ON a.id = atp.article_id WHERE a.title LIKE 'ratetest_%'"
                ).executeUpdate()
                session.createMutationQuery("delete from Article a where a.title like 'ratetest_%'").executeUpdate()
                session.createMutationQuery("delete from AuditLog").executeUpdate()
                session.createMutationQuery("delete from RefreshToken").executeUpdate()
                session.createMutationQuery("delete from User").executeUpdate()
                session.createMutationQuery(
                    "delete from Tag t where t.tagKey like '%ratetest' and t.kind = :k"
                ).setParameter("k", TagKind.CATEGORY).executeUpdate()
                tx.commit()
            } catch (e: Exception) { tx.rollback(); throw e }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.registerAndLogin(nick: String): String {
        val resp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"$nick","password":"Password1!","languages":[],"regions":[]}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status, "Register failed for $nick: ${resp.bodyAsText()}")
        return json.decodeFromString<TokenPairDto>(resp.bodyAsText()).accessToken
    }

    private fun insertClassifiedArticle(session: org.hibernate.Session, title: String): Article {
        val a = Article().also {
            it.title = title
            it.link = "https://ratetest.example.com/$title"
            it.promptVersion = "v1"
            it.articleLanguage = "en"
        }
        session.persist(a)
        session.flush()
        val tag = session.find(Tag::class.java, catTagId)
        session.persist(ArticleTagPercent(a, tag, 80))
        session.flush()
        return a
    }

    private fun getUserTagPercent(userId: Int, tagId: Int): Int? =
        HibernateUtil.getSessionFactory().openSession().use { session ->
            session.find(UserTagPercent::class.java, UserTagId(userId, tagId))?.percent
        }

    private fun getReport(userId: Int, articleId: Int): Report? =
        HibernateUtil.getSessionFactory().openSession().use { session ->
            session.createSelectionQuery(
                "FROM Report r WHERE r.user.id = :uid AND r.article.id = :aid",
                Report::class.java
            ).setParameter("uid", userId).setParameter("aid", articleId)
             .uniqueResultOptional().orElse(null)
        }

    private suspend fun ApplicationTestBuilder.rate(token: String, articleId: Int, rating: Int): HttpResponse =
        client.post("/api/v1/articles/$articleId/rate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"rating":$rating}""")
        }

    private suspend fun ApplicationTestBuilder.deleteRate(token: String, articleId: Int): HttpResponse =
        client.delete("/api/v1/articles/$articleId/rate") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

    private suspend fun ApplicationTestBuilder.complain(token: String, articleId: Int, reasonCode: String): HttpResponse =
        client.post("/api/v1/articles/$articleId/complain") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"reasonCode":"$reasonCode"}""")
        }

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Test
    fun `like increases weights`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_like")
        val userId = JWT.decode(token).subject.toInt()

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                articleId = insertClassifiedArticle(session, "ratetest_like_article").id
                tx.commit()
            }
        }

        assertNull(getUserTagPercent(userId, catTagId), "No weight before like")

        val resp = rate(token, articleId, 1)
        assertEquals(HttpStatusCode.NoContent, resp.status)

        val weight = getUserTagPercent(userId, catTagId)
        assertNotNull(weight, "Weight should exist after like")
        assertTrue(weight!! > 0, "Weight should be positive after like")
    }

    @Test
    fun `like again is no-op`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_likeagain")
        val userId = JWT.decode(token).subject.toInt()

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                articleId = insertClassifiedArticle(session, "ratetest_likeagain_article").id
                tx.commit()
            }
        }

        rate(token, articleId, 1)
        val weightAfterFirst = getUserTagPercent(userId, catTagId)

        val resp = rate(token, articleId, 1)
        assertEquals(HttpStatusCode.NoContent, resp.status)
        assertEquals(weightAfterFirst, getUserTagPercent(userId, catTagId), "Second like must not change weights")
    }

    @Test
    fun `like then dislike applies 2 steps`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_likedislike")
        val userId = JWT.decode(token).subject.toInt()

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                articleId = insertClassifiedArticle(session, "ratetest_ld_article").id
                tx.commit()
            }
        }

        rate(token, articleId, 1)
        val weightAfterLike = getUserTagPercent(userId, catTagId)!!
        assertTrue(weightAfterLike > 0, "Weight positive after like")

        val resp = rate(token, articleId, -1)
        assertEquals(HttpStatusCode.NoContent, resp.status)

        val weightAfterDislike = getUserTagPercent(userId, catTagId)
        // like→dislike: 2 steps of -1 → weight goes from positive to negative
        assertNotNull(weightAfterDislike)
        assertTrue(weightAfterDislike!! < 0, "Weight should be negative after like→dislike (2 steps)")
    }

    @Test
    fun `complain after dislike does not change weights`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_complain")
        val userId = JWT.decode(token).subject.toInt()

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                articleId = insertClassifiedArticle(session, "ratetest_complain_article").id
                tx.commit()
            }
        }

        rate(token, articleId, -1)
        val weightAfterDislike = getUserTagPercent(userId, catTagId)

        val resp = complain(token, articleId, "MISLEADING")
        assertEquals(HttpStatusCode.NoContent, resp.status)

        assertEquals(weightAfterDislike, getUserTagPercent(userId, catTagId),
            "Weight must not change when complaining after existing dislike")
    }

    @Test
    fun `delete rating removes report`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_delete")
        val userId = JWT.decode(token).subject.toInt()

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                articleId = insertClassifiedArticle(session, "ratetest_delete_article").id
                tx.commit()
            }
        }

        rate(token, articleId, 1)
        assertNotNull(getReport(userId, articleId), "Report should exist after like")

        val resp = deleteRate(token, articleId)
        assertEquals(HttpStatusCode.NoContent, resp.status)
        assertNull(getReport(userId, articleId), "Report should be gone after DELETE /rate")
    }

    @Test
    fun `rate unknown article returns 404`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_404rate")

        assertEquals(HttpStatusCode.NotFound, rate(token, 9999999, 1).status)
    }

    @Test
    fun `rate unclassified article returns 404`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_404unclass")

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val a = Article().also {
                    it.title = "ratetest_unclassified_article"
                    it.link = "https://ratetest.example.com/unclassified"
                    it.promptVersion = null
                }
                session.persist(a)
                session.flush()
                articleId = a.id
                tx.commit()
            }
        }

        assertEquals(HttpStatusCode.NotFound, rate(token, articleId, 1).status)
    }

    @Test
    fun `invalid rating value returns 422`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_422rating")

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                articleId = insertClassifiedArticle(session, "ratetest_422_article").id
                tx.commit()
            }
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, rate(token, articleId, 2).status)
    }

    @Test
    fun `invalid reason code returns 422`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_422reason")

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                articleId = insertClassifiedArticle(session, "ratetest_422reason_article").id
                tx.commit()
            }
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, complain(token, articleId, "INVALID_XYZ").status)
    }

    @Test
    fun `delete complaint returns 409`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_delcomplain")

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                articleId = insertClassifiedArticle(session, "ratetest_delcomplain_article").id
                tx.commit()
            }
        }

        val complainResp = complain(token, articleId, "MISLEADING")
        assertEquals(HttpStatusCode.NoContent, complainResp.status, "complain should succeed")

        val deleteResp = deleteRate(token, articleId)
        assertEquals(HttpStatusCode.Conflict, deleteResp.status, "DELETE /rate on a complaint should return 409")

        val body = Json.parseToJsonElement(deleteResp.bodyAsText()).jsonObject
        assertEquals("COMPLAINT_CANNOT_BE_REMOVED", body["code"]?.jsonPrimitive?.content)
    }

    @Test
    fun `disliked article excluded from feed`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_feedexcl")

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                articleId = insertClassifiedArticle(session, "ratetest_feed_disliked").id
                tx.commit()
            }
        }

        val respBefore = client.get("/api/v1/feed") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val idsBefore = Json.parseToJsonElement(respBefore.bodyAsText()).jsonObject["items"]!!
            .jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int }
        assertTrue(articleId in idsBefore, "Article must appear in feed before dislike")

        rate(token, articleId, -1)

        val respAfter = client.get("/api/v1/feed") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val idsAfter = Json.parseToJsonElement(respAfter.bodyAsText()).jsonObject["items"]!!
            .jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int }
        assertFalse(articleId in idsAfter, "Article must not appear in feed after dislike")
    }

    private fun hideArticle(articleId: Int) {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            val a = session.find(Article::class.java, articleId)!!
            a.isHidden = true
            tx.commit()
        }
    }

    @Test
    fun `rate hidden article returns 404`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_hidden_rate")

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                articleId = insertClassifiedArticle(session, "ratetest_hidden_rate_art").id
                tx.commit()
            }
        }
        hideArticle(articleId)

        assertEquals(HttpStatusCode.NotFound, rate(token, articleId, 1).status)
    }

    @Test
    fun `delete rate on hidden article returns 404`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_hidden_del")

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                articleId = insertClassifiedArticle(session, "ratetest_hidden_del_art").id
                tx.commit()
            }
        }
        hideArticle(articleId)

        assertEquals(HttpStatusCode.NotFound, deleteRate(token, articleId).status)
    }

    @Test
    fun `complain on hidden article returns 404`() = testApplication {
        application { module() }
        val token = registerAndLogin("ratetest_hidden_cmp")

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                articleId = insertClassifiedArticle(session, "ratetest_hidden_cmp_art").id
                tx.commit()
            }
        }
        hideArticle(articleId)

        assertEquals(HttpStatusCode.NotFound, complain(token, articleId, "MISLEADING").status)
    }
}
