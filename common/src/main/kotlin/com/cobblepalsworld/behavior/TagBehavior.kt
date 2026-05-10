package com.cobblepalsworld.behavior

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

interface TagBehavior {
    val tagType: TagType
    val defaultRange: Int

    /**
     * True for dual-phase behaviors that extract items for their OWN use (Sender, Placer,
        * Activator, Distributor). When true, the engine won't force-deposit items
     * at IDLE — instead it lets findTarget decide the next step.
     */
    val handlesOwnInventory: Boolean get() = false

    fun findTarget(world: World, origin: BlockPos, entity: PokemonEntity, tag: TagInstance, state: WorkerState): BlockPos?

    fun doWork(world: World, entity: PokemonEntity, target: BlockPos, tag: TagInstance, state: WorkerState): WorkResult

    fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean

    /**
     * Per-tag arrival tolerance.
     * Mirrors the Cobbleworkers idea that jobs, not the engine, define how close
     * a worker needs to be before it can act.
     */
    fun arrivalTolerance(tag: TagInstance, state: WorkerState): Double = 3.0

    /**
     * Per-tag arrival pacing.
     * Inspired by Cobbleworkers' job-owned arrival style: the engine owns the phase,
     * but the behavior decides how long it wants to wait before working.
     */
    fun arrivalDelayTicks(tag: TagInstance, state: WorkerState): Long =
        TagExecutionEngine.defaultArrivalDelayTicks()

    /**
     * Per-tag cooldown pacing.
     * Keeps the shared state machine generic while letting tags define their own rhythm.
     */
    fun cooldownTicks(tag: TagInstance, state: WorkerState): Long =
        TagExecutionEngine.defaultCooldownTicks(tag, state)

    /** How long an idle worker should wait before retrying target discovery after a miss. */
    fun idleRetryTicks(tag: TagInstance, state: WorkerState): Long =
        TagExecutionEngine.defaultIdleRetryTicks()

    /** Config range + Range augment bonus. Uses WorkerState cache. */
    fun effectiveRange(tag: TagInstance, state: WorkerState): Int =
        TagExecutionEngine.effectiveRange(tag, state)

    /** Config max items + Stack augment bonus. Uses WorkerState cache. */
    fun effectiveMaxItems(tag: TagInstance, state: WorkerState): Int =
        TagExecutionEngine.effectiveMaxItems(tag, state)
}
