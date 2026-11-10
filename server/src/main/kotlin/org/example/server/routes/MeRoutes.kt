package org.example.server.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.example.Region
import org.example.Tag
import org.example.TagKind
import org.example.User
import org.example.UserTagPercent
import org.example.ai.AiConfig
import org.example.dto.*
import org.example.server.auth.AuditService
import org.example.server.auth.LoginProtection
import org.example.server.auth.RefreshTokenService
import org.example.server.util.withNewTransaction
import org.example.service.UserService

fun Route.meRoutes(
    loginProtection: LoginProtection,
    refreshTokenService: RefreshTokenService,
    auditService: AuditService
) {
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
                val allowed = AiConfig.getLanguagesAllowed()

                val dto: UserProfileDto = try {
                    withNewTransaction { session ->
                        val user = session.find(User::class.java, userId)
                            ?: error("User not found: $userId")
                        req.languages?.let { langs ->
                            langs.forEach { lang ->
                                if (lang !in allowed)
                                    throw IllegalArgumentException("Unsupported language code: $lang")
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

                val retryAfter = loginProtection.retryAfterSeconds("pwd", userId.toString())
                if (retryAfter != null) {
                    call.response.headers.append("Retry-After", retryAfter.toString())
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("TOO_MANY_ATTEMPTS", "Too many failed password attempts")
                    )
                    return@put
                }

                try {
                    withNewTransaction { session ->
                        userService.updatePasswordInSession(session, userId, req.currentPassword, req.newPassword)
                        refreshTokenService.revokeAllForUser(session, userId)
                        val user = session.find(User::class.java, userId)
                        auditService.log(session, user, "PASSWORD_CHANGED", "user", userId.toString())
                    }
                } catch (e: IllegalArgumentException) {
                    loginProtection.recordFailure("pwd", userId.toString())
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorResponse("INVALID_PASSWORD", e.message ?: "Invalid password")
                    )
                    return@put
                }

                loginProtection.recordSuccess("pwd", userId.toString())
                call.respond(HttpStatusCode.NoContent)
            }

            get("/interests") {
                val userId = call.principal<JWTPrincipal>()!!.payload.subject.toInt()
                val lang = call.request.headers["Accept-Language"]
                    ?.let { if (it.startsWith("ru", ignoreCase = true)) "ru" else "en" } ?: "en"
                val dtos = withNewTransaction { session ->
                    session.createSelectionQuery(
                        "from UserTagPercent utp where utp.user.id = :uid and utp.tag.kind = :kind" +
                        " order by utp.tag.name",
                        UserTagPercent::class.java
                    ).setParameter("uid", userId)
                     .setParameter("kind", TagKind.CATEGORY)
                     .resultList
                     .map { utp ->
                         val name = if (lang == "ru") utp.tag.nameRu ?: utp.tag.name else utp.tag.name
                         InterestDto(
                             tagId = utp.tag.id,
                             key = utp.tag.tagKey ?: "",
                             name = name,
                             percent = utp.percent
                         )
                     }
                }
                call.respond(dtos)
            }

            put("/interests") {
                val userId = call.principal<JWTPrincipal>()!!.payload.subject.toInt()
                val req = call.receive<SetInterestsRequest>()

                try {
                    withNewTransaction { session ->
                        val allCategories = session.createSelectionQuery(
                            "from Tag t where t.kind = :kind",
                            Tag::class.java
                        ).setParameter("kind", TagKind.CATEGORY).resultList.associateBy { it.id }

                        req.categoryIds.forEach { id ->
                            if (allCategories[id] == null)
                                throw IllegalArgumentException("Tag $id is not a valid CATEGORY")
                        }

                        val user = session.find(User::class.java, userId)
                            ?: error("User not found: $userId")
                        val selectedIds = req.categoryIds.toSet()

                        val existingWeights = session.createSelectionQuery(
                            "from UserTagPercent utp where utp.user.id = :uid and utp.tag.kind = :kind",
                            UserTagPercent::class.java
                        ).setParameter("uid", userId)
                         .setParameter("kind", TagKind.CATEGORY)
                         .resultList
                         .associateBy { it.tag.id }

                        allCategories.values.forEach { cat ->
                            val newPercent = if (cat.id in selectedIds)
                                maxOf(existingWeights[cat.id]?.percent ?: 0, 50)
                            else
                                0
                            val existing = existingWeights[cat.id]
                            if (existing != null) {
                                existing.percent = newPercent
                            } else if (newPercent > 0) {
                                session.persist(UserTagPercent(user, cat, newPercent))
                            }
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorResponse("INVALID_INPUT", e.message ?: "Invalid input")
                    )
                    return@put
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
