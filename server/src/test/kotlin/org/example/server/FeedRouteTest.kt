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
import java.time.LocalDateTime

@ExtendWith(ServerTestDb::class)
class FeedRouteTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Tag IDs created in @BeforeEach
    private var catTagId = 0
    private var subTagId = 0

    @BeforeEach
    fun setup() {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            try {
                val cat = Tag("FeedTestCategory").also {
                    it.kind = TagKind.CATEGORY; it.tagKey = "cat_feedtest"
                }
                val sub = Tag("FeedTestSubtopic").also {
                    it.kind = TagKind.SUBTOPIC; it.tagKey = "sub_feedtest"; it.parent = cat
                }
                session.persist(cat)
                session.flush()
                session.persist(sub)
                session.flush()
                catTagId = cat.id
                subTagId = sub.id
                tx.commit()
            } catch (e: Exception) {
                tx.rollback(); throw e
            }
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
                // ArticleTagPercent cascades with Article (CascadeType.ALL), but delete explicitly to avoid FK issues
                session.createNativeQuery(
                    "DELETE atp FROM article_tag_percent atp " +
                    "INNER JOIN article a ON a.id = atp.article_id WHERE a.title LIKE 'feedtest_%'"
                ).executeUpdate()
                session.createMutationQuery(
                    "delete from Article a where a.title like 'feedtest_%'"
                ).executeUpdate()
                session.createMutationQuery("delete from AuditLog").executeUpdate()
                session.createMutationQuery("delete from RefreshToken").executeUpdate()
                session.createMutationQuery("delete from User").executeUpdate()
                // Delete test tags: subtopics first (FK parent constraint), then categories
                session.createMutationQuery(
                    "delete from Tag t where t.tagKey like '%feedtest' and t.kind = :k"
                ).setParameter("k", TagKind.SUBTOPIC).executeUpdate()
                session.createMutationQuery(
                    "delete from Tag t where t.tagKey like '%feedtest' and t.kind = :k"
                ).setParameter("k", TagKind.CATEGORY).executeUpdate()
                // Delete test region if any
                session.createNativeQuery(
                    "DELETE FROM region WHERE region_key LIKE '%feedtest'"
                ).executeUpdate()
                tx.commit()
            } catch (e: Exception) {
                tx.rollback(); throw e
            }
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.registerAndLogin(nick: String): String {
        val resp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"$nick","password":"Password1!","languages":[],"regions":[]}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status, "Register failed for $nick: ${resp.bodyAsText()}")
        return json.decodeFromString<TokenPairDto>(resp.bodyAsText()).accessToken
    }

    private fun insertArticle(
        session: org.hibernate.Session,
        title: String,
        promptVersion: String? = "v1",
        language: String? = "en",
        region: String? = null,
        publisher: String? = "TestPublisher",
        createdAt: LocalDateTime = LocalDateTime.now(),
        publicationDate: java.util.Date? = null
    ): Article {
        val a = Article().also {
            it.title = title
            it.link = "https://feedtest.example.com/$title"
            it.promptVersion = promptVersion
            it.articleLanguage = language
            it.articleRegion = region
            it.publisher = publisher
            it.createdAt = createdAt
            it.publicationDate = publicationDate
        }
        session.persist(a)
        session.flush()
        return a
    }

    private fun attachTag(session: org.hibernate.Session, article: Article, tagId: Int, percent: Int) {
        val tag = session.find(Tag::class.java, tagId)
        session.persist(ArticleTagPercent(article, tag, percent))
        session.flush()
    }

    private suspend fun feedGet(
        builder: ApplicationTestBuilder,
        token: String,
        params: String = ""
    ) = builder.client.get("/api/v1/feed$params") {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    // ─── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `score ordering high score first`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_score")
        val userId = JWT.decode(token).subject.toInt()

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                // highArticle: catTag at 80%, user weight 70 → score 5600
                val highArticle = insertArticle(session, "feedtest_high")
                attachTag(session, highArticle, catTagId, 80)
                // lowArticle: catTag at 20%, user weight 70 → score 1400
                val lowArticle = insertArticle(session, "feedtest_low")
                attachTag(session, lowArticle, catTagId, 20)

                val user = session.find(User::class.java, userId)
                val cat = session.find(Tag::class.java, catTagId)
                session.persist(UserTagPercent(user, cat, 70))
                tx.commit()
            }
        }

        val resp = feedGet(this, token)
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.size >= 2, "Expected at least 2 items")
        val titles = items.map { it.jsonObject["title"]!!.jsonPrimitive.content }
        val highIdx = titles.indexOfFirst { it == "feedtest_high" }
        val lowIdx = titles.indexOfFirst { it == "feedtest_low" }
        assertTrue(highIdx >= 0 && lowIdx >= 0, "Both articles should appear")
        assertTrue(highIdx < lowIdx, "High-score article must come before low-score")
    }

    @Test
    fun `date tie-break newer first when scores equal`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_date")

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val older = insertArticle(session, "feedtest_older",
                    createdAt = LocalDateTime.now().minusDays(5))
                val newer = insertArticle(session, "feedtest_newer",
                    createdAt = LocalDateTime.now().minusDays(1))
                tx.commit()
            }
        }

        val resp = feedGet(this, token)
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        val titles = items.map { it.jsonObject["title"]!!.jsonPrimitive.content }
        val olderIdx = titles.indexOfFirst { it == "feedtest_older" }
        val newerIdx = titles.indexOfFirst { it == "feedtest_newer" }
        assertTrue(newerIdx >= 0 && olderIdx >= 0, "Both articles should appear")
        assertTrue(newerIdx < olderIdx, "Newer article must come before older when scores equal")
    }

    @Test
    fun `pagination 3 pages limit 2 no duplicates no gaps`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_pages")

        val articleTitles = (1..6).map { "feedtest_page_$it" }
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                // Insert with spaced-out dates for deterministic ordering
                articleTitles.forEachIndexed { i, title ->
                    insertArticle(session, title, createdAt = LocalDateTime.now().minusDays((6 - i).toLong()))
                }
                tx.commit()
            }
        }

        val seen = mutableSetOf<Int>()
        var cursor: String? = null

        repeat(3) { page ->
            val params = if (cursor == null) "?limit=2" else "?limit=2&cursor=$cursor"
            val resp = feedGet(this, token, params)
            assertEquals(HttpStatusCode.OK, resp.status, "Page $page failed")
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val items = body["items"]!!.jsonArray
            assertEquals(2, items.size, "Page $page should have 2 items")
            items.forEach { item ->
                val id = item.jsonObject["id"]!!.jsonPrimitive.int
                assertFalse(id in seen, "Duplicate article $id on page $page")
                seen.add(id)
            }
            cursor = body["nextCursor"]?.jsonPrimitive?.contentOrNull
            if (page < 2) assertNotNull(cursor, "Expected cursor after page $page")
        }
        assertEquals(6, seen.size, "Total seen articles should be 6")
    }

    @Test
    fun `dislike and complaint excluded from feed`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_dislike")
        val userId = JWT.decode(token).subject.toInt()

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val art = insertArticle(session, "feedtest_disliked")
                val user = session.find(User::class.java, userId)
                session.persist(Report(art, user, -1))
                tx.commit()
            }
        }

        val resp = feedGet(this, token)
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.none { it.jsonObject["title"]?.jsonPrimitive?.content == "feedtest_disliked" },
            "Disliked article must not appear in feed")
    }

    @Test
    fun `TAG block excludes article from feed`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_tagblock")
        val userId = JWT.decode(token).subject.toInt()

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val art = insertArticle(session, "feedtest_blocked_tag")
                attachTag(session, art, catTagId, 60)
                val user = session.find(User::class.java, userId)
                val blockedTag = session.find(Tag::class.java, catTagId)
                session.persist(UserBlock().also {
                    it.user = user; it.blockType = BlockType.TAG; it.tag = blockedTag
                })
                tx.commit()
            }
        }

        val resp = feedGet(this, token)
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.none { it.jsonObject["title"]?.jsonPrimitive?.content == "feedtest_blocked_tag" },
            "Article with blocked tag must not appear")
    }

    @Test
    fun `REGION block excludes article from feed`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_regionblock")
        val userId = JWT.decode(token).subject.toInt()

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                // Insert a test region
                val testRegion = Region().also {
                    it.regionKey = "ro_feedtest"; it.name = "FeedTest Region"
                }
                session.persist(testRegion)
                session.flush()
                val art = insertArticle(session, "feedtest_blocked_region", region = "ro_feedtest")
                val user = session.find(User::class.java, userId)
                session.persist(UserBlock().also {
                    it.user = user; it.blockType = BlockType.REGION; it.region = testRegion
                })
                tx.commit()
            }
        }

        val resp = feedGet(this, token)
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.none { it.jsonObject["title"]?.jsonPrimitive?.content == "feedtest_blocked_region" },
            "Article from blocked region must not appear")
    }

    @Test
    fun `PUBLISHER block excludes article from feed`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_pubblock")
        val userId = JWT.decode(token).subject.toInt()

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val art = insertArticle(session, "feedtest_blocked_pub", publisher = "BlockedPub_feedtest")
                val user = session.find(User::class.java, userId)
                session.persist(UserBlock().also {
                    it.user = user; it.blockType = BlockType.PUBLISHER; it.publisher = "BlockedPub_feedtest"
                })
                tx.commit()
            }
        }

        val resp = feedGet(this, token)
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.none { it.jsonObject["title"]?.jsonPrimitive?.content == "feedtest_blocked_pub" },
            "Article from blocked publisher must not appear")
    }

    @Test
    fun `language filter restricts to user languages`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_lang")

        // Set user language to "en" via settings endpoint
        client.put("/api/v1/me/settings") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"languages":["en"],"regions":[]}""")
        }

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                insertArticle(session, "feedtest_en_article", language = "en")
                insertArticle(session, "feedtest_fr_article", language = "fr")
                tx.commit()
            }
        }

        val resp = feedGet(this, token)
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.any { it.jsonObject["title"]?.jsonPrimitive?.content == "feedtest_en_article" },
            "English article should appear")
        assertTrue(items.none { it.jsonObject["title"]?.jsonPrimitive?.content == "feedtest_fr_article" },
            "French article must not appear when user set language=en")
    }

    @Test
    fun `unclassified articles excluded from feed`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_unclass")

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                insertArticle(session, "feedtest_classified", promptVersion = "v1")
                insertArticle(session, "feedtest_unclassified", promptVersion = null)
                tx.commit()
            }
        }

        val resp = feedGet(this, token)
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertTrue(items.none { it.jsonObject["title"]?.jsonPrimitive?.content == "feedtest_unclassified" },
            "Unclassified article must not appear")
        assertTrue(items.any { it.jsonObject["title"]?.jsonPrimitive?.content == "feedtest_classified" },
            "Classified article should appear")
    }

    @Test
    fun `no weights returns newest first`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_noweights")

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                // No UserTagPercent for this user → all scores = 0 → date ordering
                insertArticle(session, "feedtest_nw_old", createdAt = LocalDateTime.now().minusDays(10))
                insertArticle(session, "feedtest_nw_new", createdAt = LocalDateTime.now().minusDays(1))
                tx.commit()
            }
        }

        val resp = feedGet(this, token)
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        val titles = items.map { it.jsonObject["title"]!!.jsonPrimitive.content }
        val oldIdx = titles.indexOfFirst { it == "feedtest_nw_old" }
        val newIdx = titles.indexOfFirst { it == "feedtest_nw_new" }
        assertTrue(newIdx >= 0 && oldIdx >= 0, "Both articles should appear")
        assertTrue(newIdx < oldIdx, "Without weights, newer article must come before older")
    }

    @Test
    fun `limit 100 clamped to 50 items`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_clamp")

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                // Insert 55 classified articles
                for (i in 1..55) {
                    insertArticle(session, "feedtest_clamp_$i",
                        createdAt = LocalDateTime.now().minusMinutes(i.toLong()))
                }
                tx.commit()
            }
        }

        val resp = feedGet(this, token, "?limit=100")
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        assertEquals(50, items.size, "limit=100 must be clamped to 50")
    }

    @Test
    fun `pagination microsecond precision no gaps no duplicates`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_micros")
        val userId = JWT.decode(token).subject.toInt()

        // 3 articles: same score (50*50=2500), created_at differs by exactly 1 microsecond
        // With millis-only cursor the page-2 cursor_date (.000000) != stored dates (.000001-.000003)
        // → pages 2 and 3 would be empty. Micros fix ensures all 3 are returned.
        val baseTime = LocalDateTime.of(2024, 1, 15, 12, 0, 0, 0)
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val a1 = insertArticle(session, "feedtest_micro_1", createdAt = baseTime.plusNanos(1_000))
                val a2 = insertArticle(session, "feedtest_micro_2", createdAt = baseTime.plusNanos(2_000))
                val a3 = insertArticle(session, "feedtest_micro_3", createdAt = baseTime.plusNanos(3_000))
                attachTag(session, a1, catTagId, 50)
                attachTag(session, a2, catTagId, 50)
                attachTag(session, a3, catTagId, 50)
                val user = session.find(User::class.java, userId)
                val cat = session.find(Tag::class.java, catTagId)
                session.persist(UserTagPercent(user, cat, 50))
                tx.commit()
            }
        }

        val seen = mutableSetOf<Int>()
        var cursor: String? = null

        repeat(3) { page ->
            val params = if (cursor == null) "?limit=1" else "?limit=1&cursor=$cursor"
            val resp = feedGet(this, token, params)
            assertEquals(HttpStatusCode.OK, resp.status, "Page $page failed")
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val items = body["items"]!!.jsonArray
            assertEquals(1, items.size, "Page $page should have exactly 1 item")
            val id = items[0].jsonObject["id"]!!.jsonPrimitive.int
            assertFalse(id in seen, "Duplicate article $id on page $page")
            seen.add(id)
            cursor = body["nextCursor"]?.jsonPrimitive?.contentOrNull
            if (page < 2) assertNotNull(cursor, "Expected cursor on page $page")
        }
        assertEquals(3, seen.size, "All 3 microsecond-precision articles must be returned across 3 pages")
    }

    @Test
    fun `invalid cursor returns 422`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_badcursor")

        val resp = feedGet(this, token, "?cursor=notavalidcursor!!!")
        assertEquals(HttpStatusCode.UnprocessableEntity, resp.status)
    }

    @Test
    fun `GET articles by id returns 404 for unknown`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_404")

        val resp = client.get("/api/v1/articles/9999999") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `GET articles by id returns 404 for unclassified`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_unclass_id")

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val art = insertArticle(session, "feedtest_unclassified_id", promptVersion = null)
                articleId = art.id
                tx.commit()
            }
        }

        val resp = client.get("/api/v1/articles/$articleId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `matchPercent partial article 60pct weight 50 max 100 gives 30`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_mp30")
        val userId = JWT.decode(token).subject.toInt()

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                // Second category with higher user weight → makes max weight = 100
                val catTag2 = Tag("FeedTestCategory2").also {
                    it.kind = TagKind.CATEGORY; it.tagKey = "cat2_feedtest"
                }
                session.persist(catTag2)
                session.flush()

                val art = insertArticle(session, "feedtest_mp30_art")
                attachTag(session, art, catTagId, 60)      // article 60% in catTag

                val user = session.find(User::class.java, userId)
                val cat = session.find(Tag::class.java, catTagId)
                session.persist(UserTagPercent(user, cat, 50))       // weight 50
                session.persist(UserTagPercent(user, catTag2, 100))  // weight 100 → max
                tx.commit()
            }
        }

        val resp = feedGet(this, token)
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        val art = items.first { it.jsonObject["title"]?.jsonPrimitive?.content == "feedtest_mp30_art" }
        // score = 60*50 = 3000; userMaxWeight = 100; matchPercent = 3000/100 = 30
        assertEquals(30, art.jsonObject["matchPercent"]?.jsonPrimitive?.intOrNull,
            "matchPercent should be 30 for 60% article, weight 50, max weight 100")
    }

    @Test
    fun `matchPercent full match in max weight category gives 100`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_mp100")
        val userId = JWT.decode(token).subject.toInt()

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val art = insertArticle(session, "feedtest_mp100_art")
                attachTag(session, art, catTagId, 100)    // 100% in catTag

                val user = session.find(User::class.java, userId)
                val cat = session.find(Tag::class.java, catTagId)
                session.persist(UserTagPercent(user, cat, 75))  // weight 75 (this is max)
                tx.commit()
            }
        }

        val resp = feedGet(this, token)
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        val art = items.first { it.jsonObject["title"]?.jsonPrimitive?.content == "feedtest_mp100_art" }
        // score = 100*75 = 7500; userMaxWeight = 75; matchPercent = 7500/75 = 100
        assertEquals(100, art.jsonObject["matchPercent"]?.jsonPrimitive?.intOrNull,
            "matchPercent should be 100 for 100% article in max-weight category")
    }

    @Test
    fun `matchPercent no user weights gives null`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_mpnull")

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val art = insertArticle(session, "feedtest_mpnull_art")
                attachTag(session, art, catTagId, 60)  // article has tags, user has no interests
                tx.commit()
            }
        }

        val resp = feedGet(this, token)
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        val art = items.first { it.jsonObject["title"]?.jsonPrimitive?.content == "feedtest_mpnull_art" }
        // No UserTagPercent → userMaxWeight = 0 → matchPercent = null
        val mp = art.jsonObject["matchPercent"]
        assertTrue(mp == null || mp is kotlinx.serialization.json.JsonNull,
            "matchPercent should be null when user has no interests")
    }

    @Test
    fun `matchPercent subtopic weight does not inflate matchPercent`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_mpsub")
        val userId = JWT.decode(token).subject.toInt()

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val art = insertArticle(session, "feedtest_mpsub_art")
                attachTag(session, art, catTagId, 60)   // article 60% in CATEGORY tag
                attachTag(session, art, subTagId, 80)   // article 80% in SUBTOPIC tag

                val user = session.find(User::class.java, userId)
                val cat = session.find(Tag::class.java, catTagId)
                val sub = session.find(Tag::class.java, subTagId)
                session.persist(UserTagPercent(user, cat, 80))  // CATEGORY weight 80 (is max)
                session.persist(UserTagPercent(user, sub, 40))  // SUBTOPIC weight from rating feedback
                tx.commit()
            }
        }

        val resp = feedGet(this, token)
        assertEquals(HttpStatusCode.OK, resp.status)
        val items = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["items"]!!.jsonArray
        val art = items.first { it.jsonObject["title"]?.jsonPrimitive?.content == "feedtest_mpsub_art" }
        // category_score = 60*80 = 4800; user_max_weight (CATEGORY only) = 80; matchPercent = 4800/80 = 60
        // All-tag score = 60*80 + 80*40 = 8000; 8000/80 = 100 (would be wrong without fix)
        assertEquals(60, art.jsonObject["matchPercent"]?.jsonPrimitive?.intOrNull,
            "SUBTOPIC weight must not inflate matchPercent: expected 60, not 100")
    }

    @Test
    fun `GET articles by id returns article for classified`() = testApplication {
        application { module() }
        val token = registerAndLogin("feedtest_artbyid")

        var articleId = 0
        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val art = insertArticle(session, "feedtest_artbyid_article", promptVersion = "v1", language = "en")
                attachTag(session, art, catTagId, 70)
                articleId = art.id
                tx.commit()
            }
        }

        val resp = client.get("/api/v1/articles/$articleId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(articleId, body["id"]!!.jsonPrimitive.int)
        assertEquals("feedtest_artbyid_article", body["title"]!!.jsonPrimitive.content)
        assertNotNull(body["mainCategory"], "mainCategory should be present")
    }
}
