package org.example.server.routes

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.example.Region
import org.example.ai.AiConfig
import org.example.dto.LanguageDto
import org.example.dto.RegionDto
import org.example.server.util.withNewTransaction

private val LANGUAGE_NAMES_EN = mapOf(
    "en" to "English",
    "ru" to "Russian",
    "de" to "German",
    "ro" to "Romanian",
    "zh" to "Chinese",
    "fr" to "French",
    "es" to "Spanish",
    "uk" to "Ukrainian",
)

private val LANGUAGE_NAMES_RU = mapOf(
    "en" to "Английский",
    "ru" to "Русский",
    "de" to "Немецкий",
    "ro" to "Румынский",
    "zh" to "Китайский",
    "fr" to "Французский",
    "es" to "Испанский",
    "uk" to "Украинский",
)

fun Route.metaRoutes() {
    authenticate("jwt-access") {
        get("/api/v1/regions") {
            val regions = withNewTransaction { session ->
                session.createSelectionQuery(
                    "from Region r order by r.regionKey",
                    Region::class.java
                ).resultList.map { r ->
                    RegionDto(key = r.regionKey, name = r.name, nameRu = r.nameRu)
                }
            }
            call.respond(regions)
        }

        get("/api/v1/languages") {
            val codes = AiConfig.getLanguagesAllowed()
            call.respond(codes.map { code -> LanguageDto(code = code, name = LANGUAGE_NAMES_RU[code]) })
        }

        get("/api/v1/language-names") {
            val lang = call.request.queryParameters["lang"] ?: "ru"
            val names = if (lang == "en") LANGUAGE_NAMES_EN else LANGUAGE_NAMES_RU
            call.respond(names)
        }
    }
}
