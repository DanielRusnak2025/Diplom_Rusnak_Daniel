package org.example

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.example.server.config.ServerConfig
import org.example.server.module

fun main() {
    val config = ServerConfig.load()
    embeddedServer(Netty, port = config.port()) {
        module()
    }.start(wait = true)
}
