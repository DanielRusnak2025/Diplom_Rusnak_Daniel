package org.example.server.config

import org.slf4j.LoggerFactory
import java.io.File
import java.util.Properties

class ServerConfig private constructor(private val props: Properties) {

    fun port(): Int = props.getProperty("server.port", "8080").toInt()

    fun jwtSecret(): String {
        val secret = System.getProperty("newsdove.jwt.secret")?.takeIf { it.isNotEmpty() }
            ?: System.getenv("NEWSDOVE_JWT_SECRET")?.takeIf { it.isNotEmpty() }
            ?: props.getProperty("jwt.secret")?.takeIf { it.isNotEmpty() }
            ?: error(
                "JWT secret not configured. " +
                "Set -Dnewsdove.jwt.secret, env NEWSDOVE_JWT_SECRET, " +
                "or jwt.secret in server.properties"
            )
        require(secret.toByteArray(Charsets.UTF_8).size >= 32) {
            "JWT secret must be at least 32 bytes"
        }
        return secret
    }

    companion object {
        private val log = LoggerFactory.getLogger(ServerConfig::class.java)

        fun load(): ServerConfig {
            val props = Properties()
            val path = System.getProperty("newsdove.server.config")
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    file.inputStream().use { props.load(it) }
                    log.info("Loaded server config from {}", path)
                } else {
                    log.warn("Server config file not found at {} — using defaults/env vars", path)
                }
            } else {
                log.warn("System property newsdove.server.config not set — using defaults/env vars only")
            }
            return ServerConfig(props)
        }
    }
}
