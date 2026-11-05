package org.example.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.example.User
import org.example.dto.ErrorResponse
import org.example.dto.HealthDto
import org.example.server.auth.AuditService
import org.example.server.auth.JwtConfig
import org.example.server.auth.LoginProtection
import org.example.server.auth.RefreshTokenService
import org.example.server.config.ServerConfig
import org.example.server.routes.authRoutes
import org.example.server.routes.categoryRoutes
import org.example.server.routes.meRoutes
import org.example.util.HibernateUtil

fun Application.module() {
    val config = ServerConfig.load()
    val jwtConfig = JwtConfig(config.jwtSecret())
    val loginProtection = LoginProtection()
    val refreshTokenService = RefreshTokenService()
    val auditService = AuditService()

    install(Authentication) {
        jwt("jwt-access") {
            realm = "newsdove"
            verifier(jwtConfig.verifier())
            validate { credential ->
                val userId = credential.payload.subject?.toIntOrNull() ?: return@validate null
                withContext(Dispatchers.IO) {
                    HibernateUtil.getSessionFactory().openSession().use { session ->
                        if (session.find(User::class.java, userId) != null)
                            JWTPrincipal(credential.payload)
                        else
                            null
                    }
                }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("MISSING_TOKEN", "Authentication required")
                )
            }
        }
    }
    install(ContentNegotiation) { json() }
    install(CallLogging)
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", cause.message ?: "Bad request"))
        }
        exception<SerializationException> { call, _ ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse("INVALID_REQUEST", "Invalid request body")
            )
        }
        exception<Exception> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "Internal server error")
            )
        }
    }

    routing {
        get("/health") {
            call.respond(HealthDto("ok"))
        }
        authRoutes(jwtConfig, loginProtection, refreshTokenService, auditService)
        meRoutes(loginProtection, refreshTokenService, auditService)
        categoryRoutes()
    }
}
