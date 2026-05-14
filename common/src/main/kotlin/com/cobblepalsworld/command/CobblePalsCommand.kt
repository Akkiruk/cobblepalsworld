package com.cobblepalsworld.command

import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.behavior.state.StateManager
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
    private fun requiresOp() = { source: ServerCommandSource -> source.hasPermissionLevel(2) }

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("cobblepals")
                .executes(::runStatus)
                .then(literal("status").executes(::runStatus))
                .then(literal("reset").requires(requiresOp())
                    .then(literal("runtime").executes(::runResetRuntime))
                    .then(literal("inventories").executes(::runResetInventories))
                    .then(literal("all").executes(::runResetAll))
                )
        )
    }

    private fun runStatus(ctx: CommandContext<ServerCommandSource>): Int {
        val source = ctx.source
        source.sendFeedback({ header("CobblePals Status") }, false)
        source.sendFeedback({ detail("Assignments", TagAssignmentManager.count()) }, false)
        source.sendFeedback({ detail("Inventories", InventoryManager.count()) }, false)
        source.sendFeedback({ detail("Runtime States", StateManager.count()) }, false)
        source.sendFeedback({ detail("Claims", ClaimManager.count()) }, false)
        return 1
    }

    private fun runResetRuntime(ctx: CommandContext<ServerCommandSource>): Int {
        TagExecutionEngine.resetRuntimeState()
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

    private fun header(text: String): Text = Text.literal(text).formatted(Formatting.AQUA, Formatting.BOLD)

    private fun detail(label: String, value: Int): Text {
        return Text.literal("$label: ").formatted(Formatting.GRAY)
            .append(Text.literal(value.toString()).formatted(Formatting.GOLD))
    }

    private fun success(text: String): Text = Text.literal(text).formatted(Formatting.GREEN)
}