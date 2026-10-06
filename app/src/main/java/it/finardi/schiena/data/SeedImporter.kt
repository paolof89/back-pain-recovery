package it.finardi.schiena.data

import androidx.room.withTransaction
import it.finardi.schiena.domain.SessionType
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class SeedImporter @Inject constructor(
    private val database: AppDatabase,
    private val clock: Clock,
) {
    suspend fun initialize(seed: ProgramSeed) = import(seed, restore = false)

    suspend fun restoreDefaults(seed: ProgramSeed) = import(seed, restore = true)

    private suspend fun import(seed: ProgramSeed, restore: Boolean) {
        database.withTransaction {
            val dao = database.programDao()
            if (!restore && dao.getMetadata(IMPORT_MARKER) != null) {
                if (dao.getState() == null) {
                    dao.upsertState(ProgramState(1, LocalDate.now(clock), seed.defaults.weeklyTarget))
                }
                return@withTransaction
            }
            val content = resolve(seed)
            dao.clearPhaseExercises()
            dao.clearMinimalSession()
            dao.clearReturnToSport()
            dao.clearOfficeRoutine()
            dao.clearPlan()
            dao.upsertExercises(seed.exercises.map {
                Exercise(it.id, it.name, it.goal, it.cues, it.kind, it.perSide)
            })
            dao.upsertPhases(seed.phases.map { Phase(it.id, it.name, it.minWeeks, it.description) })
            dao.upsertPhaseExercises(content)
            dao.upsertMinimalSession(seed.minimalSession.mapIndexed { order, prescription ->
                MinimalSessionExercise(
                    order, prescription.exerciseId, prescription.sets, prescription.reps,
                    prescription.holdSec, prescription.distanceM, prescription.restSec, prescription.enabled,
                )
            })
            dao.upsertReturnToSport(seed.phases.mapNotNull { phase ->
                phase.returnToSport?.let { ReturnToSport(phase.id, it.toString()) }
            })
            dao.upsertOfficeRoutine(seed.defaults.officeBreakRoutine.mapIndexed { order, instruction ->
                OfficeBreakRoutine(order, instruction)
            })
            dao.upsertPlan(seed.weekTemplate.map {
                WeekPlanEntry(DayOfWeek.valueOf(it.day), it.sessionType, LocalTime.parse(it.reminderTime))
            })
            dao.removeNonSeedExercises(seed.exercises.map { it.id })
            dao.removeUnreferencedNonSeedPhases(seed.phases.map { it.id })
            if (!restore && dao.getState() == null) {
                dao.upsertState(ProgramState(1, LocalDate.now(clock), seed.defaults.weeklyTarget))
            }
            dao.upsertMetadata(ProgramMetadata(IMPORT_MARKER, seed.version.toString()))
        }
    }

    private fun resolve(seed: ProgramSeed): List<PhaseExercise> {
        require(seed.version == 1) { "Unsupported seed version: ${seed.version}" }
        require(seed.exercises.isNotEmpty() && seed.exercises.map { it.id }.distinct().size == seed.exercises.size)
        require(seed.phases.isNotEmpty() && seed.phases.map { it.id }.distinct().size == seed.phases.size)
        require(seed.phases.any { it.id == 1 }) { "The initial phase must exist" }
        require(seed.defaults.weeklyTarget in 1..7)
        Settings.fromSeed(seed.defaults)
        val days = seed.weekTemplate.map { DayOfWeek.valueOf(it.day) }
        require(days.size == 7 && days.toSet() == DayOfWeek.values().toSet()) { "A plan must contain each ISO day exactly once" }
        seed.weekTemplate.forEach { LocalTime.parse(it.reminderTime) }
        require(seed.minimalSession.isNotEmpty())
        val exerciseIds = seed.exercises.map { it.id }.toSet()
        seed.exercises.forEach { require(it.id.isNotBlank() && it.name.isNotBlank()) }
        val phases = seed.phases.associateBy { it.id }
        val resolved = mutableMapOf<Int, Map<SessionType, List<SeedPrescription>>>()
        val visiting = mutableSetOf<Int>()

        fun validate(prescription: SeedPrescription) {
            require(prescription.exerciseId in exerciseIds) { "Unknown exercise: ${prescription.exerciseId}" }
            require(prescription.sets > 0 && prescription.restSec >= 0)
            require(prescription.reps == null || prescription.reps > 0)
            require(prescription.holdSec == null || prescription.holdSec > 0)
            require(prescription.distanceM == null || prescription.distanceM > 0)
            require(prescription.reps != null || prescription.holdSec != null || prescription.distanceM != null)
        }

        fun sessions(phaseId: Int): Map<SessionType, List<SeedPrescription>> {
            resolved[phaseId]?.let { return it }
            require(visiting.add(phaseId)) { "Cyclic phase inheritance at $phaseId" }
            val phase = requireNotNull(phases[phaseId]) { "Unknown inherited phase: $phaseId" }
            require(phase.id > 0 && phase.name.isNotBlank() && phase.minWeeks > 0)
            val inherited = phase.inheritSessionsFromPhase?.let { sessions(it) } ?: emptyMap()
            val result = inherited + phase.sessions
            require(result.isNotEmpty() && result.values.all { it.isNotEmpty() })
            result.values.flatten().forEach(::validate)
            visiting.remove(phaseId)
            resolved[phaseId] = result
            return result
        }

        seed.minimalSession.forEach(::validate)
        return seed.phases.flatMap { phase ->
            sessions(phase.id).flatMap { (sessionType, prescriptions) ->
                prescriptions.mapIndexed { order, prescription ->
                    PhaseExercise(
                        phase.id, sessionType, order, prescription.exerciseId, prescription.sets,
                        prescription.reps, prescription.holdSec, prescription.distanceM,
                        prescription.restSec, prescription.enabled,
                    )
                }
            }
        }
    }

    companion object {
        const val IMPORT_MARKER = "program_seed_imported_version"
    }
}