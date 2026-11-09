package org.example.server.routes

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.example.Region
import org.example.ai.AiConfig
import org.example.dto.LanguageDto
import org.example.dto.RegionDto
import org.example.server.util.withNewTransaction

fun Route.metaRoutes() {
    authenticate("jwt-access") {
        get("/api/v1/regions") {
            val regions = withNewTransaction { session ->
                session.createSelectionQuery(
                    "from Region r order by r.regionKey",
                    Region::class.java
                ).resultList.map { r ->
                    RegionDto(key = r.regionKey, name = r.name)
                }
            }
            call.respond(regions)
        }

        get("/api/v1/languages") {
            val codes = AiConfig.getLanguagesAllowed()
            call.respond(codes.map { LanguageDto(code = it) })
        }
    }
}
