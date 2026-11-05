package org.example.server.service

import jakarta.persistence.Tuple
import org.example.Tag
import org.example.dto.FeedArticleDto
import org.example.dto.FeedPageDto
import org.example.dto.TagRefDto
import org.hibernate.Session
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64

class FeedService {

    private data class ParsedCursor(val score: Long, val effDateMillis: Long, val id: Int)

    private data class WhereResult(val conditions: List<String>, val params: Map<String, Any>)

    fun getFeed(
        session: Session,
        userId: Int,
        userLanguages: Set<String>,
        cursor: String?,
        limit: Int,
        regionFilter: String?,
        categoryFilter: Int?
    ): FeedPageDto {
        val parsedCursor = cursor?.let {
            try { decodeCursor(it) } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor")
            }
        }

        val where = buildFeedWhere(userLanguages, regionFilter, categoryFilter)
        val havingSql = if (parsedCursor != null)
            "HAVING score < :cursor_score OR (score = :cursor_score AND eff_date < :cursor_date) OR (score = :cursor_score AND eff_date = :cursor_date AND a.id < :cursor_id)"
        else ""

        val sql = buildSelectSql(where.conditions, havingSql, ordered = true, limitClause = true)

        @Suppress("UNCHECKED_CAST")
        val query = session.createNativeQuery(sql, Tuple::class.java)
        query.setParameter("uid", userId)
        query.setParameter("lim", limit)
        where.params.forEach { (k, v) -> query.setParameter(k, v) }
        if (parsedCursor != null) {
            query.setParameter("cursor_score", parsedCursor.score)
            query.setParameter("cursor_date",
                LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(parsedCursor.effDateMillis),
                    ZoneId.systemDefault()
                )
            )
            query.setParameter("cursor_id", parsedCursor.id)
        }

        val rows = query.resultList
        val items = buildDtos(session, rows)

        val nextCursor = if (rows.size < limit) null else {
            val last = rows.last()
            val effDate = last.get("eff_date") as LocalDateTime
            encodeCursor(
                score = (last.get("score") as Number).toLong(),
                effDateMillis = effDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                id = (last.get("id") as Number).toInt()
            )
        }
        return FeedPageDto(items = items, nextCursor = nextCursor)
    }

    fun getArticleById(session: Session, userId: Int, articleId: Int): FeedArticleDto? {
        val sql = buildSelectSql(
            conditions = listOf("a.id = :article_id", "a.prompt_version IS NOT NULL"),
            havingSql = "",
            ordered = false,
            limitClause = false
        )
        @Suppress("UNCHECKED_CAST")
        val query = session.createNativeQuery(sql, Tuple::class.java)
        query.setParameter("uid", userId)
        query.setParameter("article_id", articleId)
        val rows = query.resultList
        return if (rows.isEmpty()) null else buildDtos(session, rows).first()
    }

    private fun buildSelectSql(
        conditions: List<String>,
        havingSql: String,
        ordered: Boolean,
        limitClause: Boolean
    ): String {
        val whereSql = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
        val orderSql = if (ordered) "ORDER BY score DESC, eff_date DESC, a.id DESC" else ""
        val limSql = if (limitClause) "LIMIT :lim" else ""
        return """
            SELECT
                a.id,
                a.title,
                a.link,
                a.publisher,
                a.publication_date,
                a.created_at,
                COALESCE(a.publication_date, a.created_at) AS eff_date,
                a.article_language,
                a.article_region,
                COALESCE(SUM(atp.percent * utp.percent), 0) AS score,
                (SELECT atp_c.tag_id FROM article_tag_percent atp_c
                 INNER JOIN tag t_c ON t_c.id = atp_c.tag_id AND t_c.kind = 'CATEGORY'
                 WHERE atp_c.article_id = a.id ORDER BY atp_c.percent DESC LIMIT 1) AS cat_tag_id,
                (SELECT atp_c.percent FROM article_tag_percent atp_c
                 INNER JOIN tag t_c ON t_c.id = atp_c.tag_id AND t_c.kind = 'CATEGORY'
                 WHERE atp_c.article_id = a.id ORDER BY atp_c.percent DESC LIMIT 1) AS cat_percent,
                (SELECT atp_s.tag_id FROM article_tag_percent atp_s
                 INNER JOIN tag t_s ON t_s.id = atp_s.tag_id AND t_s.kind = 'SUBTOPIC'
                 WHERE atp_s.article_id = a.id ORDER BY atp_s.percent DESC LIMIT 1) AS sub_tag_id,
                (SELECT atp_s.percent FROM article_tag_percent atp_s
                 INNER JOIN tag t_s ON t_s.id = atp_s.tag_id AND t_s.kind = 'SUBTOPIC'
                 WHERE atp_s.article_id = a.id ORDER BY atp_s.percent DESC LIMIT 1) AS sub_percent,
                (SELECT r.rating FROM report r
                 WHERE r.article_id = a.id AND r.user_id = :uid LIMIT 1) AS my_rating
            FROM article a
            LEFT JOIN article_tag_percent atp ON atp.article_id = a.id
            LEFT JOIN user_tag_percent utp ON utp.tag_id = atp.tag_id AND utp.user_id = :uid
            $whereSql
            GROUP BY a.id, a.title, a.link, a.publisher, a.publication_date, a.created_at, a.article_language, a.article_region
            $havingSql
            $orderSql
            $limSql
        """.trimIndent()
    }

    private fun buildFeedWhere(
        languages: Set<String>,
        region: String?,
        catId: Int?
    ): WhereResult {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()

        conditions.add("a.prompt_version IS NOT NULL")
        conditions.add(
            "NOT EXISTS (SELECT 1 FROM report r WHERE r.article_id = a.id AND r.user_id = :uid AND r.rating = -1)"
        )
        conditions.add(
            "NOT EXISTS (SELECT 1 FROM user_block ub WHERE ub.user_id = :uid AND ub.block_type = 'TAG' " +
            "AND ub.tag_id IN (SELECT atp2.tag_id FROM article_tag_percent atp2 WHERE atp2.article_id = a.id))"
        )
        conditions.add(
            "NOT EXISTS (SELECT 1 FROM user_block ub WHERE ub.user_id = :uid AND ub.block_type = 'REGION' AND ub.region_key = a.article_region)"
        )
        conditions.add(
            "NOT EXISTS (SELECT 1 FROM user_block ub WHERE ub.user_id = :uid AND ub.block_type = 'PUBLISHER' AND ub.publisher = a.publisher)"
        )

        if (languages.isNotEmpty()) {
            val langEntries = languages.mapIndexed { i, l -> "lang_$i" to l }
            conditions.add("a.article_language IN (${langEntries.joinToString(",") { ":${it.first}" }})")
            langEntries.forEach { (k, v) -> params[k] = v }
        }

        if (catId != null) {
            conditions.add(
                "EXISTS (SELECT 1 FROM article_tag_percent atp_c WHERE atp_c.article_id = a.id AND atp_c.tag_id = :cat_id)"
            )
            params["cat_id"] = catId
        }

        if (region != null) {
            conditions.add("a.article_region = :region_filter")
            params["region_filter"] = region
        }

        return WhereResult(conditions, params)
    }

    private fun buildDtos(session: Session, rows: List<Tuple>): List<FeedArticleDto> {
        if (rows.isEmpty()) return emptyList()

        val tagIds = rows.flatMap { t ->
            listOfNotNull(
                (t.get("cat_tag_id") as Number?)?.toInt(),
                (t.get("sub_tag_id") as Number?)?.toInt()
            )
        }.distinct()

        val tagMap: Map<Int, Tag> = if (tagIds.isNotEmpty()) {
            session.createSelectionQuery("from Tag t where t.id in :ids", Tag::class.java)
                .setParameter("ids", tagIds)
                .resultList
                .associateBy { it.id }
        } else emptyMap()

        return rows.map { t ->
            val pubDate = t.get("publication_date") as LocalDate?
            val createdAt = t.get("created_at") as LocalDateTime?
            val publicationDate = when {
                pubDate != null -> pubDate.toString()
                createdAt != null -> createdAt.toLocalDate().toString()
                else -> null
            }

            val catTagId = (t.get("cat_tag_id") as Number?)?.toInt()
            val catPercent = (t.get("cat_percent") as Number?)?.toInt()
            val subTagId = (t.get("sub_tag_id") as Number?)?.toInt()
            val subPercent = (t.get("sub_percent") as Number?)?.toInt()

            val mainCategory = if (catTagId != null && catPercent != null) {
                tagMap[catTagId]?.let { tag ->
                    TagRefDto(id = catTagId, key = tag.tagKey ?: "", name = tag.name ?: "", percent = catPercent)
                }
            } else null

            val subtopic = if (subTagId != null && subPercent != null) {
                tagMap[subTagId]?.let { tag ->
                    TagRefDto(id = subTagId, key = tag.tagKey ?: "", name = tag.name ?: "", percent = subPercent)
                }
            } else null

            FeedArticleDto(
                id = (t.get("id") as Number).toInt(),
                title = t.get("title") as String?,
                link = t.get("link") as String?,
                publisher = t.get("publisher") as String?,
                publicationDate = publicationDate,
                language = t.get("article_language") as String?,
                region = t.get("article_region") as String?,
                mainCategory = mainCategory,
                subtopic = subtopic,
                myRating = (t.get("my_rating") as Number?)?.toInt()
            )
        }
    }

    private fun encodeCursor(score: Long, effDateMillis: Long, id: Int): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$score|$effDateMillis|$id".toByteArray(Charsets.UTF_8))

    private fun decodeCursor(cursor: String): ParsedCursor {
        val decoded = try {
            Base64.getUrlDecoder().decode(cursor).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("bad cursor encoding")
        }
        val parts = decoded.split("|")
        if (parts.size != 3) throw IllegalArgumentException("bad cursor format")
        val score = parts[0].toLongOrNull() ?: throw IllegalArgumentException("bad cursor score")
        val millis = parts[1].toLongOrNull() ?: throw IllegalArgumentException("bad cursor date")
        val id = parts[2].toIntOrNull() ?: throw IllegalArgumentException("bad cursor id")
        return ParsedCursor(score, millis, id)
    }
}
