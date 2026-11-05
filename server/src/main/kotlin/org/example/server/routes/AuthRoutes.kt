package org.example.server.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.example.User
import org.example.dto.ErrorResponse
import org.example.dto.LoginRequest
import org.example.dto.RegisterRequest
import org.example.dto.TokenPairDto
import org.example.server.auth.AuditService
import org.example.server.auth.JwtConfig
import org.example.server.auth.LoginProtection
import org.example.server.auth.RefreshTokenService
import org.example.server.util.withNewTransaction
import org.example.service.UserService
import org.example.util.HibernateUtil

fun Route.authRoutes(
    jwtConfig: JwtConfig,
    loginProtection: LoginProtection,
    refreshTokenService: RefreshTokenService,
    auditService: AuditService
) {
    val userService = UserService()

    route("/api/v1/auth") {
        post("/register") {
            val req = call.receive<RegisterRequest>()

            val user: User = try {
                userService.registerUserNoEmail(req.nick, req.password)
            } catch (e: IllegalArgumentException) {
                val msg = e.message ?: "Registration failed"
                val code = if (msg.contains("already taken", ignoreCase = true) ||
                    msg.contains("already exists", ignoreCase = true)
                ) "NICK_TAKEN" else "INVALID_INPUT"
                val status = if (code == "NICK_TAKEN") HttpStatusCode.Conflict else HttpStatusCode.BadRequest
                call.respond(status, ErrorResponse(code, msg))
                return@post
            }

            val (accessToken, refreshRaw) = withNewTransaction { session ->
                val managed = session.find(User::class.java, user.id)
                    ?: error("User disappeared after registration: id=${user.id}")
                val access = jwtConfig.issueAccessToken(managed.nick, managed.role)
                val refresh = refreshTokenService.issue(session, managed)
                auditService.log(session, managed, "REGISTER", "user", managed.id.toString())
                Pair(access, refresh)
            }

            call.respond(
                HttpStatusCode.Created,
                TokenPairDto(accessToken = accessToken, refreshToken = refreshRaw, accessExpiresIn = 15 * 60)
            )
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            val remoteHost = call.request.local.remoteHost

            val retryAfter = loginProtection.retryAfterSeconds(remoteHost, req.login)
            if (retryAfter != null) {
                call.response.headers.append("Retry-After", retryAfter.toString())
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("TOO_MANY_ATTEMPTS", "Too many failed login attempts")
                )
                return@post
            }

            val userOpt = userService.authenticate(req.login, req.password)
            if (userOpt.isEmpty) {
                loginProtection.recordFailure(remoteHost, req.login)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("INVALID_CREDENTIALS", "Invalid credentials")
                )
                return@post
            }

            val user = userOpt.get()
            loginProtection.recordSuccess(remoteHost, req.login)

            val (accessToken, refreshRaw) = withNewTransaction { session ->
                val managed = session.find(User::class.java, user.id)
                    ?: error("User disappeared during login: id=${user.id}")
                val access = jwtConfig.issueAccessToken(managed.nick, managed.role)
                val refresh = refreshTokenService.issue(session, managed)
                auditService.log(session, managed, "LOGIN", "user", managed.id.toString())
                Pair(access, refresh)
            }

            call.respond(
                HttpStatusCode.OK,
                TokenPairDto(accessToken = accessToken, refreshToken = refreshRaw, accessExpiresIn = 15 * 60)
            )
        }
    }
}
