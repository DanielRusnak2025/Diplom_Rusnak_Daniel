package org.example.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.example.*
import org.example.Report
import org.example.ReportReason
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

    private suspend fun ApplicationTestBuilder.registerUser(nick: String): String =
        registerUser(nick, emptyList(), emptyList())

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

    private fun insertComplaintFor(articleId: Int, nick: String) {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            val article = session.find(Article::class.java, articleId)
            val user = session.createSelectionQuery("FROM User u WHERE u.nick = :nick", User::class.java)
                .setParameter("nick", nick).uniqueResult()
            session.persist(Report(article, user, -1, ReportReason.OTHER))
            tx.commit()
        }
    }

    private suspend fun ApplicationTestBuilder.registerUser(
        nick: String,
        languages: List<String> = emptyList(),
        regions: List<String> = emptyList(),
    ): String {
        val langs = languages.joinToString(",") { "\"$it\"" }
        val regs = regions.joinToString(",") { "\"$it\"" }
        val resp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"$nick","password":"Password1!","languages":[$langs],"regions":[$regs]}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status, "register failed for $nick: ${resp.bodyAsText()}")
        return json.decodeFromString<TokenPairDto>(resp.bodyAsText()).accessToken
    }

    private fun insertArticleWithDetails(
        title: String,
        language: String = "en",
        region: String? = null,
        publisher: String? = null,
        classified: Boolean = true,
    ): Int {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            val a = Article().also {
                it.title = title
                it.link = "https://admin-filter-test.example.com/$title"
                if (classified) it.promptVersion = "v1"
                it.articleLanguage = language
                it.articleRegion = region
                it.publisher = publisher
            }
            session.persist(a)
            tx.commit()
            return a.id
        }
    }

    private fun insertTagAndLinkToArticle(articleId: Int, tagKey: String) {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            val existing = session.createSelectionQuery("FROM Tag t WHERE t.tagKey = :key", Tag::class.java)
                .setParameter("key", tagKey).uniqueResult()
            val tag = existing ?: Tag(tagKey).also { t ->
                t.tagKey = tagKey
                session.persist(t)
                session.flush()
            }
            val article = session.find(Article::class.java, articleId)
            session.persist(ArticleTagPercent(article, tag, 50))
            tx.commit()
        }
    }

    private fun insertComplaintWithReason(articleId: Int, nick: String, reason: ReportReason) {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            val article = session.find(Article::class.java, articleId)
            val user = session.createSelectionQuery("FROM User u WHERE u.nick = :nick", User::class.java)
                .setParameter("nick", nick).uniqueResult()
            session.persist(Report(article, user, -1, reason))
            tx.commit()
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
                session.createMutationQuery("delete from Tag t where t.tagKey like 'adm_%'").executeUpdate()
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

    // ─── Dismiss complaints ───────────────────────────────────────────────────

    @Test
    fun `moderator can dismiss complaints - returns 204`() = testApplication {
        application { module() }
        val modToken = registerUser("adm_dis1")
        setRole("adm_dis1", Role.MODERATOR)
        val userToken = registerUser("adm_dis1_reporter")
        val articleId = insertClassifiedArticle("adm_dismiss_article1")
        insertComplaintFor(articleId, "adm_dis1_reporter")

        val resp = client.delete("/api/v1/admin/articles/$articleId/complaints") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)
    }

    @Test
    fun `user gets 403 on dismiss complaints`() = testApplication {
        application { module() }
        val token = registerUser("adm_dis2")
        val articleId = insertClassifiedArticle("adm_dismiss_article2")

        val resp = client.delete("/api/v1/admin/articles/$articleId/complaints") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `dismiss complaints for unknown article returns 404`() = testApplication {
        application { module() }
        val token = registerUser("adm_dis3")
        setRole("adm_dis3", Role.MODERATOR)

        val resp = client.delete("/api/v1/admin/articles/999999/complaints") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `dismissed article stays visible in feed`() = testApplication {
        application { module() }
        val modToken = registerUser("adm_dis4_mod")
        setRole("adm_dis4_mod", Role.MODERATOR)
        val userToken = registerUser("adm_dis4_user")
        val articleId = insertClassifiedArticle("adm_dismiss_article4")
        // Note: adm_dis4_user has NO report for this article, so it should appear normally
        insertComplaintFor(articleId, "adm_dis4_mod")

        val dismissResp = client.delete("/api/v1/admin/articles/$articleId/complaints") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.NoContent, dismissResp.status)

        val feedResp = client.get("/api/v1/feed") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.OK, feedResp.status)
        val items = Json.parseToJsonElement(feedResp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(
            items.any { it.jsonObject["id"]?.jsonPrimitive?.int == articleId },
            "Article should still be visible in feed to a user who did not file a complaint"
        )
    }

    @Test
    fun `dismiss keeps report row with rating -1 and clears reason_code`() = testApplication {
        application { module() }
        val modToken = registerUser("adm_dis5_mod")
        setRole("adm_dis5_mod", Role.MODERATOR)
        registerUser("adm_dis5_reporter")
        val articleId = insertClassifiedArticle("adm_dismiss_article5")
        insertComplaintFor(articleId, "adm_dis5_reporter")

        val dismissResp = client.delete("/api/v1/admin/articles/$articleId/complaints") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.NoContent, dismissResp.status)

        // Row must still exist with rating=-1 but reasonCode=null
        val report = HibernateUtil.getSessionFactory().openSession().use { session ->
            session.createSelectionQuery(
                "FROM Report r WHERE r.article.id = :articleId",
                Report::class.java
            ).setParameter("articleId", articleId).uniqueResult()
        }
        assertNotNull(report, "Report row must still exist after dismiss")
        assertEquals(-1, report!!.rating, "rating must remain -1")
        assertNull(report.reasonCode, "reasonCode must be cleared to null")
    }

    @Test
    fun `dismissed complaint no longer appears in GET admin complaints`() = testApplication {
        application { module() }
        val modToken = registerUser("adm_dis6_mod")
        setRole("adm_dis6_mod", Role.MODERATOR)
        registerUser("adm_dis6_reporter")
        val articleId = insertClassifiedArticle("adm_dismiss_article6")
        insertComplaintFor(articleId, "adm_dis6_reporter")

        val before = client.get("/api/v1/admin/complaints") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        val itemsBefore = Json.parseToJsonElement(before.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(
            itemsBefore.any { it.jsonObject["articleId"]?.jsonPrimitive?.int == articleId },
            "Complaint should appear in GET /admin/complaints before dismiss"
        )

        client.delete("/api/v1/admin/articles/$articleId/complaints") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }

        val after = client.get("/api/v1/admin/complaints") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        val itemsAfter = Json.parseToJsonElement(after.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertFalse(
            itemsAfter.any { it.jsonObject["articleId"]?.jsonPrimitive?.int == articleId },
            "Complaint should be gone from GET /admin/complaints after dismiss"
        )
    }

    @Test
    fun `dismissed article stays hidden from the reporter's feed`() = testApplication {
        application { module() }
        val modToken = registerUser("adm_dis7_mod")
        setRole("adm_dis7_mod", Role.MODERATOR)
        val reporterToken = registerUser("adm_dis7_reporter")
        val articleId = insertClassifiedArticle("adm_dismiss_article7")
        // Reporter files a complaint (rating=-1, reasonCode set)
        insertComplaintFor(articleId, "adm_dis7_reporter")

        // Moderator dismisses the complaint (reasonCode cleared, but rating=-1 stays)
        val dismissResp = client.delete("/api/v1/admin/articles/$articleId/complaints") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.NoContent, dismissResp.status)

        // The article must still be absent from the reporter's feed because rating=-1 remains
        val feedResp = client.get("/api/v1/feed") {
            header(HttpHeaders.Authorization, "Bearer $reporterToken")
        }
        assertEquals(HttpStatusCode.OK, feedResp.status)
        val items = Json.parseToJsonElement(feedResp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertFalse(
            items.any { it.jsonObject["id"]?.jsonPrimitive?.int == articleId },
            "Article must stay out of the reporter's feed: rating=-1 row was kept, only reasonCode was cleared"
        )
    }

    // ─── Articles search filters ──────────────────────────────────────────────

    @Test
    fun `articles filtered by language`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_lang1")
        setRole("adm_filt_lang1", Role.ADMIN)
        val enId = insertArticleWithDetails("adm_filt_lang_en", language = "en")
        val frId = insertArticleWithDetails("adm_filt_lang_fr", language = "fr")

        val resp = client.get("/api/v1/admin/articles?language=fr") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == frId })
        assertFalse(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == enId })
    }

    @Test
    fun `articles filtered by region`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_reg1")
        setRole("adm_filt_reg1", Role.ADMIN)
        val naId = insertArticleWithDetails("adm_filt_reg_na", region = "north_america")
        val eeId = insertArticleWithDetails("adm_filt_reg_ee", region = "eastern_europe")

        val resp = client.get("/api/v1/admin/articles?region=north_america") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == naId })
        assertFalse(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == eeId })
    }

    @Test
    fun `articles filtered by publisher contains`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_pub1")
        setRole("adm_filt_pub1", Role.ADMIN)
        val nytId = insertArticleWithDetails("adm_filt_pub_nyt", publisher = "New York Times")
        val bbcId = insertArticleWithDetails("adm_filt_pub_bbc", publisher = "BBC News")

        val resp = client.get("/api/v1/admin/articles?publisher=york") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == nytId })
        assertFalse(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == bbcId })
    }

    @Test
    fun `articles filtered by category tag key`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_cat1")
        setRole("adm_filt_cat1", Role.ADMIN)
        val withTagId = insertArticleWithDetails("adm_filt_cat_with")
        val noTagId = insertArticleWithDetails("adm_filt_cat_none")
        insertTagAndLinkToArticle(withTagId, "adm_tech")

        val resp = client.get("/api/v1/admin/articles?category=adm_tech") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == withTagId })
        assertFalse(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == noTagId })
    }

    @Test
    fun `articles classified=true returns only classified`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_cls1")
        setRole("adm_filt_cls1", Role.ADMIN)
        val classifiedId = insertArticleWithDetails("adm_filt_cls_yes", classified = true)
        val unclassifiedId = insertArticleWithDetails("adm_filt_cls_no", classified = false)

        val resp = client.get("/api/v1/admin/articles?classified=true") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == classifiedId })
        assertFalse(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == unclassifiedId })
    }

    @Test
    fun `articles classified=false returns only unclassified`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_cls2")
        setRole("adm_filt_cls2", Role.ADMIN)
        val classifiedId = insertArticleWithDetails("adm_filt_unclsyes", classified = true)
        val unclassifiedId = insertArticleWithDetails("adm_filt_unclsno", classified = false)

        val resp = client.get("/api/v1/admin/articles?classified=false") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == unclassifiedId })
        assertFalse(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == classifiedId })
    }

    @Test
    fun `articles hasComplaints=true returns only articles with open complaints`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_hc1")
        setRole("adm_filt_hc1", Role.ADMIN)
        registerUser("adm_filt_hc1_reporter")
        val withComplaintId = insertArticleWithDetails("adm_filt_hcyes")
        val noComplaintId = insertArticleWithDetails("adm_filt_hcno")
        insertComplaintFor(withComplaintId, "adm_filt_hc1_reporter")

        val resp = client.get("/api/v1/admin/articles?hasComplaints=true") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == withComplaintId })
        assertFalse(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == noComplaintId })
    }

    @Test
    fun `articles invalid classified param returns 400`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_bad1")
        setRole("adm_filt_bad1", Role.ADMIN)

        val resp = client.get("/api/v1/admin/articles?classified=maybe") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `articles invalid hasComplaints param returns 400`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_bad2")
        setRole("adm_filt_bad2", Role.ADMIN)

        val resp = client.get("/api/v1/admin/articles?hasComplaints=yes") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `articles filters combine with q and paging`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_comb1")
        setRole("adm_filt_comb1", Role.ADMIN)
        val matchId = insertArticleWithDetails("adm_filt_comb_match", language = "fr", publisher = "Le Monde")
        insertArticleWithDetails("adm_filt_comb_nomatch_lang", language = "en", publisher = "Le Monde")
        insertArticleWithDetails("adm_filt_comb_nomatch_pub", language = "fr", publisher = "Other")

        val resp = client.get("/api/v1/admin/articles?language=fr&publisher=monde") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["id"]?.jsonPrimitive?.int == matchId })
        assertEquals(1, items.count { it.jsonObject["id"]?.jsonPrimitive?.int == matchId })
    }

    // ─── Users search filters ─────────────────────────────────────────────────

    @Test
    fun `users filtered by role`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_role_adm")
        setRole("adm_filt_role_adm", Role.ADMIN)
        registerUser("adm_filt_role_mod")
        setRole("adm_filt_role_mod", Role.MODERATOR)
        registerUser("adm_filt_role_usr")

        val resp = client.get("/api/v1/admin/users?role=MODERATOR") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["nick"]?.jsonPrimitive?.content == "adm_filt_role_mod" })
        assertFalse(items.any { it.jsonObject["nick"]?.jsonPrimitive?.content == "adm_filt_role_usr" })
        assertFalse(items.any { it.jsonObject["nick"]?.jsonPrimitive?.content == "adm_filt_role_adm" })
    }

    @Test
    fun `users filtered by language`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_ulang_adm")
        setRole("adm_filt_ulang_adm", Role.ADMIN)
        registerUser("adm_filt_ulang_en", languages = listOf("en"))
        registerUser("adm_filt_ulang_fr", languages = listOf("fr"))

        val resp = client.get("/api/v1/admin/users?language=en") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["nick"]?.jsonPrimitive?.content == "adm_filt_ulang_en" })
        assertFalse(items.any { it.jsonObject["nick"]?.jsonPrimitive?.content == "adm_filt_ulang_fr" })
    }

    @Test
    fun `users filtered by region`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_ureg_adm")
        setRole("adm_filt_ureg_adm", Role.ADMIN)
        registerUser("adm_filt_ureg_na", regions = listOf("north_america"))
        registerUser("adm_filt_ureg_ee", regions = listOf("eastern_europe"))

        val resp = client.get("/api/v1/admin/users?region=north_america") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["nick"]?.jsonPrimitive?.content == "adm_filt_ureg_na" })
        assertFalse(items.any { it.jsonObject["nick"]?.jsonPrimitive?.content == "adm_filt_ureg_ee" })
    }

    @Test
    fun `users hasComplaints=true returns users who filed open complaints`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_uhc_adm")
        setRole("adm_filt_uhc_adm", Role.ADMIN)
        registerUser("adm_filt_uhc_reporter")
        registerUser("adm_filt_uhc_silent")
        val articleId = insertArticleWithDetails("adm_filt_uhc_article")
        insertComplaintFor(articleId, "adm_filt_uhc_reporter")

        val resp = client.get("/api/v1/admin/users?hasComplaints=true") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["nick"]?.jsonPrimitive?.content == "adm_filt_uhc_reporter" })
        assertFalse(items.any { it.jsonObject["nick"]?.jsonPrimitive?.content == "adm_filt_uhc_silent" })
    }

    @Test
    fun `users invalid role param returns 400`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_urole_bad")
        setRole("adm_filt_urole_bad", Role.ADMIN)

        val resp = client.get("/api/v1/admin/users?role=SUPERUSER") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `users invalid hasComplaints param returns 400`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_uhc_bad")
        setRole("adm_filt_uhc_bad", Role.ADMIN)

        val resp = client.get("/api/v1/admin/users?hasComplaints=1") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `users role filter combined with q and paging`() = testApplication {
        application { module() }
        val adminToken = registerUser("adm_filt_ucomb_adm")
        setRole("adm_filt_ucomb_adm", Role.ADMIN)
        registerUser("adm_filt_ucomb_mod1")
        setRole("adm_filt_ucomb_mod1", Role.MODERATOR)
        registerUser("adm_filt_ucomb_mod2")
        setRole("adm_filt_ucomb_mod2", Role.MODERATOR)
        registerUser("adm_filt_ucomb_usr1")

        val resp = client.get("/api/v1/admin/users?role=MODERATOR&q=mod1") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["nick"]?.jsonPrimitive?.content == "adm_filt_ucomb_mod1" })
        assertFalse(items.any { it.jsonObject["nick"]?.jsonPrimitive?.content == "adm_filt_ucomb_mod2" })
        assertFalse(items.any { it.jsonObject["nick"]?.jsonPrimitive?.content == "adm_filt_ucomb_usr1" })
    }

    // ─── Complaints search filters ────────────────────────────────────────────

    @Test
    fun `complaints filtered by reason`() = testApplication {
        application { module() }
        val modToken = registerUser("adm_filt_creason_mod")
        setRole("adm_filt_creason_mod", Role.MODERATOR)
        registerUser("adm_filt_creason_rep")
        val a1 = insertArticleWithDetails("adm_filt_creason_a1")
        val a2 = insertArticleWithDetails("adm_filt_creason_a2")
        insertComplaintWithReason(a1, "adm_filt_creason_rep", ReportReason.BROKEN_LINK)
        insertComplaintWithReason(a2, "adm_filt_creason_rep", ReportReason.MISLEADING)

        val resp = client.get("/api/v1/admin/complaints?reason=BROKEN_LINK") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["articleId"]?.jsonPrimitive?.int == a1 })
        assertFalse(items.any { it.jsonObject["articleId"]?.jsonPrimitive?.int == a2 })
    }

    @Test
    fun `complaints filtered by reporter nick contains`() = testApplication {
        application { module() }
        val modToken = registerUser("adm_filt_crep_mod")
        setRole("adm_filt_crep_mod", Role.MODERATOR)
        registerUser("adm_filt_crep_alice")
        registerUser("adm_filt_crep_bob")
        val a1 = insertArticleWithDetails("adm_filt_crep_a1")
        val a2 = insertArticleWithDetails("adm_filt_crep_a2")
        insertComplaintFor(a1, "adm_filt_crep_alice")
        insertComplaintFor(a2, "adm_filt_crep_bob")

        val resp = client.get("/api/v1/admin/complaints?reporter=alice") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["articleId"]?.jsonPrimitive?.int == a1 })
        assertFalse(items.any { it.jsonObject["articleId"]?.jsonPrimitive?.int == a2 })
    }

    @Test
    fun `complaints filtered by article language`() = testApplication {
        application { module() }
        val modToken = registerUser("adm_filt_clang_mod")
        setRole("adm_filt_clang_mod", Role.MODERATOR)
        registerUser("adm_filt_clang_rep")
        val enId = insertArticleWithDetails("adm_filt_clang_en", language = "en")
        val frId = insertArticleWithDetails("adm_filt_clang_fr", language = "fr")
        insertComplaintFor(enId, "adm_filt_clang_rep")
        insertComplaintFor(frId, "adm_filt_clang_rep")

        val resp = client.get("/api/v1/admin/complaints?language=fr") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["articleId"]?.jsonPrimitive?.int == frId })
        assertFalse(items.any { it.jsonObject["articleId"]?.jsonPrimitive?.int == enId })
    }

    @Test
    fun `complaints invalid reason param returns 400`() = testApplication {
        application { module() }
        val modToken = registerUser("adm_filt_creason_bad")
        setRole("adm_filt_creason_bad", Role.MODERATOR)

        val resp = client.get("/api/v1/admin/complaints?reason=SPAM") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `complaints combined filters - reason and language`() = testApplication {
        application { module() }
        val modToken = registerUser("adm_filt_ccomb_mod")
        setRole("adm_filt_ccomb_mod", Role.MODERATOR)
        registerUser("adm_filt_ccomb_rep")
        val frBroken = insertArticleWithDetails("adm_filt_ccomb_fr_broken", language = "fr")
        val frMisl = insertArticleWithDetails("adm_filt_ccomb_fr_misl", language = "fr")
        val enBroken = insertArticleWithDetails("adm_filt_ccomb_en_broken", language = "en")
        insertComplaintWithReason(frBroken, "adm_filt_ccomb_rep", ReportReason.BROKEN_LINK)
        insertComplaintWithReason(frMisl, "adm_filt_ccomb_rep", ReportReason.MISLEADING)
        insertComplaintWithReason(enBroken, "adm_filt_ccomb_rep", ReportReason.BROKEN_LINK)

        val resp = client.get("/api/v1/admin/complaints?reason=BROKEN_LINK&language=fr") {
            header(HttpHeaders.Authorization, "Bearer $modToken")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["articleId"]?.jsonPrimitive?.int == frBroken })
        assertFalse(items.any { it.jsonObject["articleId"]?.jsonPrimitive?.int == frMisl })
        assertFalse(items.any { it.jsonObject["articleId"]?.jsonPrimitive?.int == enBroken })
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
