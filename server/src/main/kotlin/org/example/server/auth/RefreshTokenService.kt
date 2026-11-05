package org.example.server.auth

import jakarta.persistence.LockModeType
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

    fun issue(session: Session, user: User, familyId: String = UUID.randomUUID().toString()): String {
        val rawBytes = ByteArray(32).also { rng.nextBytes(it) }
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes)
        val hash = sha256hex(raw)
        val token = RefreshToken().apply {
            this.user = user
            tokenHash = hash
            this.familyId = familyId
            expiresAt = LocalDateTime.now().plusDays(refreshTokenTtlDays)
        }
        session.persist(token)
        return raw
    }

    fun findByHash(session: Session, hash: String): RefreshToken? {
        val query = session.createSelectionQuery(
            "from RefreshToken where tokenHash = :hash", RefreshToken::class.java
        ).setParameter("hash", hash)
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE)
        return query.uniqueResult()
    }

    fun revoke(session: Session, token: RefreshToken) {
        token.revokedAt = LocalDateTime.now()
        session.merge(token)
    }

    fun revokeFamily(session: Session, familyId: String) {
        session.createMutationQuery(
            "update RefreshToken set revokedAt = :now where familyId = :fid and revokedAt is null"
        )
        .setParameter("now", LocalDateTime.now())
        .setParameter("fid", familyId)
        .executeUpdate()
    }

    fun revokeAllForUser(session: Session, userId: Int) {
        session.createMutationQuery(
            "update RefreshToken set revokedAt = :now where user.id = :uid and revokedAt is null"
        )
        .setParameter("now", LocalDateTime.now())
        .setParameter("uid", userId)
        .executeUpdate()
    }

    fun hasActiveFamilyToken(session: Session, familyId: String): Boolean =
        (session.createSelectionQuery(
            "select count(r) from RefreshToken r where r.familyId = :fid and r.revokedAt is null and r.expiresAt > :now",
            Long::class.java
        ).setParameter("fid", familyId).setParameter("now", LocalDateTime.now()).uniqueResult() ?: 0L) > 0L

    companion object {
        fun sha256hex(input: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
