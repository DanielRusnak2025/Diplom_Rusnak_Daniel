package org.example.server.util

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.Role
import org.example.User
import org.example.dto.ErrorResponse
import org.example.util.HibernateUtil

suspend fun ApplicationCall.requireRoleFromDb(vararg roles: Role): User? {
    val userId = principal<JWTPrincipal>()?.payload?.subject?.toIntOrNull()
        ?: run { respond(HttpStatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "Authentication required")); return null }
    val user = withContext(Dispatchers.IO) {
        HibernateUtil.getSessionFactory().openSession().use { it.find(User::class.java, userId) }
    }
    return when {
        user == null -> { respond(HttpStatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "User not found")); null }
        user.role !in roles -> { respond(HttpStatusCode.Forbidden, ErrorResponse("FORBIDDEN", "Insufficient role")); null }
        else -> user
    }
}
