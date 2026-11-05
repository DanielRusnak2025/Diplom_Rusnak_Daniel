package org.example.server

import org.example.server.config.ServerConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ServerConfigTest {

    @Test
    fun `loads port from temp properties file`() {
        val tmp = Files.createTempFile("server-config-test", ".properties").toFile()
        val prevPath = System.getProperty("newsdove.server.config")
        try {
            tmp.writeText("server.port=9090\n")
            System.setProperty("newsdove.server.config", tmp.absolutePath)
            assertEquals(9090, ServerConfig.load().port())
        } finally {
            if (prevPath != null) System.setProperty("newsdove.server.config", prevPath)
            else System.clearProperty("newsdove.server.config")
            tmp.delete()
        }
    }

    @Test
    fun `returns default port 8080 when config file missing`() {
        val prevPath = System.getProperty("newsdove.server.config")
        System.setProperty("newsdove.server.config", "/nonexistent/path/server.properties")
        try {
            assertEquals(8080, ServerConfig.load().port())
        } finally {
            if (prevPath != null) System.setProperty("newsdove.server.config", prevPath)
            else System.clearProperty("newsdove.server.config")
        }
    }

    @Test
    fun `jwtSecret reads from system property`() {
        val prevSecret = System.getProperty("newsdove.jwt.secret")
        val testSecret = "config-test-secret-32-bytes-long!!"
        System.setProperty("newsdove.jwt.secret", testSecret)
        try {
            assertEquals(testSecret, ServerConfig.load().jwtSecret())
        } finally {
            if (prevSecret != null) System.setProperty("newsdove.jwt.secret", prevSecret)
            else System.clearProperty("newsdove.jwt.secret")
        }
    }

    @Test
    fun `jwtSecret throws when secret is too short`() {
        val prevSecret = System.getProperty("newsdove.jwt.secret")
        System.setProperty("newsdove.jwt.secret", "tooshort")
        try {
            assertThrows(IllegalArgumentException::class.java) {
                ServerConfig.load().jwtSecret()
            }
        } finally {
            if (prevSecret != null) System.setProperty("newsdove.jwt.secret", prevSecret)
            else System.clearProperty("newsdove.jwt.secret")
        }
    }
}
