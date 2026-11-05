package org.example.server.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.example.Article
import org.example.Report
import org.example.ReportReason
import org.example.dto.ErrorResponse
import org.example.dto.ReportReasonDto
import org.example.server.util.withNewTransaction
import org.example.service.ReportService

@Serializable
private data class RateBody(val rating: Int)

@Serializable
private data class ComplainBody(val reasonCode: ReportReasonDto)

fun Route.rateRoutes(reportService: ReportService) {
    authenticate("jwt-access") {
        route("/api/v1/articles/{id}") {

            post("/rate") {
                val articleId = call.parameters["id"]?.toIntOrNull()
                    ?: run {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                        return@post
                    }
                val userId = call.principal<JWTPrincipal>()!!.payload.subject.toInt()
                val body = call.receive<RateBody>()

                if (body.rating != 1 && body.rating != -1) {
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("INVALID_RATING", "Rating must be 1 or -1"))
                    return@post
                }

                val articleOk = withNewTransaction { session ->
                    val art = session.find(Article::class.java, articleId)
                    art != null && art.promptVersion != null
                }
                if (!articleOk) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                    return@post
                }

                withContext(Dispatchers.IO) {
                    reportService.upsertRating(articleId, userId, body.rating)
                }
                call.respond(HttpStatusCode.NoContent)
            }

            delete("/rate") {
                val articleId = call.parameters["id"]?.toIntOrNull()
                    ?: run {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                        return@delete
                    }
                val userId = call.principal<JWTPrincipal>()!!.payload.subject.toInt()

                // Single session: check article + get existing rating
                val (articleOk, existingRating) = withNewTransaction { session ->
                    val art = session.find(Article::class.java, articleId)
                    if (art == null || art.promptVersion == null) {
                        Pair(false, null as Int?)
                    } else {
                        val r = session.createSelectionQuery(
                            "FROM Report r WHERE r.user.id = :uid AND r.article.id = :aid",
                            Report::class.java
                        ).setParameter("uid", userId).setParameter("aid", articleId)
                         .uniqueResultOptional().orElse(null)
                        Pair(true, r?.rating)
                    }
                }

                if (!articleOk) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                    return@delete
                }
                if (existingRating == null) {
                    call.respond(HttpStatusCode.NoContent)
                    return@delete
                }

                // Revert weight effect using upsertRating(0) — ReportService variant B
                if (existingRating != 0) {
                    withContext(Dispatchers.IO) {
                        reportService.upsertRating(articleId, userId, 0)
                    }
                }
                // Delete the report row (now neutral or already neutral)
                withNewTransaction { session ->
                    session.createMutationQuery(
                        "DELETE FROM Report r WHERE r.user.id = :uid AND r.article.id = :aid"
                    ).setParameter("uid", userId).setParameter("aid", articleId).executeUpdate()
                }
                call.respond(HttpStatusCode.NoContent)
            }

            post("/complain") {
                val articleId = call.parameters["id"]?.toIntOrNull()
                    ?: run {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                        return@post
                    }
                val userId = call.principal<JWTPrincipal>()!!.payload.subject.toInt()
                val body = call.receive<ComplainBody>()

                val articleOk = withNewTransaction { session ->
                    val art = session.find(Article::class.java, articleId)
                    art != null && art.promptVersion != null
                }
                if (!articleOk) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                    return@post
                }

                val reason = ReportReason.valueOf(body.reasonCode.name)
                withContext(Dispatchers.IO) {
                    reportService.complain(articleId, userId, reason)
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
