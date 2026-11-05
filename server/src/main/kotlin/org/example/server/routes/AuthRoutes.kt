package org.example.server.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.RefreshToken
import org.example.User
import org.example.dto.ErrorResponse
import org.example.dto.LoginRequest
import org.example.dto.RefreshRequest
import org.example.dto.RegisterRequest
import org.example.dto.TokenPairDto
import org.example.server.auth.AuditService
import org.example.server.auth.JwtConfig
import org.example.server.auth.LoginProtection
import org.example.server.auth.RefreshTokenService
import org.example.server.util.withNewTransaction
import org.example.service.NickAlreadyTakenException
import org.example.service.UserService
import java.time.LocalDateTime

private sealed class RefreshOutcome {
    object NotFound : RefreshOutcome()
    object Reused : RefreshOutcome()
    object Expired : RefreshOutcome()
    data class Ok(val access: String, val refresh: String) : RefreshOutcome()
}

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
                withContext(Dispatchers.IO) { userService.registerUserNoEmail(req.nick, req.password) }
            } catch (e: NickAlreadyTakenException) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("NICK_TAKEN", "Nick already taken"))
                return@post
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_INPUT", e.message ?: "Invalid input"))
                return@post
            }

            val (accessToken, refreshRaw) = withNewTransaction { session ->
                val managed = session.find(User::class.java, user.id)
                    ?: error("User disappeared after registration: id=${user.id}")
                val access = jwtConfig.issueAccessToken(managed.id, managed.role)
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

            val userOpt = withContext(Dispatchers.IO) { userService.authenticate(req.login, req.password) }
            if (userOpt.isEmpty) {
                loginProtection.recordFailure(remoteHost, req.login)
                withNewTransaction { session ->
                    auditService.log(session, null, "LOGIN_FAILED", "user", null, "nick=${req.login}")
                }
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
                val access = jwtConfig.issueAccessToken(managed.id, managed.role)
                val refresh = refreshTokenService.issue(session, managed)
                auditService.log(session, managed, "LOGIN", "user", managed.id.toString())
                Pair(access, refresh)
            }

            call.respond(
                HttpStatusCode.OK,
                TokenPairDto(accessToken = accessToken, refreshToken = refreshRaw, accessExpiresIn = 15 * 60)
            )
        }

        post("/refresh") {
            val req = call.receive<RefreshRequest>()
            val hash = RefreshTokenService.sha256hex(req.refreshToken)

            val outcome: RefreshOutcome = withNewTransaction { session ->
                val token: RefreshToken = refreshTokenService.findByHash(session, hash)
                    ?: return@withNewTransaction RefreshOutcome.NotFound

                when {
                    token.revokedAt != null -> {
                        if (refreshTokenService.hasActiveFamilyToken(session, token.familyId)) {
                            refreshTokenService.revokeFamily(session, token.familyId)
                            auditService.log(session, null, "TOKEN_REUSE", "refresh_token", token.id.toString())
                            RefreshOutcome.Reused
                        } else {
                            RefreshOutcome.NotFound
                        }
                    }
                    token.expiresAt.isBefore(LocalDateTime.now()) -> {
                        token.revokedAt = LocalDateTime.now()
                        session.merge(token)
                        RefreshOutcome.Expired
                    }
                    else -> {
                        refreshTokenService.revoke(session, token)
                        val user = token.user
                        val newAccess = jwtConfig.issueAccessToken(user.id, user.role)
                        val newRefresh = refreshTokenService.issue(session, user, token.familyId)
                        auditService.log(session, user, "TOKEN_REFRESH", "user", user.id.toString())
                        RefreshOutcome.Ok(newAccess, newRefresh)
                    }
                }
            }

            when (outcome) {
                RefreshOutcome.NotFound ->
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("INVALID_TOKEN", "Invalid refresh token"))
                RefreshOutcome.Reused ->
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("TOKEN_REUSED", "Token reuse detected — all sessions revoked"))
                RefreshOutcome.Expired ->
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("TOKEN_EXPIRED", "Refresh token expired"))
                is RefreshOutcome.Ok ->
                    call.respond(HttpStatusCode.OK, TokenPairDto(outcome.access, outcome.refresh, 15 * 60))
            }
        }

        post("/logout") {
            val req = call.receive<RefreshRequest>()
            val hash = RefreshTokenService.sha256hex(req.refreshToken)

            withNewTransaction { session ->
                val token = refreshTokenService.findByHash(session, hash)
                if (token != null && token.revokedAt == null) {
                    refreshTokenService.revoke(session, token)
                    auditService.log(session, token.user, "LOGOUT", "user", token.user.id.toString())
                }
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
