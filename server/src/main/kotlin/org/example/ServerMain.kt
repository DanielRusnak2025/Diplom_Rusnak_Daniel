package org.example

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.example.server.config.ServerConfig
import org.example.server.module
import org.slf4j.LoggerFactory
import java.io.File
import java.security.KeyStore

private val log = LoggerFactory.getLogger("org.example.ServerMain")

fun main() {
    val config = ServerConfig.load()
    val httpsPort = config.httpsPort()
    val keystorePath = config.keystorePath()

    if (httpsPort != null && keystorePath != null) {
        val keystoreFile = File(keystorePath)
        require(keystoreFile.exists()) { "Keystore file not found: $keystorePath" }
        val keystorePass = config.keystorePassword()
        val keyStore = KeyStore.getInstance("PKCS12")
        keystoreFile.inputStream().use { keyStore.load(it, keystorePass.toCharArray()) }
        val alias = config.keyAlias()

        val httpPort = config.port()
        log.info("HTTPS enabled: SSL on port {}, plain HTTP on port {} (health only)", httpsPort, httpPort)
        embeddedServer(Netty, configure = {
            connector { port = httpPort }
            sslConnector(
                keyStore = keyStore,
                keyAlias = alias,
                keyStorePassword = { keystorePass.toCharArray() },
                privateKeyPassword = { keystorePass.toCharArray() }
            ) { port = httpsPort }
        }) {
            module(httpsEnabled = true)
        }.start(wait = true)
    } else {
        log.warn("HTTPS not configured (server.https.port or server.keystore.path missing) — running HTTP only on port {}", config.port())
        embeddedServer(Netty, port = config.port()) {
            module()
        }.start(wait = true)
    }
}
