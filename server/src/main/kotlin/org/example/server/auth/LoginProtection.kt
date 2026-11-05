package org.example.server.auth

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class LoginProtection {
    private data class AttemptRecord(val count: Int, val lastFailedAt: Long)

    private val attempts = ConcurrentHashMap<Pair<String, String>, AttemptRecord>()
    private val insertCount = AtomicInteger(0)

    companion object {
        private const val EVICT_AFTER_MS = 15L * 60 * 1000
        private const val MAX_ENTRIES = 10_000
        private const val EVICT_EVERY_N = 100
    }

    fun retryAfterSeconds(remoteHost: String, nick: String): Long? {
        val key = Pair(remoteHost, nick)
        val record = attempts[key] ?: return null
        // Evict stale entry on access
        if (System.currentTimeMillis() - record.lastFailedAt > EVICT_AFTER_MS) {
            attempts.remove(key)
            return null
        }
        val delayMs = minOf((1L shl minOf(record.count - 1, 6)) * 1000L, 60_000L)
        val elapsed = System.currentTimeMillis() - record.lastFailedAt
        val remaining = delayMs - elapsed
        return if (remaining > 0) (remaining + 999) / 1000 else null
    }

    fun recordFailure(remoteHost: String, nick: String) {
        val key = Pair(remoteHost, nick)
        val existing = attempts[key]
        attempts[key] = AttemptRecord((existing?.count ?: 0) + 1, System.currentTimeMillis())
        if (insertCount.incrementAndGet() % EVICT_EVERY_N == 0 || attempts.size > MAX_ENTRIES) {
            evictStale()
        }
    }

    fun recordSuccess(remoteHost: String, nick: String) {
        attempts.remove(Pair(remoteHost, nick))
    }

    private fun evictStale() {
        val cutoff = System.currentTimeMillis() - EVICT_AFTER_MS
        val it = attempts.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.lastFailedAt < cutoff) it.remove()
        }
        if (attempts.size > MAX_ENTRIES) {
            attempts.entries
                .sortedBy { it.value.lastFailedAt }
                .take(attempts.size - MAX_ENTRIES)
                .forEach { attempts.remove(it.key) }
        }
    }
}
