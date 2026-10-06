package it.finardi.schiena.data

import it.finardi.schiena.domain.ExerciseKind
import it.finardi.schiena.domain.OfficeBreakAction
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionStatus
import it.finardi.schiena.domain.SessionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseConvertersTest {
    private val converters = DatabaseConverters()

    @Test
    fun dateTimeAndInstantRoundTripWithoutLosingPrecision() {
        val date = LocalDate.of(2028, 2, 29)
        val time = LocalTime.of(9, 0, 1, 123456789)
        val instant = Instant.parse("2026-10-06T09:00:00.123456789Z")
        assertEquals(date, converters.stringToDate(converters.dateToString(date)))
        assertEquals(time, converters.stringToTime(converters.timeToString(time)))
        assertEquals(instant, converters.stringToInstant(converters.instantToString(instant)))
    }

    @Test
    fun encodedInstantsSortChronologicallyIncludingFractionalSeconds() {
        val instants = listOf("2026-10-06T09:00:00Z", "2026-10-06T09:00:00.001Z",
            "2026-10-06T09:00:00.1Z", "2026-10-06T09:00:01Z").map(Instant::parse)
        assertEquals(instants, instants.reversed().map(converters::instantToString).sorted().map(converters::stringToInstant))
    }

    @Test
    fun cuesUseJsonAndPreserveQuotesNewlinesAndUnicode() {
        val cues = listOf("Mobility \u00e0", "Quote: \"hold\"", "Line one\nLine two", "Comma, semicolon; bracket[]", "")
        assertEquals(cues, converters.jsonToCues(converters.cuesToJson(cues)))
        assertEquals(emptyList<String>(), converters.jsonToCues(converters.cuesToJson(emptyList())))
    }

    @Test
    fun everyEnumAndIsoDayRoundTrips() {
        SessionType.values().forEach { assertEquals(it, converters.stringToSession(converters.sessionToString(it))) }
        ExerciseKind.values().forEach { assertEquals(it, converters.stringToKind(converters.kindToString(it))) }
        SessionOutcome.values().forEach { assertEquals(it, converters.stringToOutcome(converters.outcomeToString(it))) }
        SessionStatus.values().forEach { assertEquals(it, converters.stringToStatus(converters.statusToString(it))) }
        OfficeBreakAction.values().forEach { assertEquals(it, converters.stringToAction(converters.actionToString(it))) }
        DayOfWeek.values().forEach { assertEquals(it, converters.intToDay(converters.dayToInt(it))) }
        assertEquals((1..7).toList(), DayOfWeek.values().map(converters::dayToInt))
    }
}