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

@ExtendWith(ServerTestDb::class)
class AdminComplaintRouteTest {

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

    private fun insertArticle(title: String): Int =
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            val a = Article().also {
                it.title = title
                it.link = "https://crt-test.example.com/$title"
                it.promptVersion = "v1"
                it.articleLanguage = "en"
            }
            session.persist(a)
            tx.commit()
            a.id
        }

    private fun insertReport(articleId: Int, userNick: String, reason: ReportReason = ReportReason.OTHER): Int =
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            val article = session.find(Article::class.java, articleId)
            val user = session.createSelectionQuery("FROM User u WHERE u.nick = :nick", User::class.java)
                .setParameter("nick", userNick).uniqueResult()
            val report = Report(article, user, -1, reason)
            session.persist(report)
            tx.commit()
            report.id
        }

    @AfterEach
    fun cleanup() {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            try {
                session.createMutationQuery("delete from Report").executeUpdate()
                session.createMutationQuery("delete from Article a where a.title like 'zzcrt-%'").executeUpdate()
                session.createMutationQuery("delete from AuditLog").executeUpdate()
                session.createMutationQuery("delete from RefreshToken").executeUpdate()
                session.createMutationQuery("delete from User").executeUpdate()
                tx.commit()
            } catch (e: Exception) { tx.rollback(); throw e }
        }
    }

    @Test
    fun `DELETE complaint 204 for MODERATOR`() = testApplication {
        application { module() }
        val modToken = registerUser("zzmod_cr1")
        setRole("zzmod_cr1", Role.MODERATOR)
        registerUser("zzuser_cr1")
        val articleId = insertArticle("zzcrt-dismiss-ok")
        val reportId = insertReport(articleId, "zzuser_cr1")

        val resp = client.delete("/api/v1/admin/complaints/$reportId") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)

        HibernateUtil.getSessionFactory().openSession().use { session ->
            val report = session.find(Report::class.java, reportId)
            assertNull(report.reasonCode)
            assertEquals(-1, report.rating)
        }
    }

    @Test
    fun `DELETE complaint 403 for USER role`() = testApplication {
        application { module() }
        val userToken = registerUser("zzuser_cr2")

        val resp = client.delete("/api/v1/admin/complaints/999999") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `DELETE complaint 404 for unknown reportId`() = testApplication {
        application { module() }
        val modToken = registerUser("zzmod_cr3")
        setRole("zzmod_cr3", Role.MODERATOR)

        val resp = client.delete("/api/v1/admin/complaints/999999") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `DELETE complaint leaves other complaints for same article intact`() = testApplication {
        application { module() }
        val modToken = registerUser("zzmod_cr4")
        setRole("zzmod_cr4", Role.MODERATOR)
        registerUser("zzuser_cr4a")
        registerUser("zzuser_cr4b")

        val articleId = insertArticle("zzcrt-two-reports")
        val reportId1 = insertReport(articleId, "zzuser_cr4a", ReportReason.OTHER)
        val reportId2 = insertReport(articleId, "zzuser_cr4b", ReportReason.BROKEN_LINK)

        val resp = client.delete("/api/v1/admin/complaints/$reportId1") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)

        HibernateUtil.getSessionFactory().openSession().use { session ->
            val r1 = session.find(Report::class.java, reportId1)
            val r2 = session.find(Report::class.java, reportId2)
            assertNull(r1.reasonCode)
            assertNotNull(r2.reasonCode)
            assertEquals(ReportReason.BROKEN_LINK, r2.reasonCode)
        }
    }
}
