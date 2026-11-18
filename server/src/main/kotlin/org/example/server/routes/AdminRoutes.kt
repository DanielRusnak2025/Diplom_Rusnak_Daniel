package org.example.server.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jakarta.persistence.Tuple
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.format.DateTimeFormatter
import org.example.Article
import org.example.ArticleTagPercent
import org.example.AuditLog
import org.example.Report
import org.example.Role
import org.example.Tag
import org.example.TagKind
import org.example.User
import org.example.UserBlock
import org.example.UserTagPercent
import org.example.dto.AdminArticleDetailDto
import org.example.dto.AdminArticleDto
import org.example.dto.AdminArticlePageDto
import org.example.dto.AdminBlockDto
import org.example.dto.AdminInterestDto
import org.example.dto.AdminUserDetailDto
import org.example.dto.AdminUserDto
import org.example.dto.AdminUserPageDto
import org.example.dto.AdminUserRatingsDto
import org.example.dto.AdminUserRefDto
import org.example.dto.AuditLogItemDto
import org.example.dto.AuditPageDto
import org.example.dto.BlockTypeDto
import org.example.dto.ComplaintItemDto
import org.example.dto.ComplaintPageDto
import org.example.dto.ErrorResponse
import org.example.dto.HiddenRequest
import org.example.dto.ReportReasonDto
import org.example.dto.RoleDto
import org.example.dto.SetRoleRequest
import org.example.dto.StatsDto
import org.example.dto.TagRefDto
import org.example.server.auth.AuditService
import org.example.server.util.requireRoleFromDb
import org.example.server.util.withNewTransaction
import java.text.SimpleDateFormat

private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd")
private val DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

private fun escapeLike(s: String): String =
    s.replace("!", "!!").replace("%", "!%").replace("_", "!_")

