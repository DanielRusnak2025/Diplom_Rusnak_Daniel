package org.example.androidapp.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.example.dto.AdminArticleDetailDto
import org.example.dto.AdminArticlePageDto
import org.example.dto.AdminUserDetailDto
import org.example.dto.AdminUserPageDto
import org.example.dto.AuditPageDto
import org.example.dto.ComplaintPageDto
import org.example.dto.HiddenRequest
import org.example.dto.RoleDto
import org.example.dto.SetRoleRequest
import org.example.dto.StatsDto

class AdminApi(
    private val baseUrl: String,
    private val authClient: HttpClient,
) {
    suspend fun complaints(
        cursor: String? = null,
        limit: Int = 20,
        q: String? = null,
        fromDate: String? = null,
        toDate: String? = null,
        reason: String? = null,
        reporter: String? = null,
        language: String? = null,
    ): ComplaintPageDto =
        authClient.get("$baseUrl/api/v1/admin/complaints") {
            if (cursor != null) parameter("cursor", cursor)
            parameter("limit", limit)
            if (!q.isNullOrBlank()) parameter("q", q)
            if (fromDate != null) parameter("fromDate", fromDate)
            if (toDate != null) parameter("toDate", toDate)
            if (!reason.isNullOrBlank()) parameter("reason", reason)
            if (!reporter.isNullOrBlank()) parameter("reporter", reporter)
            if (!language.isNullOrBlank()) parameter("language", language)
        }.body()

    suspend fun hideArticle(articleId: Int, hidden: Boolean) {
        authClient.put("$baseUrl/api/v1/admin/articles/$articleId/hidden") {
            contentType(ContentType.Application.Json)
            setBody(HiddenRequest(hidden))
        }
    }

    suspend fun dismissComplaints(articleId: Int) {
        authClient.delete("$baseUrl/api/v1/admin/articles/$articleId/complaints")
    }

    suspend fun dismissComplaint(reportId: Int) {
        authClient.delete("$baseUrl/api/v1/admin/complaints/$reportId")
    }

    suspend fun stats(): StatsDto =
        authClient.get("$baseUrl/api/v1/admin/stats").body()

    suspend fun audit(cursor: String? = null, limit: Int = 30, actions: List<String>? = null): AuditPageDto =
        authClient.get("$baseUrl/api/v1/admin/audit") {
            if (cursor != null) parameter("cursor", cursor)
            parameter("limit", limit)
            if (!actions.isNullOrEmpty()) parameter("actions", actions.joinToString(","))
        }.body()

    suspend fun searchArticles(
        q: String? = null,
        fromDate: String? = null,
        toDate: String? = null,
        hidden: Boolean? = null,
        language: String? = null,
        region: String? = null,
        publisher: String? = null,
        category: String? = null,
        classified: Boolean? = null,
        hasComplaints: Boolean? = null,
        cursor: Int? = null,
        limit: Int = 20,
    ): AdminArticlePageDto =
        authClient.get("$baseUrl/api/v1/admin/articles") {
            if (!q.isNullOrBlank()) parameter("q", q)
            if (fromDate != null) parameter("fromDate", fromDate)
            if (toDate != null) parameter("toDate", toDate)
            if (hidden != null) parameter("hidden", hidden)
            if (!language.isNullOrBlank()) parameter("language", language)
            if (!region.isNullOrBlank()) parameter("region", region)
            if (!publisher.isNullOrBlank()) parameter("publisher", publisher)
            if (!category.isNullOrBlank()) parameter("category", category)
            if (classified != null) parameter("classified", classified)
            if (hasComplaints != null) parameter("hasComplaints", hasComplaints)
            if (cursor != null) parameter("cursor", cursor)
            parameter("limit", limit)
        }.body()

    suspend fun getArticleDetail(id: Int): AdminArticleDetailDto =
        authClient.get("$baseUrl/api/v1/admin/articles/$id").body()

    suspend fun getUsers(
        q: String? = null,
        fromDate: String? = null,
        toDate: String? = null,
        role: String? = null,
        language: String? = null,
        region: String? = null,
        hasComplaints: Boolean? = null,
        cursor: Int? = null,
        limit: Int = 20,
    ): AdminUserPageDto =
        authClient.get("$baseUrl/api/v1/admin/users") {
            if (!q.isNullOrBlank()) parameter("q", q)
            if (fromDate != null) parameter("fromDate", fromDate)
            if (toDate != null) parameter("toDate", toDate)
            if (!role.isNullOrBlank()) parameter("role", role)
            if (!language.isNullOrBlank()) parameter("language", language)
            if (!region.isNullOrBlank()) parameter("region", region)
            if (hasComplaints != null) parameter("hasComplaints", hasComplaints)
            if (cursor != null) parameter("cursor", cursor)
            parameter("limit", limit)
        }.body()

    suspend fun getUserDetail(id: Int): AdminUserDetailDto =
        authClient.get("$baseUrl/api/v1/admin/users/$id").body()

    suspend fun setUserRole(id: Int, role: RoleDto) {
        authClient.put("$baseUrl/api/v1/admin/users/$id/role") {
            contentType(ContentType.Application.Json)
            setBody(SetRoleRequest(role))
        }
    }
}
