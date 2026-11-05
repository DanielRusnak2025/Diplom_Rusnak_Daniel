package org.example.server.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.Region
import org.example.Role
import org.example.User
import org.example.dto.*
import org.example.server.auth.AuditService
import org.example.server.auth.RefreshTokenService
import org.example.server.util.withNewTransaction
import org.example.service.UserService

fun Route.meRoutes(refreshTokenService: RefreshTokenService, auditService: AuditService) {
    val userService = UserService()

    authenticate("jwt-access") {
        route("/api/v1/me") {

            get {
                val userId = call.principal<JWTPrincipal>()!!.payload.subject.toInt()
                val dto = withNewTransaction { session ->
                    val user = session.find(User::class.java, userId)
                        ?: error("Authenticated user vanished: $userId")
                    user.toProfileDto()
                }
                call.respond(dto)
            }

            put("/settings") {
                val userId = call.principal<JWTPrincipal>()!!.payload.subject.toInt()
                val req = call.receive<UpdateSettingsRequest>()

                val dto: UserProfileDto = try {
                    withNewTransaction { session ->
                        val user = session.find(User::class.java, userId)
                            ?: error("User not found: $userId")
                        req.languages?.let { langs ->
                            langs.forEach { lang ->
                                if (!lang.matches(Regex("^[a-z]{2}$")))
                                    throw IllegalArgumentException("Invalid language code: $lang")
                            }
                            user.languages = langs.toHashSet()
                        }
                        req.regions?.let { keys ->
                            keys.forEach { key ->
                                if (session.find(Region::class.java, key) == null)
                                    throw IllegalArgumentException("Unknown region_key: $key")
                            }
                            user.regions = keys.toHashSet()
                        }
                        req.notificationFrequency?.let { freq ->
                            user.notificationFrequency = freq
                        }
                        user.toProfileDto()
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorResponse("INVALID_INPUT", e.message ?: "Invalid input")
                    )
                    return@put
                }
                call.respond(dto)
            }

            put("/password") {
                val userId = call.principal<JWTPrincipal>()!!.payload.subject.toInt()
                val req = call.receive<ChangePasswordRequest>()

                try {
                    withContext(Dispatchers.IO) {
                        userService.updatePassword(userId, req.currentPassword, req.newPassword)
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorResponse("INVALID_PASSWORD", e.message ?: "Invalid password")
                    )
                    return@put
                }

                withNewTransaction { session ->
                    refreshTokenService.revokeAllForUser(session, userId)
                    val user = session.find(User::class.java, userId)
                    auditService.log(session, user, "PASSWORD_CHANGED", "user", userId.toString())
                }

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun User.toProfileDto() = UserProfileDto(
    id = id,
    nick = nick,
    email = email,
    role = RoleDto.valueOf(role.name),
    languages = languages.toList().sorted(),
    regions = regions.toList().sorted(),
    notificationFrequency = notificationFrequency
)
