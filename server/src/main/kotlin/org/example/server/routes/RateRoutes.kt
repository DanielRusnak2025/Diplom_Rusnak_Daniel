package org.example.server.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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

                val found = withNewTransaction { session ->
                    val art = session.find(Article::class.java, articleId)
                    if (art == null || art.promptVersion == null) false
                    else { reportService.upsertRatingInSession(session, articleId, userId, body.rating); true }
                }

                if (!found) call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                else call.respond(HttpStatusCode.NoContent)
            }

            delete("/rate") {
                val articleId = call.parameters["id"]?.toIntOrNull()
                    ?: run {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                        return@delete
                    }
                val userId = call.principal<JWTPrincipal>()!!.payload.subject.toInt()

                // 0=notFound, 1=complaint→409, 2=noRating→204 no-op, 3=deleted→204
                val outcome = withNewTransaction { session ->
                    val art = session.find(Article::class.java, articleId)
                    if (art == null || art.promptVersion == null) return@withNewTransaction 0

                    val r = session.createSelectionQuery(
                        "FROM Report r WHERE r.user.id = :uid AND r.article.id = :aid",
                        Report::class.java
                    ).setParameter("uid", userId).setParameter("aid", articleId)
                     .uniqueResultOptional().orElse(null)

                    if (r == null) return@withNewTransaction 2
                    if (r.reasonCode != null) return@withNewTransaction 1

                    if (r.rating != 0) reportService.upsertRatingInSession(session, articleId, userId, 0)
                    session.createMutationQuery(
                        "DELETE FROM Report r WHERE r.user.id = :uid AND r.article.id = :aid"
                    ).setParameter("uid", userId).setParameter("aid", articleId).executeUpdate()
                    3
                }

                when (outcome) {
                    0 -> call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                    1 -> call.respond(HttpStatusCode.Conflict, ErrorResponse("COMPLAINT_CANNOT_BE_REMOVED", "Cannot delete a complaint; use the complain endpoint to update it"))
                    else -> call.respond(HttpStatusCode.NoContent)
                }
            }

            post("/complain") {
                val articleId = call.parameters["id"]?.toIntOrNull()
                    ?: run {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                        return@post
                    }
                val userId = call.principal<JWTPrincipal>()!!.payload.subject.toInt()
                val body = call.receive<ComplainBody>()

                val reason = ReportReason.valueOf(body.reasonCode.name)
                val found = withNewTransaction { session ->
                    val art = session.find(Article::class.java, articleId)
                    if (art == null || art.promptVersion == null) false
                    else { reportService.complainInSession(session, articleId, userId, reason); true }
                }

                if (!found) call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                else call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
