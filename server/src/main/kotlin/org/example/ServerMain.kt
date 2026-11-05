package org.example

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.example.server.module
import java.io.File
import java.util.Properties

fun main() {
    val config = loadServerConfig()
    val port = config.getProperty("server.port", "8080").toInt()
    embeddedServer(Netty, port = port) {
        module()
    }.start(wait = true)
}

private fun loadServerConfig(): Properties {
    val props = Properties()
    val file = File("server/config/server.properties")
    if (file.exists()) {
        file.inputStream().use { props.load(it) }
    }
    return props
}
