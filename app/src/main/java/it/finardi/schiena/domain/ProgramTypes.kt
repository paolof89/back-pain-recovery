package it.finardi.schiena.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SessionType { STRENGTH_A, STRENGTH_B, AEROBIC, PILATES, FREE, ACTIVE_REST }
@Serializable
enum class ExerciseKind { REPS, HOLD, DISTANCE, MOBILITY }
enum class SessionOutcome { DONE, MINIMAL, SKIPPED }
enum class SessionStatus { PENDING, GREEN, YELLOW, RED, UNVERIFIED }
enum class OfficeBreakAction { DONE, SNOOZED, SKIPPED }