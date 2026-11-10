package org.example.server.routes

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.example.Tag
import org.example.TagKind
import org.example.dto.CategoryDto
import org.example.dto.SubtopicDto
import org.example.server.util.withNewTransaction

fun Route.categoryRoutes() {
    authenticate("jwt-access") {
        get("/api/v1/categories") {
            val lang = call.request.headers["Accept-Language"]
                ?.let { if (it.startsWith("ru", ignoreCase = true)) "ru" else "en" } ?: "en"

            val categories = withNewTransaction { session ->
                val cats = session.createSelectionQuery(
                    "from Tag t where t.kind = :kind order by t.name",
                    Tag::class.java
                ).setParameter("kind", TagKind.CATEGORY).resultList

                val subtopics = session.createSelectionQuery(
                    "from Tag t join fetch t.parent where t.kind = :kind order by t.name",
                    Tag::class.java
                ).setParameter("kind", TagKind.SUBTOPIC).resultList

                val subtopicsByParent = subtopics.groupBy { it.parent?.id }

                fun localize(tag: Tag): String =
                    if (lang == "ru") tag.nameRu ?: tag.name else tag.name

                cats.map { cat ->
                    CategoryDto(
                        id = cat.id,
                        key = cat.tagKey ?: "",
                        name = localize(cat),
                        subtopics = subtopicsByParent[cat.id]?.map { sub ->
                            SubtopicDto(id = sub.id, key = sub.tagKey ?: "", name = localize(sub))
                        } ?: emptyList()
                    )
                }
            }
            call.respond(categories)
        }
    }
}
