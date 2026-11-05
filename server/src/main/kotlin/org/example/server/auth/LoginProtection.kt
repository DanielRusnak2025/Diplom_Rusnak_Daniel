package org.example.server.auth

import java.util.concurrent.ConcurrentHashMap

class LoginProtection {
    private data class AttemptRecord(val count: Int, val lastFailedAt: Long)

    private val attempts = ConcurrentHashMap<Pair<String, String>, AttemptRecord>()

    fun retryAfterSeconds(remoteHost: String, nick: String): Long? {
        val record = attempts[Pair(remoteHost, nick)] ?: return null
        val delayMs = minOf((1L shl minOf(record.count - 1, 6)) * 1000L, 60_000L)
        val elapsed = System.currentTimeMillis() - record.lastFailedAt
        val remaining = delayMs - elapsed
        return if (remaining > 0) (remaining + 999) / 1000 else null
    }

    fun recordFailure(remoteHost: String, nick: String) {
        val key = Pair(remoteHost, nick)
        val existing = attempts[key]
        attempts[key] = AttemptRecord((existing?.count ?: 0) + 1, System.currentTimeMillis())
    }

    fun recordSuccess(remoteHost: String, nick: String) {
        attempts.remove(Pair(remoteHost, nick))
    }
}
