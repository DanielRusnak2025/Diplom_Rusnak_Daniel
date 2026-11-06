package org.example.server.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.example.Article
import org.example.AuditLog
import org.example.Report
import org.example.Role
import org.example.User
import org.example.dto.AdminUserDto
import org.example.dto.AdminUserPageDto
import org.example.dto.AuditLogItemDto
import org.example.dto.AuditPageDto
import org.example.dto.ComplaintItemDto
import org.example.dto.ComplaintPageDto
import org.example.dto.ErrorResponse
import org.example.dto.HiddenRequest
import org.example.dto.ReportReasonDto
import org.example.dto.RoleDto
import org.example.dto.SetRoleRequest
import org.example.dto.StatsDto
import org.example.server.auth.AuditService
import org.example.server.util.requireRoleFromDb
import org.example.server.util.withNewTransaction
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter

private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd")
private val DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

fun Route.adminRoutes(auditService: AuditService) {
    authenticate("jwt-access") {
        route("/api/v1/admin") {

            // ── MODERATOR + ADMIN ─────────────────────────────────────────────

            get("/complaints") {
                val actorId = call.requireRoleFromDb(Role.MODERATOR, Role.ADMIN)?.id ?: return@get
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
                val cursor = call.request.queryParameters["cursor"]?.toIntOrNull()

                val page = withNewTransaction { session ->
                    val hql = buildString {
                        append("FROM Report r WHERE r.reasonCode IS NOT NULL")
                        if (cursor != null) append(" AND r.id < :cursor")
                        append(" ORDER BY r.id DESC")
                    }
                    val q = session.createSelectionQuery(hql, Report::class.java)
                    if (cursor != null) q.setParameter("cursor", cursor)
                    q.setMaxResults(limit + 1)
                    val rows = q.resultList
                    val hasMore = rows.size > limit
                    val items = rows.take(limit).map { r ->
                        ComplaintItemDto(
                            reportId = r.id,
                            articleId = r.article.id,
                            articleTitle = r.article.title,
                            userId = r.user.id,
                            userNick = r.user.nick,
                            reasonCode = ReportReasonDto.valueOf(r.reasonCode!!.name),
                            date = r.date?.let { DATE_FMT.format(it) }
                        )
                    }
                    val nextCursor = if (hasMore) items.last().reportId.toString() else null
                    ComplaintPageDto(items, nextCursor)
                }
                call.respond(page)
            }

            put("/articles/{id}/hidden") {
                val actorId = call.requireRoleFromDb(Role.MODERATOR, Role.ADMIN)?.id ?: return@put
                val articleId = call.parameters["id"]?.toIntOrNull()
                    ?: run { call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found")); return@put }
                val body = call.receive<HiddenRequest>()

                val found = withNewTransaction { session ->
                    val article = session.find(Article::class.java, articleId) ?: return@withNewTransaction false
                    article.isHidden = body.hidden
                    val actor = session.find(User::class.java, actorId)!!
                    auditService.log(
                        session, actor,
                        if (body.hidden) "ARTICLE_HIDDEN" else "ARTICLE_UNHIDDEN",
                        "article", articleId.toString()
                    )
                    true
                }
                if (!found) call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                else call.respond(HttpStatusCode.NoContent)
            }

            // ── ADMIN only ────────────────────────────────────────────────────

            get("/users") {
                val actorId = call.requireRoleFromDb(Role.ADMIN)?.id ?: return@get
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
                val cursor = call.request.queryParameters["cursor"]?.toIntOrNull()

                val page = withNewTransaction { session ->
                    val hql = buildString {
                        append("FROM User u")
                        if (cursor != null) append(" WHERE u.id < :cursor")
                        append(" ORDER BY u.id DESC")
                    }
                    val q = session.createSelectionQuery(hql, User::class.java)
                    if (cursor != null) q.setParameter("cursor", cursor)
                    q.setMaxResults(limit + 1)
                    val rows = q.resultList
                    val hasMore = rows.size > limit
                    val items = rows.take(limit).map { u ->
                        AdminUserDto(
                            id = u.id,
                            nick = u.nick,
                            email = u.email,
                            role = RoleDto.valueOf(u.role.name),
                            registrationDate = u.registrationDate?.let { DATE_FMT.format(it) }
                        )
                    }
                    val nextCursor = if (hasMore) items.last().id.toString() else null
                    AdminUserPageDto(items, nextCursor)
                }
                call.respond(page)
            }

            put("/users/{id}/role") {
                val actor = call.requireRoleFromDb(Role.ADMIN) ?: return@put
                val targetId = call.parameters["id"]?.toIntOrNull()
                    ?: run { call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "User not found")); return@put }
                if (targetId == actor.id) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("SELF_ROLE_CHANGE", "Cannot change your own role"))
                    return@put
                }
                val body = call.receive<SetRoleRequest>()
                val newRole = Role.valueOf(body.role.name)

                val found = withNewTransaction { session ->
                    val target = session.find(User::class.java, targetId) ?: return@withNewTransaction false
                    val managedActor = session.find(User::class.java, actor.id)!!
                    target.role = newRole
                    auditService.log(
                        session, managedActor, "USER_ROLE_CHANGED",
                        "user", targetId.toString(),
                        "role=${newRole.name}"
                    )
                    true
                }
                if (!found) call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "User not found"))
                else call.respond(HttpStatusCode.NoContent)
            }

            get("/audit") {
                val actorId = call.requireRoleFromDb(Role.ADMIN)?.id ?: return@get
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
                val cursor = call.request.queryParameters["cursor"]?.toLongOrNull()

                val page = withNewTransaction { session ->
                    val hql = buildString {
                        append("FROM AuditLog al")
                        if (cursor != null) append(" WHERE al.id < :cursor")
                        append(" ORDER BY al.id DESC")
                    }
                    val q = session.createSelectionQuery(hql, AuditLog::class.java)
                    if (cursor != null) q.setParameter("cursor", cursor)
                    q.setMaxResults(limit + 1)
                    val rows = q.resultList
                    val hasMore = rows.size > limit
                    val items = rows.take(limit).map { al ->
                        AuditLogItemDto(
                            id = al.id,
                            actorId = al.actor?.id,
                            actorNick = al.actor?.nick,
                            action = al.action,
                            targetType = al.targetType,
                            targetId = al.targetId,
                            details = al.details,
                            createdAt = al.createdAt?.format(DATETIME_FMT)
                        )
                    }
                    val nextCursor = if (hasMore) items.last().id.toString() else null
                    AuditPageDto(items, nextCursor)
                }
                call.respond(page)
            }

            get("/stats") {
                val actorId = call.requireRoleFromDb(Role.ADMIN)?.id ?: return@get
                val stats = withNewTransaction { session ->
                    val total = session.createSelectionQuery(
                        "SELECT COUNT(a) FROM Article a", Long::class.java
                    ).uniqueResult()
                    val classified = session.createSelectionQuery(
                        "SELECT COUNT(a) FROM Article a WHERE a.promptVersion IS NOT NULL", Long::class.java
                    ).uniqueResult()
                    val users = session.createSelectionQuery(
                        "SELECT COUNT(u) FROM User u", Long::class.java
                    ).uniqueResult()
                    val complaints = session.createSelectionQuery(
                        "SELECT COUNT(r) FROM Report r WHERE r.reasonCode IS NOT NULL", Long::class.java
                    ).uniqueResult()
                    StatsDto(
                        totalArticles = total,
                        classifiedArticles = classified,
                        pendingQueue = total - classified,
                        totalUsers = users,
                        openComplaints = complaints
                    )
                }
                call.respond(stats)
            }
        }
    }
}
