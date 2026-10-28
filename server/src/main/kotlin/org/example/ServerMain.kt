package org.example

import kotlinx.serialization.json.Json
import org.example.dto.HealthDto

fun main() {
    println("server ok")
    println(Json.encodeToString(HealthDto("ok")))
}
