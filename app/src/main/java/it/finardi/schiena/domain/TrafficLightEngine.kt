package it.finardi.schiena.domain

object TrafficLightEngine {
    fun provisional(outcome: SessionOutcome, painDuring: Int?, radiating: Boolean): SessionStatus {
        if (outcome == SessionOutcome.SKIPPED) return SessionStatus.UNVERIFIED
        require(painDuring != null && painDuring in 0..10)
        return when {
            radiating || painDuring > 5 -> SessionStatus.RED
            painDuring >= 4 -> SessionStatus.YELLOW
            else -> SessionStatus.PENDING
        }
    }

    fun verified(provisional: SessionStatus, backToBaseline: Boolean?, expired: Boolean): SessionStatus = when {
        backToBaseline == null -> if (expired) SessionStatus.UNVERIFIED else provisional
        provisional == SessionStatus.RED -> SessionStatus.RED
        provisional == SessionStatus.YELLOW || !backToBaseline -> SessionStatus.YELLOW
        else -> SessionStatus.GREEN
    }
}