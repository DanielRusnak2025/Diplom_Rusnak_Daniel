package org.example.dto

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DtoEnumTest {

    @Test
    fun unknownReportReasonDtoValueThrows() {
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString<ReportReasonDto>("\"DEFINITELY_UNKNOWN_VALUE\"")
        }
    }

    @Test
    fun unknownBlockTypeDtoValueThrows() {
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString<BlockTypeDto>("\"DEFINITELY_UNKNOWN_VALUE\"")
        }
    }

    @Test
    fun unknownRoleDtoValueThrows() {
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString<RoleDto>("\"DEFINITELY_UNKNOWN_VALUE\"")
        }
    }

    @Test
    fun knownReportReasonRoundTrips() {
        val encoded = Json.encodeToString(ReportReasonDto.serializer(), ReportReasonDto.OTHER)
        val decoded = Json.decodeFromString<ReportReasonDto>(encoded)
        assert(decoded == ReportReasonDto.OTHER)
    }

    @Test
    fun feedPageDtoRoundTrip() {
        val dto = FeedPageDto(
            items = listOf(
                FeedArticleDto(
                    id = 1, title = "Test Article", link = "https://example.com/1",
                    publisher = "Example", publicationDate = "2024-11-01",
                    language = "en", region = null,
                    mainCategory = TagRefDto(id = 10, key = "technology", name = "Technology", percent = 80),
                    subtopic = null,
                    myRating = 1
                )
            ),
            nextCursor = "abc123"
        )
        val json = Json.encodeToString(dto)
        val decoded = Json.decodeFromString<FeedPageDto>(json)
        assertEquals(dto, decoded)
    }

    @Test
    fun complaintRequestRoundTrip() {
        val dto = ComplaintRequest(articleId = 5, reason = ReportReasonDto.NOT_INTERESTED_TOPIC)
        val json = Json.encodeToString(dto)
        val decoded = Json.decodeFromString<ComplaintRequest>(json)
        assertEquals(dto, decoded)
    }
}
