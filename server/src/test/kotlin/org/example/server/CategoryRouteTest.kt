package org.example.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.example.Tag
import org.example.TagKind
import org.example.util.HibernateUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ServerTestDb::class)
class CategoryRouteTest {

    private val json = Json { ignoreUnknownKeys = true }
    private var catTagId = 0

    @BeforeEach
    fun setup() {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            try {
                val cat = Tag("TestCategoryEn").also {
                    it.kind = TagKind.CATEGORY
                    it.tagKey = "cat_catroute_test"
                    it.nameRu = "ТестКатегория"
                }
                session.persist(cat)
                session.flush()
                catTagId = cat.id
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
                session.createMutationQuery("delete from Tag t where t.tagKey = 'cat_catroute_test'").executeUpdate()
                session.createMutationQuery("delete from AuditLog").executeUpdate()
                session.createMutationQuery("delete from RefreshToken").executeUpdate()
                session.createMutationQuery("delete from User").executeUpdate()
                tx.commit()
            } catch (e: Exception) {
                tx.rollback(); throw e
            }
        }
    }

    private suspend fun ApplicationTestBuilder.registerAndLogin(nick: String): String {
        val resp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"nick":"$nick","password":"Password1!","languages":[],"regions":[]}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status, "Register failed for $nick")
        val body = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        return body["accessToken"]!!.jsonPrimitive.content
    }

    @Test
    fun `GET categories without Accept-Language returns English name`() = testApplication {
        application { module() }
        val token = registerAndLogin("cat_test_en")

        val resp = client.get("/api/v1/categories") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val array = json.parseToJsonElement(resp.bodyAsText()).jsonArray
        val testCat = array.firstOrNull { it.jsonObject["key"]?.jsonPrimitive?.content == "cat_catroute_test" }
        assertNotNull(testCat, "Test category not found in response")
        assertEquals("TestCategoryEn", testCat!!.jsonObject["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET categories with Accept-Language ru returns Russian name`() = testApplication {
        application { module() }
        val token = registerAndLogin("cat_test_ru")

        val resp = client.get("/api/v1/categories") {
            bearerAuth(token)
            header(HttpHeaders.AcceptLanguage, "ru-RU,ru;q=0.9")
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val array = json.parseToJsonElement(resp.bodyAsText()).jsonArray
        val testCat = array.firstOrNull { it.jsonObject["key"]?.jsonPrimitive?.content == "cat_catroute_test" }
        assertNotNull(testCat, "Test category not found in response")
        assertEquals("ТестКатегория", testCat!!.jsonObject["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET categories with Accept-Language en returns English name`() = testApplication {
        application { module() }
        val token = registerAndLogin("cat_test_en2")

        val resp = client.get("/api/v1/categories") {
            bearerAuth(token)
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9")
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val array = json.parseToJsonElement(resp.bodyAsText()).jsonArray
        val testCat = array.firstOrNull { it.jsonObject["key"]?.jsonPrimitive?.content == "cat_catroute_test" }
        assertNotNull(testCat, "Test category not found in response")
        assertEquals("TestCategoryEn", testCat!!.jsonObject["name"]?.jsonPrimitive?.content)
    }
}
