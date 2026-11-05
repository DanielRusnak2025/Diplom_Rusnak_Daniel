package org.example.server

import org.example.util.HibernateUtil
import org.flywaydb.core.Flyway
import org.hibernate.cfg.Configuration
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.charset.StandardCharsets
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicBoolean

class ServerTestDb : BeforeAllCallback {

    override fun beforeAll(context: ExtensionContext) {
        if (!initialized.compareAndSet(false, true)) return

        val hCfg = Configuration().configure()
        val rawUrl = hCfg.getProperty("hibernate.connection.url")
        val user = hCfg.getProperty("hibernate.connection.username")
        val url = HibernateUtil.resolveUrl(rawUrl)
        require(url.contains("/db_use_test_it")) {
            "ServerTestDb must run against db_use_test_it; got: $url\n" +
                "Set -Dnewsdove.db.name=db_use_test_it in build.gradle.kts"
        }
        val pass = hCfg.getProperty("hibernate.connection.password")

        val flyway = Flyway.configure()
            .dataSource(url, user, pass)
            .baselineVersion("1")
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load()

        flyway.clean()
        executeBaselineSchema(url, user, pass)
        flyway.baseline()
        flyway.migrate()
    }

    private fun executeBaselineSchema(url: String, user: String, pass: String) {
        val sql = ServerTestDb::class.java
            .getResourceAsStream("/db/baseline_schema.sql")
            ?.use { it.readBytes().toString(StandardCharsets.UTF_8) }
            ?: error("baseline_schema.sql not found on classpath at /db/baseline_schema.sql")

        DriverManager.getConnection(url, user, pass).use { conn ->
            conn.createStatement().use { st ->
                for (stmt in sql.split(";")) {
                    val trimmed = stmt.trim()
                    if (trimmed.isNotEmpty()) {
                        st.execute(trimmed)
                    }
                }
            }
        }
    }

    companion object {
        private val initialized = AtomicBoolean(false)
    }
}