fun Route.adminRoutes(auditService: AuditService) {
    authenticate("jwt-access") {
        route("/api/v1/admin") {

            // ── MODERATOR + ADMIN ─────────────────────────────────────────────

            get("/complaints") {
                val actorId = call.requireRoleFromDb(Role.MODERATOR, Role.ADMIN)?.id ?: return@get
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
                val cursor = call.request.queryParameters["cursor"]?.toIntOrNull()
                val q = call.request.queryParameters["q"]?.trim()?.takeIf { it.isNotEmpty() }
                val fromDateStr = call.request.queryParameters["fromDate"]
                val toDateStr = call.request.queryParameters["toDate"]

                val fromDate = if (fromDateStr != null) {
                    try { LocalDate.parse(fromDateStr) }
                    catch (_: DateTimeParseException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_DATE", "Invalid fromDate: $fromDateStr"))
                        return@get
                    }
                } else null

                val toDate = if (toDateStr != null) {
                    try { LocalDate.parse(toDateStr) }
                    catch (_: DateTimeParseException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_DATE", "Invalid toDate: $toDateStr"))
                        return@get
                    }
                } else null

                val page = withNewTransaction { session ->
                    val hql = buildString {
                        append("FROM Report r WHERE r.reasonCode IS NOT NULL")
                        if (q != null) append(" AND LOWER(r.article.title) LIKE LOWER(:q) ESCAPE '!'")
                        if (fromDate != null) append(" AND r.date >= :fromDate")
                        if (toDate != null) append(" AND r.date <= :toDate")
                        if (cursor != null) append(" AND r.id < :cursor")
                        append(" ORDER BY r.id DESC")
                    }
                    val query = session.createSelectionQuery(hql, Report::class.java)
                    if (q != null) query.setParameter("q", "%${escapeLike(q)}%")
                    if (fromDate != null) query.setParameter("fromDate", java.sql.Date.valueOf(fromDate))
                    if (toDate != null) query.setParameter("toDate", java.sql.Date.valueOf(toDate))
                    if (cursor != null) query.setParameter("cursor", cursor)
                    query.setMaxResults(limit + 1)
                    val rows = query.resultList
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

            // ── Article search ────────────────────────────────────────────────

            get("/articles") {
                val actorId = call.requireRoleFromDb(Role.MODERATOR, Role.ADMIN)?.id ?: return@get
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 50)
                val cursor = call.request.queryParameters["cursor"]?.toIntOrNull()
                val q = call.request.queryParameters["q"]?.trim()?.takeIf { it.isNotEmpty() }
                val fromDateStr = call.request.queryParameters["fromDate"]
                val toDateStr = call.request.queryParameters["toDate"]
                val hiddenParam = call.request.queryParameters["hidden"]

                val fromDate = if (fromDateStr != null) {
                    try { LocalDate.parse(fromDateStr) }
                    catch (_: DateTimeParseException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_DATE", "Invalid fromDate: $fromDateStr"))
                        return@get
                    }
                } else null

                val toDate = if (toDateStr != null) {
                    try { LocalDate.parse(toDateStr) }
                    catch (_: DateTimeParseException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_DATE", "Invalid toDate: $toDateStr"))
                        return@get
                    }
                } else null

                val hiddenFilter: Boolean? = when (hiddenParam?.lowercase()) {
                    "true"  -> true
                    "false" -> false
                    null    -> null
                    else -> {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_PARAM", "hidden must be true or false"))
                        return@get
                    }
                }

                val page = withNewTransaction { session ->
                    val conditions = mutableListOf<String>()
                    val params = mutableMapOf<String, Any>()

                    if (q != null) {
                        conditions.add("LOWER(a.title) LIKE LOWER(:q) ESCAPE '!'")
                        params["q"] = "%${escapeLike(q)}%"
                    }
                    if (fromDate != null) {
                        conditions.add("COALESCE(a.publication_date, DATE(a.created_at)) >= :fromDate")
                        params["fromDate"] = java.sql.Date.valueOf(fromDate)
                    }
                    if (toDate != null) {
                        conditions.add("COALESCE(a.publication_date, DATE(a.created_at)) <= :toDate")
                        params["toDate"] = java.sql.Date.valueOf(toDate)
                    }
                    if (hiddenFilter != null) {
                        conditions.add("a.hidden = :hidden")
                        params["hidden"] = hiddenFilter
                    }
                    if (cursor != null) {
                        conditions.add("a.id < :cursor")
                        params["cursor"] = cursor
                    }

                    val whereSql = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
                    val sql = """
                        SELECT a.id, a.title, a.link, a.publisher, a.article_language, a.article_region,
                               a.publication_date, a.created_at, a.hidden, a.prompt_version
                        FROM article a
                        $whereSql
                        ORDER BY a.id DESC
                        LIMIT :lim
                    """.trimIndent()

                    @Suppress("UNCHECKED_CAST")
                    val query = session.createNativeQuery(sql, Tuple::class.java)
                    query.setParameter("lim", limit + 1)
                    params.forEach { (k, v) -> query.setParameter(k, v) }

                    val rows = query.resultList as List<Tuple>
                    val hasMore = rows.size > limit
                    val items = rows.take(limit).map { t ->
                        fun rawToDateStr(raw: Any?): String? = when (raw) {
                            is java.time.LocalDate -> raw.toString()
                            is java.sql.Date -> raw.toLocalDate().toString()
                            is java.sql.Timestamp -> raw.toLocalDateTime().toLocalDate().toString()
                            is java.util.Date -> raw.toInstant()
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                            else -> null
                        }
                        val pubDateStr = rawToDateStr(t.get("publication_date"))
                        val createdAtStr = rawToDateStr(t.get("created_at"))
                        AdminArticleDto(
                            id = (t.get("id") as Number).toInt(),
                            title = t.get("title") as String?,
                            link = t.get("link") as String?,
                            publisher = t.get("publisher") as String?,
                            language = t.get("article_language") as String?,
                            region = t.get("article_region") as String?,
                            publicationDate = pubDateStr ?: createdAtStr,
                            hidden = when (val h = t.get("hidden")) {
                                is Boolean -> h
                                is Number  -> h.toInt() != 0
                                else       -> false
                            },
                            classified = t.get("prompt_version") != null
                        )
                    }
                    val nextCursor = if (hasMore) items.last().id.toString() else null
                    AdminArticlePageDto(items, nextCursor)
                }
                call.respond(page)
            }

            // ── Article detail ────────────────────────────────────────────────

            get("/articles/{id}") {
                val actorId = call.requireRoleFromDb(Role.MODERATOR, Role.ADMIN)?.id ?: return@get
                val articleId = call.parameters["id"]?.toIntOrNull()
                    ?: run { call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found")); return@get }

                val detail = withNewTransaction { session ->
                    val article = session.find(Article::class.java, articleId) ?: return@withNewTransaction null

                    val tags = session.createSelectionQuery(
                        "FROM ArticleTagPercent atp WHERE atp.article.id = :id ORDER BY atp.percent DESC",
                        ArticleTagPercent::class.java
                    ).setParameter("id", articleId).resultList.map { atp ->
                        TagRefDto(
                            id = atp.tag.id,
                            key = atp.tag.tagKey ?: "",
                            name = atp.tag.name ?: "",
                            percent = atp.percent,
                            nameRu = atp.tag.nameRu
                        )
                    }

                    val likeCount = session.createSelectionQuery(
                        "SELECT COUNT(r) FROM Report r WHERE r.article.id = :id AND r.rating = 1",
                        Long::class.java
                    ).setParameter("id", articleId).uniqueResult().toInt()

                    val dislikeCount = session.createSelectionQuery(
                        "SELECT COUNT(r) FROM Report r WHERE r.article.id = :id AND r.rating = -1 AND r.reasonCode IS NULL",
                        Long::class.java
                    ).setParameter("id", articleId).uniqueResult().toInt()

                    val complaintCount = session.createSelectionQuery(
                        "SELECT COUNT(r) FROM Report r WHERE r.article.id = :id AND r.reasonCode IS NOT NULL",
                        Long::class.java
                    ).setParameter("id", articleId).uniqueResult().toInt()

                    val complainants = session.createSelectionQuery(
                        "FROM Report r WHERE r.article.id = :id AND r.reasonCode IS NOT NULL ORDER BY r.id ASC",
                        Report::class.java
                    ).setParameter("id", articleId).resultList.map { r ->
                        AdminUserRefDto(
                            userId = r.user.id,
                            nick = r.user.nick,
                            reasonCode = ReportReasonDto.valueOf(r.reasonCode!!.name)
                        )
                    }

                    AdminArticleDetailDto(
                        id = article.id,
                        title = article.title,
                        link = article.link,
                        publisher = article.publisher,
                        language = article.articleLanguage,
                        region = article.articleRegion,
                        publicationDate = article.publicationDate?.let { DATE_FMT.format(it) },
                        hidden = article.isHidden,
                        classified = article.promptVersion != null,
                        tags = tags,
                        likeCount = likeCount,
                        dislikeCount = dislikeCount,
                        complaintCount = complaintCount,
                        complainants = complainants
                    )
                }

                if (detail == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                else call.respond(detail)
            }

            delete("/articles/{id}/complaints") {
                val actorId = call.requireRoleFromDb(Role.MODERATOR, Role.ADMIN)?.id ?: return@delete
                val articleId = call.parameters["id"]?.toIntOrNull()
                    ?: run { call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found")); return@delete }

                val found = withNewTransaction { session ->
                    session.find(Article::class.java, articleId) ?: return@withNewTransaction false
                    val updated = session.createMutationQuery(
                        "UPDATE Report r SET r.reasonCode = NULL WHERE r.article.id = :articleId AND r.reasonCode IS NOT NULL"
                    ).setParameter("articleId", articleId).executeUpdate()
                    val actor = session.find(User::class.java, actorId)!!
                    auditService.log(
                        session, actor, "COMPLAINTS_DISMISSED",
                        "article", articleId.toString(), "count=$updated"
                    )
                    true
                }
                if (!found) call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Article not found"))
                else call.respond(HttpStatusCode.NoContent)
            }

            delete("/complaints/{reportId}") {
                val actorId = call.requireRoleFromDb(Role.MODERATOR, Role.ADMIN)?.id ?: return@delete
                val reportId = call.parameters["reportId"]?.toIntOrNull()
                    ?: run { call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Complaint not found")); return@delete }

                val found = withNewTransaction { session ->
                    val report = session.find(Report::class.java, reportId) ?: return@withNewTransaction false
                    if (report.reasonCode == null) return@withNewTransaction false
                    report.reasonCode = null
                    val actor = session.find(User::class.java, actorId)!!
                    auditService.log(session, actor, "COMPLAINT_DISMISSED", "report", reportId.toString())
                    true
                }
                if (!found) call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Complaint not found"))
                else call.respond(HttpStatusCode.NoContent)
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
                val q = call.request.queryParameters["q"]?.trim()?.takeIf { it.isNotEmpty() }
                val fromDateStr = call.request.queryParameters["fromDate"]
                val toDateStr = call.request.queryParameters["toDate"]

                val fromDate = if (fromDateStr != null) {
                    try { LocalDate.parse(fromDateStr) }
                    catch (_: DateTimeParseException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_DATE", "Invalid fromDate: $fromDateStr"))
                        return@get
                    }
                } else null

                val toDate = if (toDateStr != null) {
                    try { LocalDate.parse(toDateStr) }
                    catch (_: DateTimeParseException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_DATE", "Invalid toDate: $toDateStr"))
                        return@get
                    }
                } else null

                val page = withNewTransaction { session ->
                    val conditions = mutableListOf<String>()
                    if (cursor != null) conditions.add("u.id < :cursor")
                    if (q != null) conditions.add("u.nick LIKE :q ESCAPE '!'")
                    if (fromDate != null) conditions.add("u.registrationDate >= :fromDate")
                    if (toDate != null) conditions.add("u.registrationDate <= :toDate")

                    val hql = buildString {
                        append("FROM User u")
                        if (conditions.isNotEmpty()) append(" WHERE " + conditions.joinToString(" AND "))
                        append(" ORDER BY u.id DESC")
                    }
                    val query = session.createSelectionQuery(hql, User::class.java)
                    if (cursor != null) query.setParameter("cursor", cursor)
                    if (q != null) query.setParameter("q", "%${escapeLike(q)}%")
                    if (fromDate != null) query.setParameter("fromDate", java.sql.Date.valueOf(fromDate))
                    if (toDate != null) query.setParameter("toDate", java.sql.Date.valueOf(toDate))
                    query.setMaxResults(limit + 1)
                    val rows = query.resultList
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

            // ── User detail ───────────────────────────────────────────────────

            get("/users/{id}") {
                val actorId = call.requireRoleFromDb(Role.ADMIN)?.id ?: return@get
                val userId = call.parameters["id"]?.toIntOrNull()
                    ?: run { call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "User not found")); return@get }

                val detail = withNewTransaction { session ->
                    val user = session.find(User::class.java, userId) ?: return@withNewTransaction null

                    val interests = session.createSelectionQuery(
                        "FROM UserTagPercent utp WHERE utp.user.id = :uid AND utp.percent != 0 AND utp.tag.kind = :kind ORDER BY utp.percent DESC",
                        UserTagPercent::class.java
                    ).setParameter("uid", userId).setParameter("kind", TagKind.CATEGORY).resultList.map { utp ->
                        AdminInterestDto(
                            tagKey = utp.tag.tagKey ?: "",
                            nameEn = utp.tag.name,
                            nameRu = utp.tag.nameRu,
                            percent = utp.percent
                        )
                    }

                    val blocks = session.createSelectionQuery(
                        "FROM UserBlock ub WHERE ub.user.id = :uid ORDER BY ub.id ASC",
                        UserBlock::class.java
                    ).setParameter("uid", userId).resultList.map { ub ->
                        AdminBlockDto(
                            blockType = BlockTypeDto.valueOf(ub.blockType.name),
                            tagKey = ub.tag?.tagKey,
                            region = ub.region?.regionKey,
                            publisher = ub.publisher
                        )
                    }

                    val likeCount = session.createSelectionQuery(
                        "SELECT COUNT(r) FROM Report r WHERE r.user.id = :uid AND r.rating = 1",
                        Long::class.java
                    ).setParameter("uid", userId).uniqueResult().toInt()

                    val dislikeCount = session.createSelectionQuery(
                        "SELECT COUNT(r) FROM Report r WHERE r.user.id = :uid AND r.rating = -1 AND r.reasonCode IS NULL",
                        Long::class.java
                    ).setParameter("uid", userId).uniqueResult().toInt()

                    val complaintCount = session.createSelectionQuery(
                        "SELECT COUNT(r) FROM Report r WHERE r.user.id = :uid AND r.reasonCode IS NOT NULL",
                        Long::class.java
                    ).setParameter("uid", userId).uniqueResult().toInt()

                    AdminUserDetailDto(
                        id = user.id,
                        nick = user.nick,
                        email = user.email,
                        registrationDate = user.registrationDate?.let { DATE_FMT.format(it) },
                        role = RoleDto.valueOf(user.role.name),
                        languages = user.languages.toList(),
                        regions = user.regions.toList(),
                        interests = interests,
                        blocks = blocks,
                        ratings = AdminUserRatingsDto(likeCount, dislikeCount, complaintCount)
                    )
                }

                if (detail == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "User not found"))
                else call.respond(detail)
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
                call.requireRoleFromDb(Role.ADMIN) ?: return@get
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
                val cursor = call.request.queryParameters["cursor"]?.toLongOrNull()
                val actions = call.request.queryParameters["actions"]
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() }

                val page = withNewTransaction { session ->
                    val conditions = mutableListOf<String>()
                    if (cursor != null) conditions.add("al.id < :cursor")
                    if (actions != null) conditions.add("al.action IN :actions")
                    val hql = buildString {
                        append("FROM AuditLog al")
                        if (conditions.isNotEmpty()) append(" WHERE ${conditions.joinToString(" AND ")}")
                        append(" ORDER BY al.id DESC")
                    }
                    val q = session.createSelectionQuery(hql, AuditLog::class.java)
                    if (cursor != null) q.setParameter("cursor", cursor)
                    if (actions != null) q.setParameter("actions", actions)
                    q.setMaxResults(limit + 1)
                    val rows = q.resultList
                    val hasMore = rows.size > limit
                    val limited = rows.take(limit)

                    val userTargetIds = limited
                        .filter { it.targetType == "user" }
                        .mapNotNull { it.targetId?.toIntOrNull() }
                        .toSet()
                    val nickByUserId: Map<Int, String?> = if (userTargetIds.isEmpty()) emptyMap() else {
                        session.createSelectionQuery("FROM User u WHERE u.id IN :ids", User::class.java)
                            .setParameter("ids", userTargetIds.toList())
                            .resultList
                            .associate { u -> u.id to u.nick }
                    }

                    val items = limited.map { al ->
                        val targetNick = if (al.targetType == "user") {
                            al.targetId?.toIntOrNull()?.let { nickByUserId[it] }
                        } else null
                        AuditLogItemDto(
                            id = al.id,
                            actorId = al.actor?.id,
                            actorNick = al.actor?.nick,
                            action = al.action,
                            targetType = al.targetType,
                            targetId = al.targetId,
                            details = al.details,
                            createdAt = al.createdAt?.format(DATETIME_FMT),
                            targetNick = targetNick,
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
