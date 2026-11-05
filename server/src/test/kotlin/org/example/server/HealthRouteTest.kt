package org.example.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HealthRouteTest {

    @Test
    fun `GET health returns 200 with status ok`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""status":"ok""""), "body was: $body")
    }

    @Test
    fun `malformed JSON body on POST register returns 4xx not 500`() = testApplication {
        application { module() }
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("{this is not json}")
        }
        assertTrue(
            response.status.value in 400..499,
            "Expected 4xx status but got ${response.status}"
        )
    }
}
