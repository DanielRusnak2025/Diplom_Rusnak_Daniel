package org.example.server.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.example.User
import org.example.dto.ErrorResponse
import org.example.server.service.FeedService
import org.example.server.util.withNewTransaction
import org.example.util.HibernateUtil

fun Route.feedRoutes(feedService: FeedService) {
    authenticate("jwt-access") {
        get("/api/v1/feed") {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject.toInt()
            val rawLimit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val limit = rawLimit.coerceIn(1, 50)
            val cursor = call.request.queryParameters["cursor"]
            val regionFilter = call.request.queryParameters["region"]
            val categoryFilter = call.request.queryParameters["category"]?.toIntOrNull()
            val lang = call.request.headers["Accept-Language"]
                ?.let { if (it.startsWith("ru", ignoreCase = true)) "ru" else "en" } ?: "en"

            val result = try {
                withNewTransaction { session ->
                    val user = session.find(User::class.java, userId)
                    val languages = user?.languages ?: emptySet()
                    feedService.getFeed(session, userId, languages, cursor, limit, regionFilter, categoryFilter, lang)
                }
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorResponse("INVALID_CURSOR", e.message ?: "Invalid cursor")
                )
                return@get
            }
            call.respond(result)
        }

        get("/api/v1/articles/{id}") {
            val userId = call.principal<JWTPrincipal>()!!.payload.subject.toInt()
            val articleId = call.parameters["id"]?.toIntOrNull()
                ?: run {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                    return@get
                }
            val lang = call.request.headers["Accept-Language"]
                ?.let { if (it.startsWith("ru", ignoreCase = true)) "ru" else "en" } ?: "en"

            val article = withNewTransaction { session ->
                feedService.getArticleById(session, userId, articleId, lang)
            }

            if (article == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
            } else {
                call.respond(article)
            }
        }
    }
}
