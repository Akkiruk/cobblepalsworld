package com.cobblepalsworld.behavior.phase

import com.cobblepalsworld.behavior.state.WorkerPhase

/**
 * Registry for phase-specific state handlers.
 * Adding a worker phase should only require creating a new handler and adding its map entry here.
 */
object PhaseRegistry {
    private val handlers: Map<WorkerPhase, PhaseHandler> = mapOf(
        WorkerPhase.IDLE to IdlePhaseHandler,
        WorkerPhase.NAVIGATING to NavigatingPhaseHandler,
        WorkerPhase.ARRIVING to ArrivingPhaseHandler,
        WorkerPhase.WORKING to WorkingPhaseHandler,
        WorkerPhase.DEPOSITING to DepositingPhaseHandler
    )

    fun handlerFor(phase: WorkerPhase): PhaseHandler = handlers.getValue(phase)
}
