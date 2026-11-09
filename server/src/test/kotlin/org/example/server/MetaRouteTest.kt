package org.example.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.util.HibernateUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ServerTestDb::class)
class MetaRouteTest {

    private val json = Json { ignoreUnknownKeys = true }

    @AfterEach
    fun cleanup() {
        HibernateUtil.getSessionFactory().openSession().use { session ->
            val tx = session.beginTransaction()
            try {
                session.createMutationQuery("delete from AuditLog").executeUpdate()
                session.createMutationQuery("delete from RefreshToken").executeUpdate()
                session.createMutationQuery("delete from User").executeUpdate()
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
        val body = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        return body["accessToken"]!!.jsonPrimitive.content
    }

    @Test
    fun `GET regions without JWT returns 401`() = testApplication {
        application { module() }
        val resp = client.get("/api/v1/regions")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET regions with JWT returns 12 rows including known keys`() = testApplication {
        application { module() }
        val token = registerAndLogin("meta_user1")

        val resp = client.get("/api/v1/regions") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val array = json.parseToJsonElement(resp.bodyAsText()).jsonArray
        assertEquals(12, array.size)

        val keys = array.map { it.jsonObject["key"]!!.jsonPrimitive.content }.toSet()
        assertTrue("western_europe" in keys)
        assertTrue("north_america" in keys)
        assertTrue("global" in keys)
    }

    @Test
    fun `GET languages without JWT returns 401`() = testApplication {
        application { module() }
        val resp = client.get("/api/v1/languages")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET languages with JWT returns codes including en and ru`() = testApplication {
        application { module() }
        val token = registerAndLogin("meta_user2")

        val resp = client.get("/api/v1/languages") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, resp.status)

        val array = json.parseToJsonElement(resp.bodyAsText()).jsonArray
        val codes = array.map { it.jsonObject["code"]!!.jsonPrimitive.content }.toSet()
        assertTrue("en" in codes)
        assertTrue("ru" in codes)
    }
}
