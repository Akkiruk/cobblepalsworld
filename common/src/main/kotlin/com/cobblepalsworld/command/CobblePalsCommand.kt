package com.cobblepalsworld.command

import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.behavior.state.WorkerStatusKind
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.crew.CommandPostCrewLifecycle
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.assignment.TagAssignmentManager
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.runtime.ServerScaleRuntime
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.entity.ItemEntity
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting

object CobblePalsCommand {
    private const val MAX_STATUS_LINES = 24

    private data class WorkerStatusRow(
        val pokemonId: java.util.UUID,
        val tagType: String,
        val reason: WorkerStatusReason,
        val detail: String,
        val carriedItemCount: Int,
        val controllerPos: String,
        val worksitePos: String
    )

    private fun requiresOp() = { source: ServerCommandSource -> source.hasPermissionLevel(2) }

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("cobblepals")
                .executes(::runStatus)
                .then(
                    literal("status")
                        .executes(::runStatus)
                        .then(literal("workers").executes(::runStatusWorkers))
                        .then(literal("blocked").executes(::runStatusBlocked))
                )
                .then(literal("reset").requires(requiresOp())
                    .then(literal("runtime").executes(::runResetRuntime))
                    .then(literal("inventories").executes(::runResetInventories))
                    .then(literal("all").executes(::runResetAll))
                )
        )
    }

    private fun runStatus(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val workers = collectWorkerRows()
        source.sendFeedback({ header("CobblePals Status") }, false)
        source.sendFeedback({ detail("Assignments", TagAssignmentManager.count()) }, false)
        source.sendFeedback({ detail("Inventories", InventoryManager.count()) }, false)
        source.sendFeedback({ detail("Runtime States", StateManager.count()) }, false)
        source.sendFeedback({ detail("Claims", ClaimManager.count()) }, false)
        source.sendFeedback({ detail("Active Workers", workers.count { it.reason.kind == WorkerStatusKind.ACTIVE }) }, false)
        source.sendFeedback({ detail("Blocked Workers", workers.count { it.reason.kind == WorkerStatusKind.BLOCKED }) }, false)
        source.sendFeedback({ detail("Standby Workers", workers.count { it.reason.kind == WorkerStatusKind.STANDBY }) }, false)
        source.sendFeedback({ detail("Waiting Workers", workers.count { it.reason.kind == WorkerStatusKind.WAITING }) }, false)
        return 1
    }

    private fun runStatusWorkers(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val workers = collectWorkerRows()
        source.sendFeedback({ header("CobblePals Workers") }, false)
        if (workers.isEmpty()) {
            source.sendFeedback({ Text.literal("No assigned workers.").formatted(Formatting.GRAY) }, false)
            return 1
        }

        workers.take(MAX_STATUS_LINES).forEach { row ->
            source.sendFeedback({ workerLine(row) }, false)
        }
        if (workers.size > MAX_STATUS_LINES) {
            source.sendFeedback({ Text.literal("Showing $MAX_STATUS_LINES of ${workers.size} workers.").formatted(Formatting.DARK_GRAY) }, false)
        }
        return 1
    }

    private fun runStatusBlocked(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        val workers = collectWorkerRows().filter { it.reason.kind == WorkerStatusKind.BLOCKED }
        source.sendFeedback({ header("CobblePals Blocked") }, false)
        if (workers.isEmpty()) {
            source.sendFeedback({ Text.literal("No blocked workers.").formatted(Formatting.GRAY) }, false)
            return 1
        }

        workers.take(MAX_STATUS_LINES).forEach { row ->
            source.sendFeedback({ workerLine(row) }, false)
        }
        if (workers.size > MAX_STATUS_LINES) {
            source.sendFeedback({ Text.literal("Showing $MAX_STATUS_LINES of ${workers.size} blocked workers.").formatted(Formatting.DARK_GRAY) }, false)
        }
        return 1
    }

    private fun runResetRuntime(ctx: CommandContext<ServerCommandSource>): Int {
        TagExecutionEngine.resetRuntimeState()
        CommandPostCrewLifecycle.clearRuntimeState(ctx.source.server)
        ServerScaleRuntime.clearTransient()
        markAllWorldsDirty(ctx.source)
        ctx.source.sendFeedback({ success("Cleared CobblePals runtime state and claims.") }, true)
        return 1
    }

    private fun runResetInventories(ctx: CommandContext<ServerCommandSource>): Int {
        val droppedStacks = spillInventoriesAtSource(ctx.source)
        markAllWorldsDirty(ctx.source)
        ctx.source.sendFeedback({
            success("Dropped and cleared $droppedStacks carried stack${if (droppedStacks == 1) "" else "s"}.")
        }, true)
        return 1
    }

    private fun runResetAll(ctx: CommandContext<ServerCommandSource>): Int {
        val droppedStacks = spillInventoriesAtSource(ctx.source)
        TagAssignmentManager.clear()
        TagExecutionEngine.resetRuntimeState()
        CommandPostCrewLifecycle.clearRuntimeState(ctx.source.server)
        ServerScaleRuntime.clearTransient()
        markAllWorldsDirty(ctx.source)
        ctx.source.sendFeedback({
            success("Cleared CobblePals assignments, runtime state, and $droppedStacks carried stack${if (droppedStacks == 1) "" else "s"}.")
        }, true)
        return 1
    }

    private fun spillInventoriesAtSource(source: ServerCommandSource): Int {
        val world = source.world
        val dropPos = source.position
        var droppedStacks = 0

        InventoryManager.forEach { _, inventory ->
            for (slot in 0 until inventory.size()) {
                val stack = inventory.getStack(slot)
                if (stack.isEmpty) continue

                val entity = ItemEntity(
                    world,
                    dropPos.x,
                    dropPos.y + 0.5,
                    dropPos.z,
                    stack.copy()
                )
                entity.setToDefaultPickupDelay()
                world.spawnEntity(entity)
                droppedStacks += 1
            }
        }

        InventoryManager.clear()
        return droppedStacks
    }

    private fun markAllWorldsDirty(source: ServerCommandSource) {
        source.server.worlds.forEach(CobblePalsSaveData::markDirty)
    }

    private fun collectWorkerRows(): List<WorkerStatusRow> {
        val rows = mutableListOf<WorkerStatusRow>()
        TagAssignmentManager.forEachRecord { uuid, tag, worksiteBinding, controllerBinding, _ ->
            val state = StateManager.get(uuid)
            val carriedItemCount = InventoryManager.get(uuid)?.let { inventory ->
                (0 until inventory.size()).sumOf { slot -> inventory.getStack(slot).count }
            } ?: 0
            rows += WorkerStatusRow(
                pokemonId = uuid,
                tagType = tag.type.id,
                reason = state?.statusReason ?: WorkerStatusReason.READY,
                detail = state?.statusDetail.orEmpty(),
                carriedItemCount = carriedItemCount,
                controllerPos = formatPos(controllerBinding?.dimensionId, controllerBinding?.pos),
                worksitePos = formatPos(worksiteBinding?.dimensionId, worksiteBinding?.pos)
            )
        }
        return rows.sortedWith(compareBy<WorkerStatusRow>({ it.reason.kind.ordinal }, { it.tagType }, { it.pokemonId.toString() }))
    }

    private fun workerLine(row: WorkerStatusRow): Text {
        val detail = row.detail.ifBlank { row.reason.label }
        val cargoText = if (row.carriedItemCount > 0) " cargo=${row.carriedItemCount}" else ""
        val summary = "${shortId(row.pokemonId)} ${row.tagType} ${row.reason.label}$cargoText | $detail | controller=${row.controllerPos} | worksite=${row.worksitePos}"
        val color = when (row.reason.kind) {
            WorkerStatusKind.ACTIVE -> Formatting.AQUA
            WorkerStatusKind.READY -> Formatting.GREEN
            WorkerStatusKind.WAITING -> Formatting.YELLOW
            WorkerStatusKind.BLOCKED -> Formatting.RED
            WorkerStatusKind.STANDBY -> Formatting.GOLD
        }
        return Text.literal(summary).formatted(color)
    }

    private fun shortId(uuid: java.util.UUID): String = uuid.toString().substring(0, 8)

    private fun formatPos(dimensionId: String?, pos: net.minecraft.util.math.BlockPos?): String {
        if (dimensionId == null || pos == null) return "-"
        val dimension = dimensionId.substringAfterLast(':')
        return "$dimension@${pos.x},${pos.y},${pos.z}"
    }

    private fun header(text: String): Text = Text.literal(text).formatted(Formatting.AQUA, Formatting.BOLD)

    private fun detail(label: String, value: Int): Text {
        return Text.literal("$label: ").formatted(Formatting.GRAY)
            .append(Text.literal(value.toString()).formatted(Formatting.GOLD))
    }

    private fun success(text: String): Text = Text.literal(text).formatted(Formatting.GREEN)
}