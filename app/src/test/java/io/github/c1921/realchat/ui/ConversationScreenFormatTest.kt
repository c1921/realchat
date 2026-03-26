package io.github.c1921.realchat.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ConversationScreenFormatTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun formatConversationTimestamp_returnsTimeForSameDay() {
        val now = Instant.parse("2026-03-26T12:00:00Z")
        val updatedAt = Instant.parse("2026-03-26T03:15:00Z").toEpochMilli()

        val result = formatConversationTimestamp(
            updatedAtMillis = updatedAt,
            zoneId = zoneId,
            now = now
        )

        assertEquals("11:15", result)
    }

    @Test
    fun formatConversationTimestamp_returnsMonthDayForDifferentDay() {
        val now = Instant.parse("2026-03-26T12:00:00Z")
        val updatedAt = Instant.parse("2026-03-24T20:30:00Z").toEpochMilli()

        val result = formatConversationTimestamp(
            updatedAtMillis = updatedAt,
            zoneId = zoneId,
            now = now
        )

        assertEquals("03-25", result)
    }

    @Test
    fun formatConversationTimestamp_returnsEmptyForNonPositiveTimestamp() {
        val result = formatConversationTimestamp(
            updatedAtMillis = 0L,
            zoneId = zoneId,
            now = Instant.parse("2026-03-26T12:00:00Z")
        )

        assertEquals("", result)
    }
}
