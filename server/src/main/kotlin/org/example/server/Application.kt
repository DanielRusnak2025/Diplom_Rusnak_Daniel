package org.example.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import org.example.dto.ErrorResponse
import org.example.dto.HealthDto

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(CallLogging)
    install(StatusPages) {
        exception<SerializationException> { call, _ ->
            call.respond(HttpStatusCode.UnprocessableEntity,
                ErrorResponse("INVALID_REQUEST", "Invalid request body"))
        }
        exception<Exception> { call, _ ->
            call.respond(HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "Internal server error"))
        }
    }
    routing {
        get("/health") {
            call.respond(HealthDto("ok"))
        }
    }
}
