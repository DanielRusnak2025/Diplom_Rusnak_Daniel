package org.example.server.auth

import org.example.RefreshToken
import org.example.User
import org.hibernate.Session
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

class RefreshTokenService {
    private val rng = SecureRandom()
    private val refreshTokenTtlDays = 30L

    fun issue(session: Session, user: User): String {
        val rawBytes = ByteArray(32).also { rng.nextBytes(it) }
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes)
        val hash = sha256hex(raw)
        val token = RefreshToken().apply {
            this.user = user
            tokenHash = hash
            familyId = UUID.randomUUID().toString()
            expiresAt = LocalDateTime.now().plusDays(refreshTokenTtlDays)
        }
        session.persist(token)
        return raw
    }

    fun sha256hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
