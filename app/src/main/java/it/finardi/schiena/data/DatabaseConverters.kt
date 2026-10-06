package it.finardi.schiena.data

import androidx.room.TypeConverter
import it.finardi.schiena.domain.ExerciseKind
import it.finardi.schiena.domain.OfficeBreakAction
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionStatus
import it.finardi.schiena.domain.SessionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatterBuilder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DatabaseConverters {
    private val instantFormat = DateTimeFormatterBuilder().appendInstant(9).toFormatter()

    @TypeConverter fun dateToString(value: LocalDate): String = value.toString()
    @TypeConverter fun stringToDate(value: String): LocalDate = LocalDate.parse(value)
    @TypeConverter fun timeToString(value: LocalTime): String = value.toString()
    @TypeConverter fun stringToTime(value: String): LocalTime = LocalTime.parse(value)
    @TypeConverter fun instantToString(value: Instant): String = instantFormat.format(value)
    @TypeConverter fun stringToInstant(value: String): Instant = Instant.parse(value)
    @TypeConverter fun dayToInt(value: DayOfWeek): Int = value.value
    @TypeConverter fun intToDay(value: Int): DayOfWeek = DayOfWeek.of(value)
    @TypeConverter fun cuesToJson(value: List<String>): String = Json.encodeToString(value)
    @TypeConverter fun jsonToCues(value: String): List<String> = Json.decodeFromString(value)
    @TypeConverter fun sessionToString(value: SessionType): String = value.name
    @TypeConverter fun stringToSession(value: String): SessionType = SessionType.valueOf(value)
    @TypeConverter fun kindToString(value: ExerciseKind): String = value.name
    @TypeConverter fun stringToKind(value: String): ExerciseKind = ExerciseKind.valueOf(value)
    @TypeConverter fun outcomeToString(value: SessionOutcome): String = value.name
    @TypeConverter fun stringToOutcome(value: String): SessionOutcome = SessionOutcome.valueOf(value)
    @TypeConverter fun statusToString(value: SessionStatus): String = value.name
    @TypeConverter fun stringToStatus(value: String): SessionStatus = SessionStatus.valueOf(value)
    @TypeConverter fun actionToString(value: OfficeBreakAction): String = value.name
    @TypeConverter fun stringToAction(value: String): OfficeBreakAction = OfficeBreakAction.valueOf(value)
}