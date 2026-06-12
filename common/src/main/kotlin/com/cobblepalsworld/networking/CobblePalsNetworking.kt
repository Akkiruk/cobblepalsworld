package com.cobblepalsworld.networking

import com.cobblepalsworld.gui.crew.CommandPostCrewSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceType
import com.cobblepalsworld.networking.packets.CommandPostCrewS2C
import com.cobblepalsworld.networking.packets.CrewProfileActionC2S
import com.cobblepalsworld.networking.packets.CrewSourcesS2C
import com.cobblepalsworld.networking.packets.MutateCrewC2S
import com.cobblepalsworld.networking.packets.RequestCommandPostCrewC2S
import com.cobblepalsworld.networking.packets.RequestCrewSourcesC2S
import com.cobblepalsworld.networking.packets.ReturnCrewHomeC2S
import com.cobblepalsworld.networking.packets.WorkerVisualSnapshot
import com.cobblepalsworld.networking.packets.WorkerVisualsS2C
import dev.architectury.networking.NetworkManager
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import java.util.UUID

object CobblePalsNetworking {
    fun registerS2CType() {
        NetworkManager.registerS2CPayloadType(CrewSourcesS2C.TYPE, CrewSourcesS2C.CODEC)
        NetworkManager.registerS2CPayloadType(CommandPostCrewS2C.TYPE, CommandPostCrewS2C.CODEC)
        NetworkManager.registerS2CPayloadType(WorkerVisualsS2C.TYPE, WorkerVisualsS2C.CODEC)
    }

    fun registerServer() {
        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            RequestCrewSourcesC2S.TYPE,
            RequestCrewSourcesC2S.CODEC
        ) { payload, context -> RequestCrewSourcesC2S.handle(payload, context) }

        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            RequestCommandPostCrewC2S.TYPE,
            RequestCommandPostCrewC2S.CODEC
        ) { payload, context -> RequestCommandPostCrewC2S.handle(payload, context) }

        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            MutateCrewC2S.TYPE,
            MutateCrewC2S.CODEC
        ) { payload, context -> MutateCrewC2S.handle(payload, context) }

        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            ReturnCrewHomeC2S.TYPE,
            ReturnCrewHomeC2S.CODEC
        ) { payload, context -> ReturnCrewHomeC2S.handle(payload, context) }

        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            CrewProfileActionC2S.TYPE,
            CrewProfileActionC2S.CODEC
        ) { payload, context -> CrewProfileActionC2S.handle(payload, context) }
    }

    fun registerClient(
        onCrewSources: (BlockPos, List<CrewSourceSnapshot>) -> Unit,
        onCommandPostCrew: (CommandPostCrewSnapshot) -> Unit,
        onWorkerVisuals: (BlockPos, List<WorkerVisualSnapshot>) -> Unit
    ) {
        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            CrewSourcesS2C.TYPE,
            CrewSourcesS2C.CODEC
        ) { payload, context -> CrewSourcesS2C.handle(payload, context, onCrewSources) }

        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            CommandPostCrewS2C.TYPE,
            CommandPostCrewS2C.CODEC
        ) { payload, context -> CommandPostCrewS2C.handle(payload, context, onCommandPostCrew) }

        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            WorkerVisualsS2C.TYPE,
            WorkerVisualsS2C.CODEC
        ) { payload, context -> WorkerVisualsS2C.handle(payload, context, onWorkerVisuals) }
    }

    fun sendWorkerVisuals(players: Collection<ServerPlayerEntity>, worksitePos: BlockPos, visuals: List<WorkerVisualSnapshot>) {
        if (players.isEmpty()) return
        val payload = WorkerVisualsS2C(worksitePos.toImmutable(), visuals)
        players.forEach { player -> NetworkManager.sendToPlayer(player, payload) }
    }

    fun sendCrewSourceRefresh(routerPos: BlockPos, sourceType: CrewSourceType = CrewSourceType.PC, boxIndex: Int = 0, query: String = "") {
        NetworkManager.sendToServer(RequestCrewSourcesC2S(routerPos.toImmutable(), sourceType, boxIndex, query.take(40)))
    }

    fun sendCommandPostCrewRefresh(routerPos: BlockPos) {
        NetworkManager.sendToServer(RequestCommandPostCrewC2S(routerPos.toImmutable()))
    }

    fun sendAssignCrewPokemon(routerPos: BlockPos, pokemonId: UUID) {
        NetworkManager.sendToServer(MutateCrewC2S(routerPos.toImmutable(), pokemonId, true))
    }

    fun sendRemoveCrewPokemon(routerPos: BlockPos, pokemonId: UUID) {
        NetworkManager.sendToServer(MutateCrewC2S(routerPos.toImmutable(), pokemonId, false))
    }

    fun sendReturnCrewPokemon(routerPos: BlockPos, pokemonId: UUID) {
        NetworkManager.sendToServer(ReturnCrewHomeC2S(routerPos.toImmutable(), pokemonId))
    }

    fun sendCycleCrewMode(routerPos: BlockPos, pokemonId: UUID) {
        NetworkManager.sendToServer(CrewProfileActionC2S(routerPos.toImmutable(), pokemonId, CrewProfileActionC2S.ACTION_CYCLE_MODE))
    }

    fun sendToggleCrewFallback(routerPos: BlockPos, pokemonId: UUID) {
        NetworkManager.sendToServer(CrewProfileActionC2S(routerPos.toImmutable(), pokemonId, CrewProfileActionC2S.ACTION_TOGGLE_FALLBACK))
    }
}
