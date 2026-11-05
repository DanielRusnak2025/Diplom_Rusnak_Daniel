package org.example.server

import com.auth0.jwt.JWT
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.example.Tag
import org.example.TagKind
import org.example.User
import org.example.UserTagPercent
import org.example.dto.TokenPairDto
import org.example.util.HibernateUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ServerTestDb::class)
class InterestRouteTest {

    private val json = Json { ignoreUnknownKeys = true }
    private var techId = 0
    private var sportsId = 0
    private var entityId = 0

    @BeforeEach
    fun setup() {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            try {
                val tech = Tag("Technology").also { it.kind = TagKind.CATEGORY; it.tagKey = "technology_test" }
                val sports = Tag("Sports").also { it.kind = TagKind.CATEGORY; it.tagKey = "sports_test" }
                session.persist(tech)
                session.persist(sports)
                session.flush()

                val ai = Tag("AI").also { it.kind = TagKind.SUBTOPIC; it.tagKey = "ai_test"; it.parent = tech }
                val mobile = Tag("Mobile").also { it.kind = TagKind.SUBTOPIC; it.tagKey = "mobile_test"; it.parent = tech }
                val football = Tag("Football").also {
                    it.kind = TagKind.SUBTOPIC; it.tagKey = "football_test"; it.parent = sports
                }
                session.persist(ai)
                session.persist(mobile)
                session.persist(football)

                val entity = Tag("Python").also { it.kind = TagKind.ENTITY; it.tagKey = "python_test" }
                session.persist(entity)
                session.flush()

                techId = tech.id
                sportsId = sports.id
                entityId = entity.id

                tx.commit()
            } catch (e: Exception) {
                tx.rollback()
                throw e
            }
        }
    }

    @AfterEach
    fun cleanup() {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            try {
                session.createMutationQuery("delete from UserTagPercent").executeUpdate()
                session.createMutationQuery("delete from AuditLog").executeUpdate()
                session.createMutationQuery("delete from RefreshToken").executeUpdate()
                session.createMutationQuery("delete from User").executeUpdate()
                session.createMutationQuery("delete from Tag t where t.kind = :k")
                    .setParameter("k", TagKind.SUBTOPIC).executeUpdate()
                session.createMutationQuery("delete from Tag t where t.kind = :k")
                    .setParameter("k", TagKind.CATEGORY).executeUpdate()
                session.createMutationQuery("delete from Tag t where t.kind = :k")
                    .setParameter("k", TagKind.ENTITY).executeUpdate()
                tx.commit()
            } catch (e: Exception) {
                tx.rollback()
                throw e
            }
        }
    }

    private suspend fun ApplicationTestBuilder.registerAndLogin(nick: String): String {
        val resp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"$nick","password":"Password1!","languages":[],"regions":[]}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status, "Register failed for $nick")
        return json.decodeFromString<TokenPairDto>(resp.bodyAsText()).accessToken
    }

    @Test
    fun `GET categories returns categories with subtopics`() = testApplication {
        application { module() }
        val token = registerAndLogin("test_cat_list")
        val resp = client.get("/api/v1/categories") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonArray
        // DB already has seeded categories from V5_4 migration; find our test-inserted ones by key
        val techCat = body.firstOrNull { it.jsonObject["key"]?.jsonPrimitive?.content == "technology_test" }
        assertNotNull(techCat, "technology_test category not found in response")
        assertEquals("Technology", techCat!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(techId, techCat.jsonObject["id"]!!.jsonPrimitive.int)
        val subtopics = techCat.jsonObject["subtopics"]!!.jsonArray
        assertEquals(2, subtopics.size)
        val sportsCat = body.firstOrNull { it.jsonObject["key"]?.jsonPrimitive?.content == "sports_test" }
        assertNotNull(sportsCat, "sports_test category not found in response")
        assertEquals(1, sportsCat!!.jsonObject["subtopics"]!!.jsonArray.size)
    }

    @Test
    fun `PUT interests selects categories sets percent to 50`() = testApplication {
        application { module() }
        val token = registerAndLogin("test_int_set")
        val resp = client.put("/api/v1/me/interests") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"categoryIds":[$techId]}""")
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)

        val getResp = client.get("/api/v1/me/interests") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val body = Json.parseToJsonElement(getResp.bodyAsText()).jsonArray
        val techInterest = body.first { it.jsonObject["tagId"]!!.jsonPrimitive.int == techId }
        assertEquals(50, techInterest.jsonObject["percent"]!!.jsonPrimitive.int)
    }

    @Test
    fun `PUT interests re-set keeps learned higher weight`() = testApplication {
        application { module() }
        val token = registerAndLogin("test_int_keep")
        val userId = JWT.decode(token).subject.toInt()

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val user = session.find(User::class.java, userId)
                val tech = session.find(Tag::class.java, techId)
                session.persist(UserTagPercent(user, tech, 80))
                tx.commit()
            }
        }

        val resp = client.put("/api/v1/me/interests") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"categoryIds":[$techId]}""")
        }
        assertEquals(HttpStatusCode.NoContent, resp.status)

        val getResp = client.get("/api/v1/me/interests") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val body = Json.parseToJsonElement(getResp.bodyAsText()).jsonArray
        val techInterest = body.first { it.jsonObject["tagId"]!!.jsonPrimitive.int == techId }
        assertEquals(80, techInterest.jsonObject["percent"]!!.jsonPrimitive.int)
    }

    @Test
    fun `PUT interests deselect sets percent to zero`() = testApplication {
        application { module() }
        val token = registerAndLogin("test_int_desel")

        client.put("/api/v1/me/interests") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"categoryIds":[$techId]}""")
        }

        client.put("/api/v1/me/interests") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"categoryIds":[]}""")
        }

        val getResp = client.get("/api/v1/me/interests") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val body = Json.parseToJsonElement(getResp.bodyAsText()).jsonArray
        val techInterest = body.firstOrNull { it.jsonObject["tagId"]!!.jsonPrimitive.int == techId }
        assertNotNull(techInterest, "tech entry should still be present (with percent 0)")
        assertEquals(0, techInterest!!.jsonObject["percent"]!!.jsonPrimitive.int)
    }

    @Test
    fun `PUT interests with non-category id returns 422`() = testApplication {
        application { module() }
        val token = registerAndLogin("test_int_422")
        val resp = client.put("/api/v1/me/interests") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"categoryIds":[$entityId]}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, resp.status)
    }

    @Test
    fun `PUT interests leaves ENTITY rows unchanged`() = testApplication {
        application { module() }
        val token = registerAndLogin("test_int_entity")
        val userId = JWT.decode(token).subject.toInt()

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val tx = session.beginTransaction()
                val user = session.find(User::class.java, userId)
                val entity = session.find(Tag::class.java, entityId)
                session.persist(UserTagPercent(user, entity, 70))
                tx.commit()
            }
        }

        client.put("/api/v1/me/interests") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"categoryIds":[$techId]}""")
        }

        withContext(Dispatchers.IO) {
            HibernateUtil.getSessionFactory().openSession().use { session ->
                val utp = session.createSelectionQuery(
                    "from UserTagPercent utp where utp.user.id = :uid and utp.tag.id = :tid",
                    UserTagPercent::class.java
                ).setParameter("uid", userId).setParameter("tid", entityId).uniqueResult()
                assertNotNull(utp, "ENTITY row should still exist")
                assertEquals(70, utp!!.percent)
            }
        }
    }
}
