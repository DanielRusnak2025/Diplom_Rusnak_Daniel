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

    @Test
    fun `httpsPort returns configured value`() {
        val tmp = Files.createTempFile("server-config-test", ".properties").toFile()
        val prevPath = System.getProperty("newsdove.server.config")
        try {
            tmp.writeText("server.https.port=8443\n")
            System.setProperty("newsdove.server.config", tmp.absolutePath)
            assertEquals(8443, ServerConfig.load().httpsPort())
        } finally {
            if (prevPath != null) System.setProperty("newsdove.server.config", prevPath)
            else System.clearProperty("newsdove.server.config")
            tmp.delete()
        }
    }

    @Test
    fun `httpsPort returns null when not configured`() {
        val prevPath = System.getProperty("newsdove.server.config")
        System.setProperty("newsdove.server.config", "/nonexistent/path/server.properties")
        try {
            assertNull(ServerConfig.load().httpsPort())
        } finally {
            if (prevPath != null) System.setProperty("newsdove.server.config", prevPath)
            else System.clearProperty("newsdove.server.config")
        }
    }

    @Test
    fun `keystorePath returns configured value`() {
        val tmp = Files.createTempFile("server-config-test", ".properties").toFile()
        val prevPath = System.getProperty("newsdove.server.config")
        try {
            tmp.writeText("server.keystore.path=C:/newsdove_tls/newsdove.p12\n")
            System.setProperty("newsdove.server.config", tmp.absolutePath)
            assertEquals("C:/newsdove_tls/newsdove.p12", ServerConfig.load().keystorePath())
        } finally {
            if (prevPath != null) System.setProperty("newsdove.server.config", prevPath)
            else System.clearProperty("newsdove.server.config")
            tmp.delete()
        }
    }

    @Test
    fun `keystorePath returns null when not configured`() {
        val prevPath = System.getProperty("newsdove.server.config")
        System.setProperty("newsdove.server.config", "/nonexistent/path/server.properties")
        try {
            assertNull(ServerConfig.load().keystorePath())
        } finally {
            if (prevPath != null) System.setProperty("newsdove.server.config", prevPath)
            else System.clearProperty("newsdove.server.config")
        }
    }

    @Test
    fun `keystorePassword reads from properties when env not set`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            System.getenv("NEWSDOVE_KEYSTORE_PASSWORD") == null,
            "Skipped: NEWSDOVE_KEYSTORE_PASSWORD env var is set"
        )
        val tmp = Files.createTempFile("server-config-test", ".properties").toFile()
        val prevPath = System.getProperty("newsdove.server.config")
        try {
            tmp.writeText("server.keystore.password=test-props-pass\n")
            System.setProperty("newsdove.server.config", tmp.absolutePath)
            assertEquals("test-props-pass", ServerConfig.load().keystorePassword())
        } finally {
            if (prevPath != null) System.setProperty("newsdove.server.config", prevPath)
            else System.clearProperty("newsdove.server.config")
            tmp.delete()
        }
    }

    @Test
    fun `keystorePassword throws when not configured`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            System.getenv("NEWSDOVE_KEYSTORE_PASSWORD") == null,
            "Skipped: NEWSDOVE_KEYSTORE_PASSWORD env var is set"
        )
        val prevPath = System.getProperty("newsdove.server.config")
        System.setProperty("newsdove.server.config", "/nonexistent/path/server.properties")
        try {
            assertThrows(IllegalStateException::class.java) {
                ServerConfig.load().keystorePassword()
            }
        } finally {
            if (prevPath != null) System.setProperty("newsdove.server.config", prevPath)
            else System.clearProperty("newsdove.server.config")
        }
    }

    @Test
    fun `keyAlias returns default newsdove when not configured`() {
        val prevPath = System.getProperty("newsdove.server.config")
        System.setProperty("newsdove.server.config", "/nonexistent/path/server.properties")
        try {
            assertEquals("newsdove", ServerConfig.load().keyAlias())
        } finally {
            if (prevPath != null) System.setProperty("newsdove.server.config", prevPath)
            else System.clearProperty("newsdove.server.config")
        }
    }
}
