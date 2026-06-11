package com.cobblepalsworld.behavior.phase

fun interface PhaseHandler {
    fun tick(ctx: PhaseContext)
}
