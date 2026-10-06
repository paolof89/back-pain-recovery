package it.finardi.schiena.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import it.finardi.schiena.domain.ExerciseKind
import it.finardi.schiena.domain.SessionType
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class ProgramSeed(
    val version: Int,
    val exercises: List<SeedExercise>,
    val phases: List<SeedPhase>,
    val minimalSession: List<SeedPrescription>,
    val weekTemplate: List<SeedPlanEntry>,
    val defaults: SeedDefaults,
)

@Serializable
data class SeedExercise(
    val id: String,
    val name: String,
    val goal: String,
    val cues: List<String>,
    val kind: ExerciseKind,
    val perSide: Boolean,
)

@Serializable
data class SeedPhase(
    val id: Int,
    val name: String,
    val minWeeks: Int,
    val description: String = "",
    val sessions: Map<SessionType, List<SeedPrescription>> = emptyMap(),
    val inheritSessionsFromPhase: Int? = null,
    val returnToSport: JsonObject? = null,
)

@Serializable
data class SeedPrescription(
    val exerciseId: String,
    val sets: Int,
    val reps: Int? = null,
    val holdSec: Int? = null,
    val distanceM: Int? = null,
    val restSec: Int,
    val enabled: Boolean = true,
)

@Serializable
data class SeedPlanEntry(val day: String, val sessionType: SessionType, val reminderTime: String)

@Serializable
data class SeedDefaults(
    val weeklyTarget: Int,
    val painCheckTime: String,
    val officeBreak: SeedOfficeBreak,
    val officeBreakRoutine: List<String>,
)

@Serializable
data class SeedOfficeBreak(val days: List<String>, val start: String, val end: String, val intervalMin: Int)

fun interface SeedSource {
    suspend fun load(): ProgramSeed
}

class AssetSeedSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : SeedSource {
    override suspend fun load(): ProgramSeed = withContext(Dispatchers.IO) {
        context.assets.open("seed/program_seed.json").bufferedReader(Charsets.UTF_8).use {
            Json.decodeFromString<ProgramSeed>(it.readText())
        }
    }
}