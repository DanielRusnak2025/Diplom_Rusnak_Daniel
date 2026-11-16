package org.example.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.example.Article
import org.example.Report
import org.example.ReportReason
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
class AdminComplaintFilterTest {

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

    private fun insertArticle(title: String): Int {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            val a = Article().also {
                it.title = title
                it.link = "https://cpf-test.example.com/$title"
                it.promptVersion = "v1"
                it.articleLanguage = "en"
            }
            session.persist(a)
            tx.commit()
            return a.id
        }
    }

    private fun insertReport(articleId: Int, userId: Int, reason: ReportReason, date: LocalDate? = null): Int {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            val article = session.get(Article::class.java, articleId)
            val user = session.get(User::class.java, userId)
            val report = Report(article, user, -1, reason)
            if (date != null) report.date = java.sql.Date.valueOf(date)
            session.persist(report)
            tx.commit()
            return report.id
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
                    "INNER JOIN article a ON a.id = atp.article_id WHERE a.title LIKE 'zzcp-%'"
                ).executeUpdate()
                session.createMutationQuery("delete from Article a where a.title like 'zzcp-%'").executeUpdate()
                session.createMutationQuery("delete from AuditLog").executeUpdate()
                session.createMutationQuery("delete from RefreshToken").executeUpdate()
                session.createMutationQuery("delete from User").executeUpdate()
                tx.commit()
            } catch (e: Exception) { tx.rollback(); throw e }
        }
    }

    // ─── q filter ─────────────────────────────────────────────────────────────

    @Test
    fun `q filter returns only complaint whose article title matches`() = testApplication {
        application { module() }
        val modToken = registerUser("zzcp_mod1")
        setRole("zzcp_mod1", Role.MODERATOR)
        registerUser("zzcp_c1")
        val cId = getUserId("zzcp_c1")

        val matchId  = insertArticle("zzcp-qmatch-art1")
        val otherId  = insertArticle("zzcp-qother-art1")
        insertReport(matchId, cId, ReportReason.OTHER)
        // second report needs a different user (unique constraint per user+article)
        registerUser("zzcp_c1b")
        insertReport(otherId, getUserId("zzcp_c1b"), ReportReason.OTHER)

        val resp = client.get("/api/v1/admin/complaints?q=qmatch") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertEquals(1, items.size, "Only one complaint should match q=qmatch")
        assertEquals(matchId, items[0].jsonObject["articleId"]?.jsonPrimitive?.int)
    }

    @Test
    fun `q with literal percent matches only article containing percent in title`() = testApplication {
        application { module() }
        val modToken = registerUser("zzcp_mod2")
        setRole("zzcp_mod2", Role.MODERATOR)
        registerUser("zzcp_c2a")
        registerUser("zzcp_c2b")
        val c2aId = getUserId("zzcp_c2a")
        val c2bId = getUserId("zzcp_c2b")

        // Title contains both literal % and literal _ — proves both wildcards are escaped
        val pctId   = insertArticle("zzcp-100%_art1")
        val plainId = insertArticle("zzcp-plain-art1")
        insertReport(pctId,   c2aId, ReportReason.OTHER)
        insertReport(plainId, c2bId, ReportReason.OTHER)

        // %25 URL-decodes to %; server must treat it as a literal, not a wildcard
        val resp = client.get("/api/v1/admin/complaints?q=%25") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertEquals(1, items.size, "Only the article with literal % should match")
        assertEquals(pctId, items[0].jsonObject["articleId"]?.jsonPrimitive?.int)
    }

    // ─── fromDate / toDate ─────────────────────────────────────────────────────

    @Test
    fun `fromDate excludes complaint before range`() = testApplication {
        application { module() }
        val modToken = registerUser("zzcp_mod3")
        setRole("zzcp_mod3", Role.MODERATOR)
        registerUser("zzcp_c3a")
        registerUser("zzcp_c3b")
        val c3aId = getUserId("zzcp_c3a")
        val c3bId = getUserId("zzcp_c3b")

        val artIn  = insertArticle("zzcp-date-in3")
        val artOut = insertArticle("zzcp-date-out3")
        val inId   = insertReport(artIn,  c3aId, ReportReason.OTHER, LocalDate.of(2024, 6, 15))
        val outId  = insertReport(artOut, c3bId, ReportReason.OTHER, LocalDate.of(2024, 1, 1))

        val resp = client.get("/api/v1/admin/complaints?fromDate=2024-03-01") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any  { it.jsonObject["reportId"]?.jsonPrimitive?.int == inId  }, "In-range report must appear")
        assertFalse(items.any { it.jsonObject["reportId"]?.jsonPrimitive?.int == outId }, "Out-of-range report must not appear")
    }

    @Test
    fun `toDate includes complaint created on exact toDate`() = testApplication {
        application { module() }
        val modToken = registerUser("zzcp_mod4")
        setRole("zzcp_mod4", Role.MODERATOR)
        registerUser("zzcp_c4")
        val c4Id = getUserId("zzcp_c4")

        val artId    = insertArticle("zzcp-todate-art4")
        val reportId = insertReport(artId, c4Id, ReportReason.OTHER, LocalDate.of(2024, 6, 15))

        val resp = client.get("/api/v1/admin/complaints?toDate=2024-06-15") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["reportId"]?.jsonPrimitive?.int == reportId },
            "Report created on toDate must be included (inclusive upper bound)")
    }

    @Test
    fun `combined fromDate and toDate keeps only in-range complaints`() = testApplication {
        application { module() }
        val modToken = registerUser("zzcp_mod5")
        setRole("zzcp_mod5", Role.MODERATOR)
        registerUser("zzcp_c5a")
        registerUser("zzcp_c5b")
        registerUser("zzcp_c5c")
        val c5aId = getUserId("zzcp_c5a")
        val c5bId = getUserId("zzcp_c5b")
        val c5cId = getUserId("zzcp_c5c")

        val artA = insertArticle("zzcp-range-artA5")
        val artB = insertArticle("zzcp-range-artB5")
        val artC = insertArticle("zzcp-range-artC5")
        val rA = insertReport(artA, c5aId, ReportReason.OTHER, LocalDate.of(2024, 1,  1))
        val rB = insertReport(artB, c5bId, ReportReason.OTHER, LocalDate.of(2024, 6, 15))
        val rC = insertReport(artC, c5cId, ReportReason.OTHER, LocalDate.of(2024, 12, 31))

        val resp = client.get("/api/v1/admin/complaints?fromDate=2024-02-01&toDate=2024-07-01") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertFalse(items.any { it.jsonObject["reportId"]?.jsonPrimitive?.int == rA }, "Jan report must be excluded")
        assertTrue(items.any  { it.jsonObject["reportId"]?.jsonPrimitive?.int == rB }, "June report must be included")
        assertFalse(items.any { it.jsonObject["reportId"]?.jsonPrimitive?.int == rC }, "Dec report must be excluded")
    }
}
