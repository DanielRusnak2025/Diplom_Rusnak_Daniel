package org.example.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HttpsRouteTest {

    @Test
    fun `api route returns 403 on plain http when https enabled`() = testApplication {
        application { module(httpsEnabled = true) }
        val resp = client.get("/api/v1/feed")
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("HTTPS_REQUIRED", body["code"]?.jsonPrimitive?.content)
    }

    @Test
    fun `health route accessible on plain http when https enabled`() = testApplication {
        application { module(httpsEnabled = true) }
        val resp = client.get("/health")
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `api routes work normally when https disabled`() = testApplication {
        application { module(httpsEnabled = false) }
        val resp = client.get("/api/v1/feed")
        // No HTTPS guard → auth guard fires → 401
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }
}
