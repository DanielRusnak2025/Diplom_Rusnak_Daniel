package org.example.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import org.example.Role
import java.util.Date
import java.util.UUID

class JwtConfig(secret: String) {
    private val algorithm = Algorithm.HMAC256(secret)
    private val issuer = "newsdove"
    private val accessTokenTtlMs = 15L * 60 * 1000

    fun issueAccessToken(userId: Int, role: Role): String =
        JWT.create()
            .withIssuer(issuer)
            .withSubject(userId.toString())
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("role", role.name)
            .withExpiresAt(Date(System.currentTimeMillis() + accessTokenTtlMs))
            .sign(algorithm)

    fun verifier(): JWTVerifier =
        JWT.require(algorithm)
            .withIssuer(issuer)
            .build()
}
